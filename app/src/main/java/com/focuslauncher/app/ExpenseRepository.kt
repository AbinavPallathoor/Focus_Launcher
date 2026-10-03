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
 * Reads bank/UPI debit alerts straight out of the SMS inbox (content://sms/inbox) and keeps a
 * local SQLite record of them — nothing ever leaves the device. A merchant is tagged with a
 * category once; every future transaction from that merchant inherits it automatically.
 */
object ExpenseRepository {

    data class Transaction(
        val id: Long,
        val amount: Double,
        val merchant: String,
        val timestamp: Long,
        val category: ExpenseCategory?,
    )

    data class DayTotal(val dayStartMillis: Long, val total: Double)

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

    /**
     * Scans any inbox messages newer than the last sync and records the ones that parse as
     * spend. The very first sync (nothing recorded yet) looks back to the start of the
     * current calendar month rather than the device's entire SMS history — enough to make
     * "this month" correct immediately, without churning through years of old messages.
     * Every sync after that is purely incremental from wherever the last one left off.
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
                    val category = categoryForMerchant(db, parsed.merchant)
                    val values = ContentValues().apply {
                        put("sms_id", smsId)
                        put("amount", parsed.amount)
                        put("merchant", parsed.merchant)
                        put("type", "DEBIT")
                        put("timestamp", date)
                        put("category", category?.name)
                    }
                    db.insertWithOnConflict("transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            prefs(context).edit().putLong(KEY_LAST_SYNC_TS, maxTimestamp).apply()
        } finally {
            db.close()
        }
    }

    private fun categoryForMerchant(db: SQLiteDatabase, merchant: String): ExpenseCategory? {
        db.query("merchant_tags", arrayOf("category"), "merchant = ?", arrayOf(merchant), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) return ExpenseCategory.fromStorage(cursor.getString(0)) }
        return null
    }

    fun getUntaggedCount(context: Context): Int = getUntaggedMerchants(context).size

    fun getUntaggedMerchants(context: Context): List<String> {
        val db = ExpenseDbHelper(context).readableDatabase
        val result = mutableListOf<String>()
        try {
            db.rawQuery(
                "SELECT DISTINCT merchant FROM transactions WHERE category IS NULL ORDER BY merchant", null
            ).use { cursor -> while (cursor.moveToNext()) result.add(cursor.getString(0)) }
        } finally {
            db.close()
        }
        return result
    }

    /** Tags a merchant and retroactively applies the category to its existing transactions. */
    fun tagMerchant(context: Context, merchant: String, category: ExpenseCategory) {
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            db.insertWithOnConflict(
                "merchant_tags", null,
                ContentValues().apply { put("merchant", merchant); put("category", category.name) },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.execSQL("UPDATE transactions SET category = ? WHERE merchant = ?", arrayOf(category.name, merchant))
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

    fun getMonthTotal(context: Context): Double {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE timestamp >= ?",
                arrayOf(startOfMonth().toString())
            ).use { cursor -> cursor.moveToFirst(); return cursor.getDouble(0) }
        } finally {
            db.close()
        }
    }

    /** Category totals for the current calendar month so far — zero for anything untouched. */
    fun getCategoryTotalsMonth(context: Context): Map<ExpenseCategory, Double> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val totals = linkedMapOf<ExpenseCategory, Double>()
            ExpenseCategory.entries.forEach { totals[it] = 0.0 }
            db.rawQuery(
                """SELECT category, SUM(amount) FROM transactions
                   WHERE timestamp >= ? AND category IS NOT NULL
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

    fun getTodayTotal(context: Context): Double {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val dayStart = startOfToday()
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE timestamp >= ? AND timestamp < ?",
                arrayOf(dayStart.toString(), dayEnd.toString())
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
                    "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE timestamp >= ? AND timestamp < ?",
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

    fun getCategoryTotalsToday(context: Context): Map<ExpenseCategory, Double> {
        val db = ExpenseDbHelper(context).readableDatabase
        try {
            val dayStart = startOfToday()
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            val totals = linkedMapOf<ExpenseCategory, Double>()
            ExpenseCategory.entries.forEach { totals[it] = 0.0 }
            db.rawQuery(
                """SELECT category, SUM(amount) FROM transactions
                   WHERE timestamp >= ? AND timestamp < ? AND category IS NOT NULL
                   GROUP BY category""",
                arrayOf(dayStart.toString(), dayEnd.toString())
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
                "SELECT id, amount, merchant, timestamp, category FROM transactions ORDER BY timestamp DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        Transaction(
                            id = cursor.getLong(0),
                            amount = cursor.getDouble(1),
                            merchant = cursor.getString(2),
                            timestamp = cursor.getLong(3),
                            category = if (cursor.isNull(4)) null else ExpenseCategory.fromStorage(cursor.getString(4)),
                        )
                    )
                }
            }
            return result
        } finally {
            db.close()
        }
    }
}
