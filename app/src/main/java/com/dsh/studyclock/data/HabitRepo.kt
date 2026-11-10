package com.dsh.studyclock.data

import android.content.ContentValues
import java.time.LocalDate

/** 一条「每日必做」任务。 */
data class Habit(
    val id: Long,
    val name: String,
    val sortOrder: Int,
)

/**
 * 每日必做清单：任务定义 + 每天打勾记录。
 * 「日期」用的是"学习日"（受换日时刻设置影响），由调用方传入 day 字符串（yyyy-MM-dd）。
 */
class HabitRepo(private val helper: StudyDb) {

    private fun db() = helper.writableDatabase

    fun habits(): List<Habit> {
        val out = ArrayList<Habit>()
        db().query(
            "habits", arrayOf("id", "name", "sort_order"),
            "archived = 0", null, null, null, "sort_order ASC, id ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Habit(c.getLong(0), c.getString(1) ?: "", c.getInt(2)))
            }
        }
        return out
    }

    fun addHabit(name: String, now: Long): Long {
        val maxOrder = db().rawQuery("SELECT COALESCE(MAX(sort_order), 0) FROM habits", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("sort_order", maxOrder + 1)
            put("created_at", now)
            put("archived", 0)
        }
        return db().insert("habits", null, values)
    }

    fun renameHabit(id: Long, name: String) {
        val values = ContentValues().apply { put("name", name.trim()) }
        db().update("habits", values, "id = ?", arrayOf(id.toString()))
    }

    /** 软删除：把任务归档，历史打勾记录保留（将来可做统计）。 */
    fun archiveHabit(id: Long) {
        val values = ContentValues().apply { put("archived", 1) }
        db().update("habits", values, "id = ?", arrayOf(id.toString()))
    }

    /** 今天（学习日）已打勾的任务 id 集合。 */
    fun checkedOn(day: String): Set<Long> {
        val out = HashSet<Long>()
        db().query("habit_checks", arrayOf("habit_id"), "day = ?", arrayOf(day),
            null, null, null).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0))
        }
        return out
    }

    /** 切换某天某个任务的勾选状态。 */
    fun toggle(habitId: Long, day: String, now: Long): Boolean {
        val checked = db().query(
            "habit_checks", arrayOf("habit_id"), "habit_id = ? AND day = ?",
            arrayOf(habitId.toString(), day), null, null, null,
        ).use { it.moveToFirst() }
        return if (checked) {
            db().delete("habit_checks", "habit_id = ? AND day = ?", arrayOf(habitId.toString(), day))
            false
        } else {
            val values = ContentValues().apply {
                put("habit_id", habitId)
                put("day", day)
                put("checked_at", now)
            }
            db().insertWithOnConflict("habit_checks", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            true
        }
    }

    /** 从 [fromDay] 起（含）每个任务的打勾日期集合，用于算连续天数 / 最近 7 天。 */
    fun checkDaysSince(fromDay: String): Map<Long, Set<String>> {
        val out = HashMap<Long, MutableSet<String>>()
        db().query(
            "habit_checks", arrayOf("habit_id", "day"), "day >= ?", arrayOf(fromDay),
            null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out.getOrPut(id) { HashSet() }.add(c.getString(1) ?: "")
            }
        }
        return out
    }

    /** 连续完成天数：今天没勾则从昨天往前算（今天还没做不算断）。 */
    fun streak(days: Set<String>, today: LocalDate): Int = HabitStats.streak(days, today)

    /** 最近 [count] 天（含今天）是否完成，返回从旧到新。 */
    fun lastNDays(days: Set<String>, today: LocalDate, count: Int): List<Boolean> =
        HabitStats.lastNDays(days, today, count)
}
