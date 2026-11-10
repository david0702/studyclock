package com.dsh.studyclock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StudyDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createSegments(db)
        createHabits(db)
    }

    private fun createSegments(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE segments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER,
                note TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_segments_start ON segments(start_ms)")
        db.execSQL("CREATE INDEX idx_segments_end ON segments(end_ms)")
    }

    /** 每日必做清单：habits 是"每天循环的任务"，habit_checks 是"某天打勾了"。 */
    private fun createHabits(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE habits (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE habit_checks (
                habit_id INTEGER NOT NULL,
                day TEXT NOT NULL,
                checked_at INTEGER NOT NULL,
                PRIMARY KEY (habit_id, day)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_habit_checks_day ON habit_checks(day)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：加"大类"短标签列（历史记录一律为空串，数据不动）
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE segments ADD COLUMN category TEXT NOT NULL DEFAULT ''")
        }
        // v2 → v3：每日必做清单
        if (oldVersion < 3) {
            createHabits(db)
        }
    }

    companion object {
        private const val DB_NAME = "studyclock.db"
        private const val DB_VERSION = 3
    }
}
