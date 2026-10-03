package com.focuslauncher.app

enum class TransactionDirection { DEBIT, CREDIT }

/** A transaction extracted from a bank/UPI SMS — either money leaving or entering the account. */
data class ParsedTransaction(
    val amount: Double,
    val counterpartName: String,
    val direction: TransactionDirection,
)

/**
 * Bank SMS formats vary a lot (and there's no universal standard), so this covers the common
 * phrasing seen in Indian bank/UPI alerts — debit e.g. "Sent Rs.237.00 from A/c *3968 on
 * 28-09-26 to ALPHAMERICALS PRIVATE LIMITED" or "Rs 450 debited from your account for Swiggy";
 * credit e.g. "Your A/c *3968 is credited with Rs.63.00 on 02-10-26 by Mr Shubh Santosh
 * Shetgaonkar. RRN ...". Messages that don't look like either (OTPs, promotions) are skipped.
 */
object SmsTransactionParser {
    private val DEBIT_KEYWORDS = listOf("debited", "sent", "paid", "spent", "withdrawn", "purchase of")
    private val CREDIT_KEYWORDS = listOf("credited", "received", "refund", "deposited")

    private val AMOUNT_REGEX = Regex("""(?:rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // Both stop at the first "on/dt/via/ref" keyword or the first period/comma — proven against
    // real bank SMS (including trailing ".RRN <number>.Avl Bal Rs...." suffixes) this session.
    private val DEBIT_COUNTERPART_REGEX =
        Regex("""\b(?:to|towards|at|for)\s+([A-Za-z0-9&.'\- ]{3,60}?)(?=\s+(?:on|dt|via|ref)\b|[.,]|$)""", RegexOption.IGNORE_CASE)
    private val CREDIT_COUNTERPART_REGEX =
        Regex("""\b(?:by|from)\s+([A-Za-z0-9&.'\- ]{3,60}?)(?=\s+(?:on|dt|via|ref)\b|[.,]|$)""", RegexOption.IGNORE_CASE)

    fun parse(body: String): ParsedTransaction? {
        val lower = body.lowercase()
        val isDebit = DEBIT_KEYWORDS.any { lower.contains(it) }
        val isCredit = CREDIT_KEYWORDS.any { lower.contains(it) }
        // Neither, or both (ambiguous) — skip entirely rather than guess the direction wrong.
        if (isDebit == isCredit) return null

        val amount = AMOUNT_REGEX.find(body)
            ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null

        val direction = if (isDebit) TransactionDirection.DEBIT else TransactionDirection.CREDIT
        val counterpartRegex = if (isDebit) DEBIT_COUNTERPART_REGEX else CREDIT_COUNTERPART_REGEX
        val counterpart = counterpartRegex.find(body)
            ?.groupValues?.get(1)?.trim()?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"

        return ParsedTransaction(amount = amount, counterpartName = counterpart, direction = direction)
    }
}
