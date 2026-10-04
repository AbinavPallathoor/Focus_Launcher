package com.focuslauncher.app

import android.content.Context
import org.json.JSONObject

/**
 * The LLM-fallback half of classification (the memory-lookup half lives in MemoryStore and is
 * tried first by the caller — this is only reached for a genuinely new counterpart). Builds a
 * prompt from the user's own prior answers as few-shot examples, runs it through the
 * grammar-constrained LlamaEngine, and re-validates every field in the result against the real
 * enums regardless of what the grammar already guarantees — the model's output is always a
 * draft, never trusted blindly. Every transaction is auto-tagged immediately with no follow-up
 * questions; a wrong guess is fixed afterward with a free-text note via [reclassifyWithNote].
 */
object TransactionClassifier {
    data class Guess(val kind: TransactionKind, val category: ExpenseCategory?, val isPerson: Boolean, val confidence: Float)

    // The schema's own JSON is a single flat object (kind, category, isPerson, confidence) —
    // this is a hard ceiling in case the grammar ever lets generation run past a complete
    // object, not the expected length.
    private const val MAX_TOKENS = 40

    /**
     * Null means "couldn't classify right now" (model not downloaded, failed to load, bad
     * output) — the transaction stays UNKNOWN and is retried on the next sync/resume.
     * [priorBalance] is the user's running balance with this counterpart (positive = they owe
     * the user) — the signal that lets a credit from someone the user previously lent money to
     * be recognized as a repayment instead of guessed as plain income.
     */
    fun classify(
        context: Context,
        transaction: ParsedTransaction,
        examples: List<MemoryStore.MemoryEntry>,
        priorBalance: Double,
    ): Guess? {
        if (!LlamaModelLoader.ensureLoaded(context)) return null
        val grammar = LlamaModelLoader.readGrammar(context, "classification_schema.gbnf")
        val raw = LlamaEngine.generate(buildPrompt(transaction, examples, priorBalance, userNote = null), MAX_TOKENS, grammar) ?: return null
        return parseAndValidate(raw)
    }

    /**
     * The automatic guess was wrong — re-runs with the user's own words as the deciding signal,
     * trusted over whatever the model would otherwise assume.
     */
    fun reclassifyWithNote(
        context: Context,
        transaction: ParsedTransaction,
        examples: List<MemoryStore.MemoryEntry>,
        priorBalance: Double,
        userNote: String,
    ): Guess? {
        if (!LlamaModelLoader.ensureLoaded(context)) return null
        val grammar = LlamaModelLoader.readGrammar(context, "classification_schema.gbnf")
        val raw = LlamaEngine.generate(buildPrompt(transaction, examples, priorBalance, userNote), MAX_TOKENS, grammar) ?: return null
        return parseAndValidate(raw)
    }

    private fun buildPrompt(
        transaction: ParsedTransaction,
        examples: List<MemoryStore.MemoryEntry>,
        priorBalance: Double,
        userNote: String?,
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
        // The debt signal: a repayment doesn't have to match any one past transaction's amount —
        // someone settling several loans at once is still just a REPAYMENT_IN/OUT. A credit with
        // no outstanding balance (a monthly allowance, salary, a one-off refund) should default
        // to INCOME rather than being guessed as a repayment that doesn't actually exist.
        val balanceContext = when {
            priorBalance > 0.5 -> "This person currently owes the user ₹${"%.0f".format(priorBalance)} " +
                "from past lending. A credit from them is very likely a repayment (REPAYMENT_IN) — " +
                "even if the amount doesn't exactly match one past transaction, they may be settling " +
                "several at once."
            priorBalance < -0.5 -> "The user currently owes this person ₹${"%.0f".format(-priorBalance)} " +
                "from past borrowing. A debit to them is very likely the user paying them back " +
                "(REPAYMENT_OUT)."
            else -> "No outstanding balance with this counterpart. Do not guess REPAYMENT_IN or " +
                "REPAYMENT_OUT here — an unexplained credit with no prior debt is INCOME (e.g. an " +
                "allowance, salary, or refund), not a repayment."
        }
        val noteInstruction = if (userNote == null) {
            """Set "isPerson" true only if the counterpart looks like a person's name, not a
               business. Give your single best guess even if unsure — never leave this
               unresolved."""
        } else {
            """The automatic guess for this transaction was wrong. The user says what it
               actually was: "$userNote". Trust the user's words over any assumption and
               classify accordingly."""
        }
        return """
            You classify one bank SMS transaction for a personal expense tracker. Respond with
            ONLY a single JSON object matching the required schema — no other text.

            Valid kinds: EXPENSE (ordinary spend), LENT (user paid for someone else, they owe
            the user back), BORROWED (someone covered the user, user owes them), REPAYMENT_IN
            (a person paying the user back for something lent), REPAYMENT_OUT (user paying a
            person back for something borrowed), INCOME (salary, refund, allowance, unrelated to
            a person).
            Valid categories (EXPENSE only, else null): FOOD, TRANSPORT, ESSENTIALS, EXTRAS,
            SUBSCRIPTION.

            This user's own past answers for other counterparts:
            $exampleLines

            Transaction to classify:
            Direction: ${transaction.direction}
            Amount: ${transaction.amount}
            Counterpart: ${transaction.counterpartName}
            $balanceContext

            $noteInstruction
        """.trimIndent()
    }

    private fun parseAndValidate(raw: String): Guess? {
        return try {
            val jsonText = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
            val obj = JSONObject(jsonText)
            val kind = TransactionKind.fromStorage(obj.optString("kind").takeIf { it.isNotBlank() })
            // The prompt tells the model category only applies to EXPENSE, but a small model
            // doesn't always follow that — enforced here rather than trusted, same as every
            // other field.
            val category = if (kind != TransactionKind.EXPENSE) {
                null
            } else {
                obj.optString("category")
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?.let { ExpenseCategory.fromStorage(it) }
            }
            val isPerson = obj.optBoolean("isPerson", false)
            val confidence = obj.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
            Guess(kind, category, isPerson, confidence)
        } catch (e: Exception) {
            null
        }
    }
}
