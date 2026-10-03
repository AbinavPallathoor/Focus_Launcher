package com.focuslauncher.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DB_NAME = "expenses.db"
private const val DB_VERSION = 2

/**
 * Plain SQLite (no Room) — consistent with the rest of this app. Version 2 is a full rebuild,
 * not a migration: it replaces simple category-only tagging with a richer model (expense vs.
 * lent/borrowed/repayment, confidence, who resolved it) that the on-device LLM classifier needs,
 * and there's no sensible mapping from "a category" to "a kind + category + counterpart" — the
 * user asked to wipe everything and start fresh, so upgrading just drops the old tables.
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
        // The home screen's MCQ card queue. Up to 4 rows per transaction (question_index 0-3).
        db.execSQL(
            """
            CREATE TABLE pending_questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_id INTEGER NOT NULL,
                question_index INTEGER NOT NULL,
                question_text TEXT NOT NULL,
                options_json TEXT NOT NULL,
                answered_option TEXT
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
