package com.focuslauncher.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DB_NAME = "expenses.db"
private const val DB_VERSION = 3

/**
 * Plain SQLite (no Room) — consistent with the rest of this app. Each version bump so far is a
 * full rebuild, not a migration — there's no sensible mapping from the old shape to the new one,
 * so upgrading just drops every table and recreates them. Version 3 drops the MCQ pending-
 * questions queue: transactions are now auto-tagged immediately with no follow-up questions, and
 * a wrong guess is corrected with a free-text note instead.
 */
class ExpenseDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_id TEXT UNIQUE,
                amount REAL NOT NULL,
                counterpart_name TEXT NOT NULL,
                direction TEXT NOT NULL,
                raw_body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                kind TEXT NOT NULL DEFAULT 'UNKNOWN',
                category TEXT,
                confidence REAL NOT NULL DEFAULT 0,
                resolved_by TEXT NOT NULL DEFAULT 'UNKNOWN'
            )
            """.trimIndent()
        )
        // The few-shot memory: every confirmed answer is both an instant-lookup cache for the
        // next transaction from the same counterpart and a labeled example fed to the LLM
        // prompt for the next genuinely-new one.
        db.execSQL(
            """
            CREATE TABLE memory (
                normalized_name TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                category TEXT,
                is_person INTEGER NOT NULL DEFAULT 0,
                confirm_count INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS transactions")
        db.execSQL("DROP TABLE IF EXISTS merchant_tags")
        db.execSQL("DROP TABLE IF EXISTS memory")
        db.execSQL("DROP TABLE IF EXISTS pending_questions")
        onCreate(db)
    }
}
