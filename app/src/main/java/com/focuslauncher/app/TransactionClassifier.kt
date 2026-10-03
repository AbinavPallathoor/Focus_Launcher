package com.focuslauncher.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The LLM-fallback half of classification (the memory-lookup half lives in MemoryStore and is
 * tried first by the caller — this is only reached for a genuinely new counterpart). Builds a
 * prompt from the user's own prior answers as few-shot examples, runs it through the
 * grammar-constrained LlamaEngine, and re-validates every field in the result against the real
 * enums regardless of what the grammar already guarantees — the model's output is always a
 * draft, never trusted blindly.
 */
object TransactionClassifier {
    data class Guess(val kind: TransactionKind, val category: ExpenseCategory?, val isPerson: Boolean, val confidence: Float)
    data class Question(val text: String, val options: List<String>)
    data class ClassificationResult(val guess: Guess, val questions: List<Question>)

    private const val MAX_QUESTIONS = 4
    private const val MAX_TOKENS = 400

    /**
     * Null means "couldn't classify right now" (model not downloaded, failed to load, bad
     * output) — the transaction stays UNKNOWN and can be retried later or classified manually.
     * [qaHistory] (question asked -> option the user picked) is only non-empty when this is a
     * follow-up call after the user has answered every question from a first pass — in that
     * case the model is told to give its final answer rather than ask anything further.
     */
    fun classify(
        context: Context,
        transaction: ParsedTransaction,
        examples: List<MemoryStore.MemoryEntry>,
        qaHistory: List<Pair<String, String>> = emptyList(),
    ): ClassificationResult? {
        if (!ensureLoaded(context)) return null
        val raw = LlamaEngine.generate(buildPrompt(transaction, examples, qaHistory), MAX_TOKENS) ?: return null
        return parseAndValidate(raw)
    }

    private fun ensureLoaded(context: Context): Boolean {
        if (LlamaEngine.isLoaded()) return true
        if (!ModelDownloader.isModelReady(context)) return false
        val grammar = context.assets.open("classification_schema.gbnf").bufferedReader().use { it.readText() }
        return LlamaEngine.load(ModelDownloader.modelFile(context).absolutePath, grammar)
    }

    private fun buildPrompt(
        transaction: ParsedTransaction,
        examples: List<MemoryStore.MemoryEntry>,
        qaHistory: List<Pair<String, String>>,
    ): String {
        val exampleLines = if (examples.isEmpty()) {
            "(none yet)"
        } else {
            examples.joinToString("\n") { e ->
                val who = if (e.isPerson) "person" else "merchant"
                val categoryPart = e.category?.let { ", category ${it.name}" } ?: ""
                "- ${e.normalizedName} ($who) -> ${e.kind.name}$categoryPart"
            }
        }
        val instructions = if (qaHistory.isEmpty()) {
            """Set "isPerson" true only if the counterpart looks like a person's name, not a
               business. If confident, return "questions": []. If genuinely ambiguous (e.g.
               could be an ordinary purchase or could be paid for someone else), ask up to 4
               short multiple-choice questions (2-4 options each) that would resolve it."""
        } else {
            val qaLines = qaHistory.joinToString("\n") { (q, a) -> "- Q: $q\n  A: $a" }
            """You already asked the user these questions and they answered:
               $qaLines

               Give your FINAL classification now using these answers. Return "questions": []
               — do not ask anything further."""
        }
        return """
            You classify one bank SMS transaction for a personal expense tracker. Respond with
            ONLY a single JSON object matching the required schema — no other text.

            Valid kinds: EXPENSE (ordinary spend), LENT (user paid for someone else, they owe
            the user back), BORROWED (someone covered the user, user owes them), REPAYMENT_IN
            (a person paying the user back for something lent), REPAYMENT_OUT (user paying a
            person back for something borrowed), INCOME (salary, refund, unrelated to a person).
            Valid categories (EXPENSE only, else null): FOOD, TRANSPORT, ESSENTIALS, EXTRAS,
            SUBSCRIPTION.

            This user's own past answers for other counterparts:
            $exampleLines

            New transaction to classify:
            Direction: ${transaction.direction}
            Amount: ${transaction.amount}
            Counterpart: ${transaction.counterpartName}

            $instructions
        """.trimIndent()
    }

    private fun parseAndValidate(raw: String): ClassificationResult? {
        return try {
            val jsonText = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
            val obj = JSONObject(jsonText)
            val guessObj = obj.getJSONObject("guess")
            val kind = TransactionKind.fromStorage(guessObj.optString("kind").takeIf { it.isNotBlank() })
            val category = guessObj.optString("category")
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                ?.let { ExpenseCategory.fromStorage(it) }
            val isPerson = guessObj.optBoolean("isPerson", false)
            val confidence = obj.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)

            val questionsArray = obj.optJSONArray("questions") ?: JSONArray()
            val questions = (0 until minOf(questionsArray.length(), MAX_QUESTIONS)).mapNotNull { i ->
                val q = questionsArray.optJSONObject(i) ?: return@mapNotNull null
                val text = q.optString("question").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val optionsArray = q.optJSONArray("options") ?: return@mapNotNull null
                val options = (0 until optionsArray.length())
                    .mapNotNull { j -> optionsArray.optString(j).takeIf { it.isNotBlank() } }
                if (options.size !in 2..4) return@mapNotNull null
                Question(text, options)
            }
            ClassificationResult(Guess(kind, category, isPerson, confidence), questions)
        } catch (e: Exception) {
            null
        }
    }
}
