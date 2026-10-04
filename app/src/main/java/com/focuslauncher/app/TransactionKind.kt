package com.focuslauncher.app

/**
 * What a transaction actually represents, beyond its raw debit/credit direction — this is the
 * thing the memory lookup / on-device LLM is trying to figure out. A plain category (Food,
 * Transport, ...) only applies to EXPENSE; the other kinds feed the per-person debt ledger
 * instead of the category breakdown.
 */
enum class TransactionKind(val label: String) {
    EXPENSE("Expense"),
    LENT("Lent"),
    BORROWED("Borrowed"),
    REPAYMENT_IN("Repayment received"),
    REPAYMENT_OUT("Repayment sent"),
    INCOME("Income"),
    UNKNOWN("Unclassified");

    companion object {
        fun fromStorage(value: String?): TransactionKind =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** Who/what resolved a transaction's kind/category. */
enum class ResolvedBy {
    MEMORY, LLM, USER, UNKNOWN;

    companion object {
        fun fromStorage(value: String?): ResolvedBy =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** The single short tag shown in the terminal feed: the category for an EXPENSE, the kind for
 * anything else (both repayment directions collapse to one tag — the feed row's own debit/
 * credit amount already carries the direction). */
fun transactionTag(kind: TransactionKind, category: ExpenseCategory?): String = when (kind) {
    TransactionKind.EXPENSE -> category?.label?.lowercase() ?: "expense"
    TransactionKind.LENT -> "lent"
    TransactionKind.BORROWED -> "borrowed"
    TransactionKind.REPAYMENT_IN, TransactionKind.REPAYMENT_OUT -> "repayment"
    TransactionKind.INCOME -> "income"
    TransactionKind.UNKNOWN -> "unsorted"
}
