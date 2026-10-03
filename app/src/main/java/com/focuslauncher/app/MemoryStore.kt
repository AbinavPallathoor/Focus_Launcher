package com.focuslauncher.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * The few-shot/instant-lookup memory: once a counterpart (merchant or person) has been resolved
 * once — by the user, the LLM, or seeded directly — it's recognized instantly next time with no
 * LLM call at all, and feeds as a labeled example when the LLM *is* needed for someone new. This
 * is the main lever for "works well even with weaker models": the model is pattern-matching the
 * user's own past answers, not reasoning from nothing.
 */
object MemoryStore {

    data class MemoryEntry(
        val normalizedName: String,
        val kind: TransactionKind,
        val category: ExpenseCategory?,
        val isPerson: Boolean,
        val confirmCount: Int,
    )

    fun normalize(name: String): String = name.trim().uppercase()

    fun lookup(db: SQLiteDatabase, counterpartName: String): MemoryEntry? {
        val key = normalize(counterpartName)
        db.query(
            "memory",
            arrayOf("normalized_name", "kind", "category", "is_person", "confirm_count"),
            "normalized_name = ?",
            arrayOf(key),
            null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return MemoryEntry(
                normalizedName = cursor.getString(0),
                kind = TransactionKind.fromStorage(cursor.getString(1)),
                category = ExpenseCategory.fromStorage(cursor.getString(2)),
                isPerson = cursor.getInt(3) != 0,
                confirmCount = cursor.getInt(4),
            )
        }
    }

    /** Records or reinforces a counterpart's classification — call on every confirmed answer. */
    fun remember(
        db: SQLiteDatabase,
        counterpartName: String,
        kind: TransactionKind,
        category: ExpenseCategory?,
        isPerson: Boolean,
    ) {
        val key = normalize(counterpartName)
        val existing = lookup(db, counterpartName)
        val values = ContentValues().apply {
            put("normalized_name", key)
            put("kind", kind.name)
            put("category", category?.name)
            put("is_person", if (isPerson) 1 else 0)
            put("confirm_count", (existing?.confirmCount ?: 0) + 1)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("memory", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Recent examples for few-shot prompting, most-recently-confirmed first. */
    fun recentExamples(db: SQLiteDatabase, limit: Int = 8): List<MemoryEntry> {
        val result = mutableListOf<MemoryEntry>()
        db.rawQuery(
            """SELECT normalized_name, kind, category, is_person, confirm_count FROM memory
               ORDER BY updated_at DESC LIMIT ?""",
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    MemoryEntry(
                        normalizedName = cursor.getString(0),
                        kind = TransactionKind.fromStorage(cursor.getString(1)),
                        category = ExpenseCategory.fromStorage(cursor.getString(2)),
                        isPerson = cursor.getInt(3) != 0,
                        confirmCount = cursor.getInt(4),
                    )
                )
            }
        }
        return result
    }
}
