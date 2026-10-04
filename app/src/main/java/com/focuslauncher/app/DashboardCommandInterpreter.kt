package com.focuslauncher.app

import android.content.Context
import org.json.JSONObject

/**
 * The dashboard's free-text command bar ("add a 200 rupee cash lunch", "remove the bookstore
 * one", "that swiggy charge was actually extras") — one instruction, interpreted against a
 * numbered listing of the dashboard's own recent transactions, resolved to a single structured
 * action. The numbering only ever exists inside the prompt; the user never sees or types numbers
 * themselves, they just refer to things in plain language and the model matches them against the
 * listing it was given.
 */
object DashboardCommandInterpreter {
    enum class Action { ADD, REMOVE, MODIFY, NONE }

    data class CommandResult(
        val action: Action,
        /** 1-based index into the same transaction list the prompt was built from — only
         * meaningful for REMOVE/MODIFY. */
        val targetIndex: Int,
        val counterpartName: String,
        val amount: Double,
        val kind: TransactionKind,
        val category: ExpenseCategory?,
    )

    // Noticeably more than the plain classifier's budget — this schema has an extra string
    // field (counterpartName) and two more enums, so a complete object needs more room.
    private const val MAX_TOKENS = 80

    /** Null means "couldn't interpret right now" (model not downloaded, failed to load, bad
     * output) — callers should just leave the dashboard state untouched. */
    fun interpret(context: Context, command: String, transactions: List<ExpenseRepository.Transaction>): CommandResult? {
        if (!LlamaModelLoader.ensureLoaded(context)) return null
        val grammar = LlamaModelLoader.readGrammar(context, "dashboard_command.gbnf")
        val raw = LlamaEngine.generate(buildPrompt(command, transactions), MAX_TOKENS, grammar) ?: return null
        return parseAndValidate(raw)
    }

    private fun buildPrompt(command: String, transactions: List<ExpenseRepository.Transaction>): String {
        val listing = if (transactions.isEmpty()) {
            "(none yet)"
        } else {
            transactions.withIndex().joinToString("\n") { (i, t) ->
                val tag = transactionTag(t.kind, t.category)
                "${i + 1}. ${t.counterpartName} ₹${"%.0f".format(t.amount)} ($tag)"
            }
        }
        return """
            You control a personal expense tracker by interpreting one instruction from the
            user. Respond with ONLY a single JSON object matching the required schema — no
            other text.

            Actions:
            - ADD: the user wants a new transaction recorded that wasn't captured automatically
              (e.g. a cash purchase). Fill counterpartName, amount, kind, category. Set
              targetIndex to 0.
            - REMOVE: the user wants an existing transaction deleted (a mistake, duplicate,
              spam). Set targetIndex to its number in the list below; other fields are ignored.
            - MODIFY: the user wants an existing transaction retagged with a different
              kind/category. Set targetIndex and the new kind/category; other fields ignored.
            - NONE: the instruction doesn't clearly map to any of the above. Set targetIndex
              to 0.

            Valid kinds: EXPENSE (ordinary spend), LENT (user paid for someone else, they owe
            the user back), BORROWED (someone covered the user, user owes them), REPAYMENT_IN
            (a person paying the user back), REPAYMENT_OUT (user paying a person back), INCOME
            (salary, refund, allowance, unrelated to a person).
            Valid categories (EXPENSE only, else null): FOOD, TRANSPORT, ESSENTIALS, EXTRAS,
            SUBSCRIPTION. Coffee, meals, snacks, groceries are FOOD, not EXTRAS.

            Recent transactions (numbered, newest first):
            $listing

            User's instruction: "$command"
        """.trimIndent()
    }

    private fun parseAndValidate(raw: String): CommandResult? {
        return try {
            val jsonText = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
            val obj = JSONObject(jsonText)
            val action = Action.entries.firstOrNull { it.name == obj.optString("action") } ?: Action.NONE
            val targetIndex = obj.optInt("targetIndex", 0)
            val counterpartName = obj.optString("counterpartName").trim().takeIf { it.isNotBlank() } ?: "MANUAL"
            val amount = obj.optDouble("amount", 0.0)
            val kind = TransactionKind.fromStorage(obj.optString("kind").takeIf { it.isNotBlank() })
            val category = if (kind != TransactionKind.EXPENSE) {
                null
            } else {
                obj.optString("category")
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?.let { ExpenseCategory.fromStorage(it) }
            }
            CommandResult(action, targetIndex, counterpartName, amount, kind, category)
        } catch (e: Exception) {
            null
        }
    }
}
