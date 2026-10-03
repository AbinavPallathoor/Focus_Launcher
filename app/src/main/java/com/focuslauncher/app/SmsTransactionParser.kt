package com.focuslauncher.app

/** A debit transaction extracted from a bank/UPI SMS — this tracker only cares about spend. */
data class ParsedTransaction(val amount: Double, val merchant: String)

/**
 * Bank SMS formats vary a lot (and there's no universal standard), so this covers the common
 * phrasing seen in Indian bank/UPI debit alerts — e.g. "Sent Rs.237.00 from A/c *1234 on
 * 28-09-26 to EXAMPLE MERCHANT PRIVATE LIMITED" or "Rs 450 debited from your account for Swiggy".
 * Messages that don't look like a debit alert (OTPs, promotions, credit alerts) are skipped.
 */
object SmsTransactionParser {
    private val DEBIT_KEYWORDS = listOf("debited", "sent", "paid", "spent", "withdrawn", "purchase of")
    private val CREDIT_KEYWORDS = listOf("credited", "received", "refund", "deposited")

    private val AMOUNT_REGEX = Regex("""(?:rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val MERCHANT_TO_REGEX =
        Regex("""\b(?:to|towards|at|for)\s+([A-Za-z0-9&.'\- ]{3,60}?)(?=\s+(?:on|dt|via|ref)\b|[.,]|$)""", RegexOption.IGNORE_CASE)

    fun parse(body: String): ParsedTransaction? {
        val lower = body.lowercase()
        val isDebit = DEBIT_KEYWORDS.any { lower.contains(it) }
        val isCredit = CREDIT_KEYWORDS.any { lower.contains(it) }
        // Ambiguous or non-transactional messages (OTPs, promos) are skipped entirely.
        if (!isDebit || isCredit) return null

        val amount = AMOUNT_REGEX.find(body)
            ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null

        val merchant = MERCHANT_TO_REGEX.find(body)
            ?.groupValues?.get(1)?.trim()?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"

        return ParsedTransaction(amount = amount, merchant = merchant)
    }
}
