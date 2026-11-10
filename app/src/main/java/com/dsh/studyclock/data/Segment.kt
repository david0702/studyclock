package com.dsh.studyclock.data

/**
 * 一条学习分段记录。
 *
 * [endMs] 为 null 表示这一段正在进行中（还没有暂停）。
 * [category] 是"大类"：一个短标签（如「数学」「英语」），会显示在周历色块上，可为空。
 * [note] 是"小类/备注"：自由文本，写具体做了什么。
 *
 * 时长一律由时间戳推导，不做累加，所以进程被杀、手机重启都不会丢时间。
 */
data class Segment(
    val id: Long,
    val startMs: Long,
    val endMs: Long?,
    val note: String,
    val category: String = "",
) {
    val isRunning: Boolean get() = endMs == null

    fun endAt(now: Long): Long = endMs ?: now

    fun durationAt(now: Long): Long = (endAt(now) - startMs).coerceAtLeast(0L)
}
