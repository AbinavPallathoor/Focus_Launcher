package com.focuslauncher.app

/** Fixed set of spend categories a merchant can be tagged with. */
enum class ExpenseCategory(val label: String) {
    FOOD("Food"),
    TRANSPORT("Transport"),
    ESSENTIALS("Essentials"),
    EXTRAS("Extras"),
    SUBSCRIPTION("Subscription");

    companion object {
        fun fromStorage(value: String?): ExpenseCategory? = entries.firstOrNull { it.name == value }
    }
}
