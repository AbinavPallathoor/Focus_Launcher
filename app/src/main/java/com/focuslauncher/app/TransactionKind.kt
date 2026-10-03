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
