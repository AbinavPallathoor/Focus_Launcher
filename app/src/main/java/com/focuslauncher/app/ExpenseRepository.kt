package com.focuslauncher.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Reads bank/UPI debit and credit alerts straight out of the SMS inbox (content://sms/inbox)
 * and keeps a local SQLite record of them — nothing ever leaves the device. A counterpart
 * (merchant or person) is classified once — by memory, an on-device LLM, or the user — and
 * every future transaction from them is recognized automatically from then on.
 */
object ExpenseRepository {

    data class Transaction(
        val id: Long,
        val amount: Double,
        val counterpartName: String,
        val direction: TransactionDirection,
        val timestamp: Long,
        val kind: TransactionKind,
        val category: ExpenseCategory?,
    )

    data class DayTotal(val dayStartMillis: Long, val total: Double)

    /** Net balance with one person: positive = they owe the user, negative = user owes them. */
    data class PersonBalance(val name: String, val netAmount: Double)

    private const val PREFS = "expense_tracker"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SYNC_TS = "last_sync_ts"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasReceiveSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Scans any inbox messages newer than the last sync and records the ones that parse as a
     * transaction. The very first sync (nothing recorded yet) looks back to the start of the
     * current calendar month rather than the device's entire SMS history — enough to make
     * "this month" correct immediately. Every sync after that is purely incremental from
     * wherever the last one left off. Each transaction is classified against [MemoryStore]
     * instantly if its counterpart has been seen before; unseen counterparts are recorded as
     * [TransactionKind.UNKNOWN] for now — the on-device classifier (wired in a later stage)
     * is what turns those into a confident kind/category plus any follow-up questions.
     */
    fun syncSms(context: Context) {
        if (!hasSmsPermission(context)) return
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            val hasSyncedBefore = prefs(context).contains(KEY_LAST_SYNC_TS)
            val since = if (hasSyncedBefore) prefs(context).getLong(KEY_LAST_SYNC_TS, 0L) else startOfMonth()
            var maxTimestamp = since
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "body", "date")

            context.contentResolver.query(
                uri, projection, "date > ?", arrayOf(since.toString()), "date ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                while (cursor.moveToNext()) {
                    val smsId = cursor.getString(idIdx)
                    val body = cursor.getString(bodyIdx) ?: continue
                    val date = cursor.getLong(dateIdx)
                    if (date > maxTimestamp) maxTimestamp = date

                    val parsed = SmsTransactionParser.parse(body) ?: continue
                    recordTransaction(db, smsId, body, date, parsed)
                }
            }
            prefs(context).edit().putLong(KEY_LAST_SYNC_TS, maxTimestamp).apply()
        } finally {
            db.close()
        }
    }

    private fun recordTransaction(db: SQLiteDatabase, smsId: String, body: String, date: Long, parsed: ParsedTransaction) {
        val memory = MemoryStore.lookup(db, parsed.counterpartName)
        val values = ContentValues().apply {
            put("sms_id", smsId)
            put("amount", parsed.amount)
            put("counterpart_name", MemoryStore.normalize(parsed.counterpartName))
            put("direction", parsed.direction.name)
            put("raw_body", body)
            put("timestamp", date)
            if (memory != null) {
                put("kind", memory.kind.name)
                put("category", memory.category?.name)
                put("confidence", 1.0)
                put("resolved_by", ResolvedBy.MEMORY.name)
            } else {
                put("kind", TransactionKind.UNKNOWN.name)
                put("confidence", 0.0)
                put("resolved_by", ResolvedBy.UNKNOWN.name)
            }
        }
        db.insertWithOnConflict("transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /**
     * Manually classifies every transaction from [counterpartName] (including past ones) and
     * remembers the decision so future transactions from them are instant. This is the
     * fallback path for whenever the automatic classifier doesn't have an answer yet.
     */
    fun classify(context: Context, counterpartName: String, kind: TransactionKind, category: ExpenseCategory?, isPerson: Boolean) {
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            MemoryStore.remember(db, counterpartName, kind, category, isPerson)
            val key = MemoryStore.normalize(counterpartName)
            db.execSQL(
                "UPDATE transactions SET kind = ?, category = ?, confidence = 1.0, resolved_by = ? WHERE counterpart_name = ?",
                arrayOf(kind.name, category?.name, ResolvedBy.USER.name, key)
            )
        } finally {
            db.close()
        }
    }

    fun getUnclassifiedCounterparts(context: Context): List<String> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val result = mutableListOf<String>()
            db.rawQuery(
                "SELECT DISTINCT counterpart_name FROM transactions WHERE kind = 'UNKNOWN' ORDER BY counterpart_name", null
            ).use { cursor -> while (cursor.moveToNext()) result.add(cursor.getString(0)) }
            return result
        } finally {
            db.close()
        }
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Spend totals count every debit that isn't already confirmed as non-spend (lent/repaid) —
    // an UNKNOWN debit is assumed to be ordinary spend until classification says otherwise, so
    // the total is correct immediately rather than waiting on tagging.
    private const val SPEND_KINDS_CLAUSE = "direction = 'DEBIT' AND kind IN ('EXPENSE', 'UNKNOWN')"

    fun getTodayTotal(context: Context): Double {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val dayStart = startOfToday()
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE $SPEND_KINDS_CLAUSE AND timestamp >= ? AND timestamp < ?",
                arrayOf(dayStart.toString(), dayEnd.toString())
            ).use { cursor -> cursor.moveToFirst(); return cursor.getDouble(0) }
        } finally {
            db.close()
        }
    }

    fun getMonthTotal(context: Context): Double {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE $SPEND_KINDS_CLAUSE AND timestamp >= ?",
                arrayOf(startOfMonth().toString())
            ).use { cursor -> cursor.moveToFirst(); return cursor.getDouble(0) }
        } finally {
            db.close()
        }
    }

    /** One total per day for the last [days] days (oldest first), for the spending line graph. */
    fun getDailyTotals(context: Context, days: Int): List<DayTotal> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val todayStart = startOfToday()
            val oneDay = TimeUnit.DAYS.toMillis(1)
            val results = mutableListOf<DayTotal>()
            for (i in (days - 1) downTo 0) {
                val dayStart = todayStart - i * oneDay
                val dayEnd = dayStart + oneDay
                db.rawQuery(
                    "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE $SPEND_KINDS_CLAUSE AND timestamp >= ? AND timestamp < ?",
                    arrayOf(dayStart.toString(), dayEnd.toString())
                ).use { cursor ->
                    cursor.moveToFirst()
                    results.add(DayTotal(dayStart, cursor.getDouble(0)))
                }
            }
            return results
        } finally {
            db.close()
        }
    }

    fun getCategoryTotalsMonth(context: Context): Map<ExpenseCategory, Double> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val totals = linkedMapOf<ExpenseCategory, Double>()
            ExpenseCategory.entries.forEach { totals[it] = 0.0 }
            db.rawQuery(
                """SELECT category, SUM(amount) FROM transactions
                   WHERE direction = 'DEBIT' AND kind = 'EXPENSE' AND category IS NOT NULL AND timestamp >= ?
                   GROUP BY category""",
                arrayOf(startOfMonth().toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    ExpenseCategory.fromStorage(cursor.getString(0))?.let { totals[it] = cursor.getDouble(1) }
                }
            }
            return totals
        } finally {
            db.close()
        }
    }

    fun getRecentTransactions(context: Context, limit: Int = 20): List<Transaction> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val result = mutableListOf<Transaction>()
            db.rawQuery(
                """SELECT id, amount, counterpart_name, direction, timestamp, kind, category
                   FROM transactions ORDER BY timestamp DESC LIMIT ?""",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        Transaction(
                            id = cursor.getLong(0),
                            amount = cursor.getDouble(1),
                            counterpartName = cursor.getString(2),
                            direction = TransactionDirection.valueOf(cursor.getString(3)),
                            timestamp = cursor.getLong(4),
                            kind = TransactionKind.fromStorage(cursor.getString(5)),
                            category = ExpenseCategory.fromStorage(cursor.getString(6)),
                        )
                    )
                }
            }
            return result
        } finally {
            db.close()
        }
    }

    /**
     * Who owes the user, and who the user owes — derived straight from the transaction log
     * rather than a separately-maintained balance, so it can never drift out of sync with it.
     * Lending money (debit) or receiving a repayment (credit) moves a person's balance toward
     * "they owe you"; borrowing (credit) or sending a repayment (debit) moves it the other way.
     */
    fun getDebtLedger(context: Context): List<PersonBalance> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val result = mutableListOf<PersonBalance>()
            db.rawQuery(
                """SELECT counterpart_name,
                          SUM(CASE WHEN kind = 'LENT' THEN amount ELSE 0 END)
                        - SUM(CASE WHEN kind = 'REPAYMENT_IN' THEN amount ELSE 0 END)
                        - SUM(CASE WHEN kind = 'BORROWED' THEN amount ELSE 0 END)
                        + SUM(CASE WHEN kind = 'REPAYMENT_OUT' THEN amount ELSE 0 END) AS net
                   FROM transactions
                   WHERE kind IN ('LENT', 'BORROWED', 'REPAYMENT_IN', 'REPAYMENT_OUT')
                   GROUP BY counterpart_name
                   HAVING net != 0
                   ORDER BY ABS(net) DESC""",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(PersonBalance(name = cursor.getString(0), netAmount = cursor.getDouble(1)))
                }
            }
            return result
        } finally {
            db.close()
        }
    }
}
