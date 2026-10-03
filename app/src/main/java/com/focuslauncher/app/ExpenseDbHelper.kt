package com.focuslauncher.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DB_NAME = "expenses.db"
private const val DB_VERSION = 1

/**
 * Plain SQLite (no Room) — this app has no other generated-code build steps, and the schema
 * here is small enough that raw SQL keeps things simple rather than pulling in a new Gradle
 * plugin just for two tables.
 */
class ExpenseDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_id TEXT UNIQUE,
                amount REAL NOT NULL,
                merchant TEXT NOT NULL,
                type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                category TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE merchant_tags (
                merchant TEXT PRIMARY KEY,
                category TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS transactions")
        db.execSQL("DROP TABLE IF EXISTS merchant_tags")
        onCreate(db)
    }
}
