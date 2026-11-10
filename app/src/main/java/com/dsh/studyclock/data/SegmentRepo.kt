package com.dsh.studyclock.data

import android.content.ContentValues
import android.database.Cursor

/**
 * 分段表的读写。数据量很小（一条记录一段），同步操作对 UI 没有可感影响。
 */
class SegmentRepo(private val helper: StudyDb) {

    private fun db() = helper.writableDatabase

    private fun read(c: Cursor): Segment {
        val id = c.getLong(c.getColumnIndexOrThrow("id"))
        val startMs = c.getLong(c.getColumnIndexOrThrow("start_ms"))
        val endIdx = c.getColumnIndexOrThrow("end_ms")
        val endMs = if (c.isNull(endIdx)) null else c.getLong(endIdx)
        val note = c.getString(c.getColumnIndexOrThrow("note")) ?: ""
        val catIdx = c.getColumnIndex("category")
        val category = if (catIdx >= 0) c.getString(catIdx) ?: "" else ""
        return Segment(id, startMs, endMs, note, category)
    }

    /** 当前正在进行的一段，没有则为 null。 */
    fun running(): Segment? {
        db().query(
            "segments", null, "end_ms IS NULL", null, null, null, "start_ms DESC", "1"
        ).use { c ->
            return if (c.moveToFirst()) read(c) else null
        }
    }

    /** 开始一段；如果已经在跑，直接返回已有的一段。 */
    fun start(now: Long): Long {
        running()?.let { return it.id }
        val values = ContentValues().apply {
            put("start_ms", now)
            put("note", "")
            put("category", "")
        }
        return db().insert("segments", null, values)
    }

    /**
     * 暂停：把正在跑的一段收尾，end 记为 [now]。
     * 如果这一段短于 1 秒（误点），直接删掉，不留下无意义记录。
     * @return true 表示正常收尾，false 表示没有在跑或已被丢弃
     */
    fun pause(now: Long): Boolean {
        val cur = running() ?: return false
        if (now - cur.startMs < DISCARD_UNDER_MS) {
            delete(cur.id)
            return false
        }
        val values = ContentValues().apply { put("end_ms", now) }
        db().update("segments", values, "id = ?", arrayOf(cur.id.toString()))
        return true
    }

    /** 与 [from, to) 有交集的段，按开始时间升序。进行中的段（end_ms 为空）天然算作有交集。 */
    fun overlapping(from: Long, to: Long): List<Segment> {
        val out = ArrayList<Segment>()
        // 注意：不要写成 COALESCE(end_ms, ?) > ?——表达式没有列亲和性，
        // SQLite 会把 INTEGER 和大数字的 TEXT 参数按存储类比较，已结束的段会永远匹配不到。
        db().query(
            "segments",
            null,
            "start_ms < ? AND (end_ms IS NULL OR end_ms > ?)",
            arrayOf(to.toString(), from.toString()),
            null, null, "start_ms ASC"
        ).use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        return out
    }

    fun byId(id: Long): Segment? {
        db().query("segments", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) read(c) else null
        }
    }

    /** 同时保存"大类"（短标签）和"小类/备注"（自由文本）。 */
    fun update(id: Long, category: String, note: String) {
        val values = ContentValues().apply {
            put("category", category.trim())
            put("note", note.trim())
        }
        db().update("segments", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        db().delete("segments", "id = ?", arrayOf(id.toString()))
    }

    fun all(): List<Segment> {
        val out = ArrayList<Segment>()
        db().query("segments", null, null, null, null, null, "start_ms ASC").use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        return out
    }

    /** 用过的"大类"，按最近使用时间倒序，用来在编辑面板里做快捷选择。 */
    fun recentCategories(limit: Int = 12): List<String> {
        val out = ArrayList<String>()
        db().query(
            false,
            "segments",
            arrayOf("category", "MAX(start_ms) AS last_used"),
            "category <> ''",
            null,
            "category",
            null,
            "last_used DESC",
            limit.toString(),
        ).use { c ->
            while (c.moveToNext()) {
                val value = c.getString(0) ?: ""
                if (value.isNotBlank()) out.add(value)
            }
        }
        return out
    }

    companion object {
        private const val DISCARD_UNDER_MS = 1000L
    }
}
