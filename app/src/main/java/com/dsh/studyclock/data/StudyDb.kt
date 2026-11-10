package com.dsh.studyclock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StudyDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：加"大类"短标签列（历史记录一律为空串，数据不动）
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE segments ADD COLUMN category TEXT NOT NULL DEFAULT ''")
        }
    }

    companion object {
        private const val DB_NAME = "studyclock.db"
        private const val DB_VERSION = 2
    }
}
