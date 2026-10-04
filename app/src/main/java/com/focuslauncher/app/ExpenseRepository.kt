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
        val resolvedBy: ResolvedBy,
    )

    data class DayTotal(val dayStartMillis: Long, val total: Double)

    /** Net balance with one person: positive = they owe the user, negative = user owes them. */
    data class PersonBalance(val name: String, val netAmount: Double)

    private const val PREFS = "expense_tracker"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SYNC_TS = "last_sync_ts"

    // Guards classifyUnknownTransactions against running concurrently with itself — see its
    // own doc comment for why that matters.
    private val classifyLock = Any()

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

    /**
     * A remembered kind only applies to transactions in the direction it was actually learned
     * from. A person remembered as LENT (a debit — the user paid them) says nothing about what a
     * *credit* from that same person means; blindly reusing it would tag every repayment from
     * them as another LENT instead of ever reaching the LLM's balance-aware REPAYMENT_IN guess.
     * Plain merchants (EXPENSE/INCOME/SUBSCRIPTION etc.) are effectively one-directional anyway,
     * so this never affects them in practice.
     */
    private fun TransactionKind.matchesDirection(direction: TransactionDirection): Boolean = when (this) {
        TransactionKind.EXPENSE, TransactionKind.LENT, TransactionKind.REPAYMENT_OUT -> direction == TransactionDirection.DEBIT
        TransactionKind.INCOME, TransactionKind.BORROWED, TransactionKind.REPAYMENT_IN -> direction == TransactionDirection.CREDIT
        TransactionKind.UNKNOWN -> true
    }

    // The dashboard command model's own "kind" guess is reliable (it's the same judgment call
    // the per-transaction classifier already makes well); its "direction" field for a manually
    // described ADD is not — greedy decoding kept producing CREDIT for plainly-spent money
    // ("add 100 for coffee") even after the prompt spelled out the rule. Deriving direction from
    // kind instead sidesteps the unreliable field entirely rather than continuing to fight it.
    private fun TransactionKind.canonicalDirection(): TransactionDirection = when (this) {
        TransactionKind.EXPENSE, TransactionKind.LENT, TransactionKind.REPAYMENT_OUT -> TransactionDirection.DEBIT
        TransactionKind.INCOME, TransactionKind.BORROWED, TransactionKind.REPAYMENT_IN -> TransactionDirection.CREDIT
        TransactionKind.UNKNOWN -> TransactionDirection.DEBIT
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
            if (memory != null && memory.kind.matchesDirection(parsed.direction)) {
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
     * A concurrent, slow (LLM-backed) auto-classification pass can still be in flight for the
     * same transaction when the user explicitly corrects or confirms it — without this guard,
     * that stale pass's own write can land *after* the user's and silently clobber it back.
     * User-resolved writes are absolute; an auto (MEMORY/LLM) write only applies if the
     * transaction hasn't already been resolved by the user in the meantime.
     */
    private fun applyGuessToTransaction(
        db: SQLiteDatabase, transactionId: Long, kind: TransactionKind,
        category: ExpenseCategory?, confidence: Float, resolvedBy: ResolvedBy,
    ) {
        val whereClause = if (resolvedBy == ResolvedBy.USER) {
            "id = ?"
        } else {
            "id = ? AND resolved_by != 'USER'"
        }
        db.execSQL(
            "UPDATE transactions SET kind = ?, category = ?, confidence = ?, resolved_by = ? WHERE $whereClause",
            arrayOf(kind.name, category?.name, confidence.toDouble(), resolvedBy.name, transactionId.toString())
        )
    }

    // Shared by getDebtLedger (grouped, all counterparts) and personNetBalance (one counterpart):
    // positive = they owe the user, negative = the user owes them. A repayment doesn't need to
    // match any single past transaction's amount — paying back several loans at once nets out
    // exactly the same as paying them back one at a time.
    private const val NET_BALANCE_EXPR = """
        SUM(CASE WHEN kind = 'LENT' THEN amount ELSE 0 END)
      - SUM(CASE WHEN kind = 'REPAYMENT_IN' THEN amount ELSE 0 END)
      - SUM(CASE WHEN kind = 'BORROWED' THEN amount ELSE 0 END)
      + SUM(CASE WHEN kind = 'REPAYMENT_OUT' THEN amount ELSE 0 END)
    """

    /** The running balance with one specific counterpart — the context the classifier needs to
     * recognize a credit from them as a repayment rather than guessing INCOME/EXPENSE blind. */
    private fun personNetBalance(db: SQLiteDatabase, counterpartName: String): Double {
        val key = MemoryStore.normalize(counterpartName)
        db.rawQuery(
            """SELECT $NET_BALANCE_EXPR FROM transactions
               WHERE counterpart_name = ? AND kind IN ('LENT', 'BORROWED', 'REPAYMENT_IN', 'REPAYMENT_OUT')""",
            arrayOf(key)
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getDouble(0)
        }
    }

    /**
     * Runs the on-device classifier against up to [limit] UNKNOWN transactions, oldest first,
     * and applies the guess immediately — no follow-up questions, the total/breakdown reflects
     * every transaction right away. Blocking and potentially slow (real LLM inference per
     * genuinely-new counterpart) — callers must invoke this from a background thread/coroutine,
     * never the main thread. A counterpart that already has a memory hit (e.g. classified
     * earlier in this same batch) skips the LLM entirely.
     *
     * Synchronized process-wide: this gets called both from the app's own resume and from the
     * SMS broadcast receiver's WorkManager job, and those can legitimately overlap (an SMS
     * arriving right as the app comes to the foreground). Without this, two concurrent passes
     * would both pick up the same UNKNOWN transaction, run the LLM on it twice, and whichever
     * one finishes last would win — occasionally landing after a user correction and silently
     * overwriting it (the resolved_by='USER' guard in [applyGuessToTransaction] is the backstop
     * for that; this lock is what stops the wasted double inference in the first place).
     */
    fun classifyUnknownTransactions(context: Context, limit: Int = 5): Unit = synchronized(classifyLock) {
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            val candidates = mutableListOf<Pair<Long, ParsedTransaction>>()
            db.rawQuery(
                "SELECT id, amount, counterpart_name, direction FROM transactions WHERE kind = 'UNKNOWN' ORDER BY timestamp ASC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    candidates.add(
                        cursor.getLong(0) to ParsedTransaction(
                            amount = cursor.getDouble(1),
                            counterpartName = cursor.getString(2),
                            direction = TransactionDirection.valueOf(cursor.getString(3)),
                        )
                    )
                }
            }
            if (candidates.isEmpty()) return
            val examples = MemoryStore.recentExamples(db)

            for ((transactionId, parsed) in candidates) {
                val memoryHit = MemoryStore.lookup(db, parsed.counterpartName)
                if (memoryHit != null && memoryHit.kind.matchesDirection(parsed.direction)) {
                    applyGuessToTransaction(db, transactionId, memoryHit.kind, memoryHit.category, 1.0f, ResolvedBy.MEMORY)
                    continue
                }
                val priorBalance = personNetBalance(db, parsed.counterpartName)
                val guess = TransactionClassifier.classify(context, parsed, examples, priorBalance) ?: continue
                applyGuessToTransaction(db, transactionId, guess.kind, guess.category, guess.confidence, ResolvedBy.LLM)
                MemoryStore.remember(db, parsed.counterpartName, guess.kind, guess.category, guess.isPerson)
            }
        } finally {
            db.close()
        }
    }

    /**
     * The user's free-text note on why the automatic tag for [transactionId] was wrong (e.g.
     * "this was lending money to Raj") — re-runs the classifier trusting the note over its own
     * assumption, applies the corrected guess, and remembers it so the same counterpart is
     * never misclassified again. Blocking/slow like [classifyUnknownTransactions], so callers
     * must invoke this off the main thread.
     */
    fun correctTransaction(context: Context, transactionId: Long, note: String) {
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            val parsed = db.rawQuery(
                "SELECT amount, counterpart_name, direction FROM transactions WHERE id = ?", arrayOf(transactionId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return
                ParsedTransaction(cursor.getDouble(0), cursor.getString(1), TransactionDirection.valueOf(cursor.getString(2)))
            }
            val examples = MemoryStore.recentExamples(db)
            val priorBalance = personNetBalance(db, parsed.counterpartName)
            val guess = TransactionClassifier.reclassifyWithNote(context, parsed, examples, priorBalance, note) ?: return
            applyGuessToTransaction(db, transactionId, guess.kind, guess.category, maxOf(guess.confidence, 0.9f), ResolvedBy.USER)
            MemoryStore.remember(db, parsed.counterpartName, guess.kind, guess.category, guess.isPerson)
        } finally {
            db.close()
        }
    }

    /**
     * The dashboard's free-text command bar — one instruction that can add a transaction the
     * SMS pipeline never saw (e.g. cash), delete an existing one, or retag one, all via the
     * same on-device model. Interprets against the dashboard's own most-recent transactions so
     * "remove the bookstore one" can resolve to a real row. A command the model can't map to
     * anything actionable is silently a no-op (ResolvedBy stays whatever it already was) rather
     * than guessing. Blocking/slow (LLM inference) — callers must invoke this off the main
     * thread.
     */
    fun runDashboardCommand(context: Context, command: String) {
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            val recent = mutableListOf<Transaction>()
            db.rawQuery(
                """SELECT id, amount, counterpart_name, direction, timestamp, kind, category, resolved_by
                   FROM transactions ORDER BY timestamp DESC LIMIT 20""",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    recent.add(
                        Transaction(
                            id = cursor.getLong(0),
                            amount = cursor.getDouble(1),
                            counterpartName = cursor.getString(2),
                            direction = TransactionDirection.valueOf(cursor.getString(3)),
                            timestamp = cursor.getLong(4),
                            kind = TransactionKind.fromStorage(cursor.getString(5)),
                            category = ExpenseCategory.fromStorage(cursor.getString(6)),
                            resolvedBy = ResolvedBy.fromStorage(cursor.getString(7)),
                        )
                    )
                }
            }
            val result = DashboardCommandInterpreter.interpret(context, command, recent) ?: return
            when (result.action) {
                DashboardCommandInterpreter.Action.ADD -> {
                    val values = ContentValues().apply {
                        put("sms_id", "manual-${System.currentTimeMillis()}-${(0..999999).random()}")
                        put("amount", result.amount)
                        put("counterpart_name", MemoryStore.normalize(result.counterpartName))
                        put("direction", result.kind.canonicalDirection().name)
                        put("raw_body", "Added manually via dashboard command: \"$command\"")
                        put("timestamp", System.currentTimeMillis())
                        put("kind", result.kind.name)
                        put("category", result.category?.name)
                        put("confidence", 1.0)
                        put("resolved_by", ResolvedBy.USER.name)
                    }
                    db.insert("transactions", null, values)
                }
                DashboardCommandInterpreter.Action.REMOVE -> {
                    val target = recent.getOrNull(result.targetIndex - 1) ?: return
                    db.delete("transactions", "id = ?", arrayOf(target.id.toString()))
                }
                DashboardCommandInterpreter.Action.MODIFY -> {
                    val target = recent.getOrNull(result.targetIndex - 1) ?: return
                    applyGuessToTransaction(db, target.id, result.kind, result.category, 1.0f, ResolvedBy.USER)
                    val isPerson = MemoryStore.lookup(db, target.counterpartName)?.isPerson ?: false
                    MemoryStore.remember(db, target.counterpartName, result.kind, result.category, isPerson)
                }
                DashboardCommandInterpreter.Action.NONE -> Unit
            }
        } finally {
            db.close()
        }
    }

    /**
     * The user says the current auto-tag is already correct — no LLM call needed, just
     * reinforces it (full confidence, resolved by the user) and strengthens the memory entry
     * so this counterpart's pattern is trusted more over time.
     */
    fun confirmTransaction(context: Context, transactionId: Long) {
        val db = ExpenseDbHelper(context).writableDatabase
        try {
            val row = db.rawQuery(
                "SELECT counterpart_name, kind, category FROM transactions WHERE id = ?", arrayOf(transactionId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return
                Triple(cursor.getString(0), TransactionKind.fromStorage(cursor.getString(1)), ExpenseCategory.fromStorage(cursor.getString(2)))
            }
            val (counterpart, kind, category) = row
            db.execSQL(
                "UPDATE transactions SET confidence = 1.0, resolved_by = ? WHERE id = ?",
                arrayOf(ResolvedBy.USER.name, transactionId.toString())
            )
            val isPerson = MemoryStore.lookup(db, counterpart)?.isPerson ?: false
            MemoryStore.remember(db, counterpart, kind, category, isPerson)
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
                """SELECT id, amount, counterpart_name, direction, timestamp, kind, category, resolved_by
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
                            resolvedBy = ResolvedBy.fromStorage(cursor.getString(7)),
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
                """SELECT counterpart_name, $NET_BALANCE_EXPR AS net
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
