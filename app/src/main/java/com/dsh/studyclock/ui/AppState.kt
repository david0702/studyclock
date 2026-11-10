package com.dsh.studyclock.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dsh.studyclock.data.AppSettings
import com.dsh.studyclock.data.DayMath
import com.dsh.studyclock.data.Segment
import com.dsh.studyclock.data.SegmentRepo
import java.time.LocalDate

/** 周历里的一块（可能只是某条跨天记录在本天被裁切出来的部分）。 */
class WeekBlock(
    val segmentId: Long,
    val startMs: Long,
    val endMs: Long,
    val note: String,
    val category: String,
    val isRunning: Boolean,
    val colorIndex: Int,
)

class DayColumn(
    val date: LocalDate,
    val windowStart: Long,
    val windowEnd: Long,
    val totalMs: Long,
    val blocks: List<WeekBlock>,
)

/**
 * 全部 UI 状态的唯一来源。时间一律从时间戳推导，tick() 只负责触发重绘。
 */
class AppState(
    private val repo: SegmentRepo,
    private val settings: AppSettings,
) {
    var rolloverHour by mutableStateOf(settings.rolloverHour)
        private set

    var dayMath by mutableStateOf(DayMath(settings.rolloverHour))
        private set

    var now by mutableStateOf(System.currentTimeMillis())
        private set

    var running by mutableStateOf<Segment?>(null)
        private set

    var todaySegments by mutableStateOf<List<Segment>>(emptyList())
        private set

    var todayStart by mutableStateOf(0L)
        private set

    var todayEnd by mutableStateOf(1L)
        private set

    /** 数据版本号：任何写操作后 +1，用来让周历等页面重新查询。 */
    var version by mutableStateOf(0)
        private set

    val isRunning: Boolean get() = running != null

    /** 当前这一段的实时时长；没在计时就是 0。 */
    val currentSessionMs: Long
        get() = running?.let { (now - it.startMs).coerceAtLeast(0L) } ?: 0L

    /** 今日累计（只算落在今天这个学习日内的时间）。 */
    val todayTotalMs: Long
        get() {
            if (todayEnd <= todayStart) return 0L
            return todaySegments.sumOf { seg ->
                val s = maxOf(seg.startMs, todayStart)
                val e = minOf(seg.endAt(now), todayEnd)
                (e - s).coerceAtLeast(0L)
            }
        }

    /** 是否有跑了很久没停的段（超过 [LONG_RUN_MS]）。 */
    val longRunningMs: Long
        get() {
            val seg = running ?: return 0L
            val d = now - seg.startMs
            return if (d > LONG_RUN_MS) d else 0L
        }

    fun refresh() {
        val t = System.currentTimeMillis()
        now = t
        running = repo.running()
        todayStart = dayMath.dayStart(t)
        todayEnd = dayMath.dayEnd(t)
        todaySegments = repo.overlapping(todayStart, todayEnd)
        version++
    }

    fun tick() {
        val t = System.currentTimeMillis()
        now = t
        // 跨过换日时刻（比如凌晨 0 点/4 点）时，重新归档今天的分段
        if (t >= todayEnd) refresh()
    }

    fun start() {
        repo.start(System.currentTimeMillis())
        refresh()
    }

    /** @return false 表示这一段太短（<1 秒）已被丢弃，什么也没记 */
    fun pause(): Boolean {
        val kept = repo.pause(System.currentTimeMillis())
        refresh()
        return kept
    }

    fun setNote(id: Long, category: String, note: String) {
        repo.update(id, category, note)
        refresh()
    }

    /** 编辑面板里的大类快捷候选（用过的分类，最近优先）。 */
    fun recentCategories(): List<String> = repo.recentCategories()

    fun delete(id: Long) {
        repo.delete(id)
        refresh()
    }

    fun segment(id: Long): Segment? = repo.byId(id)

    fun setRollover(hour: Int) {
        settings.rolloverHour = hour
        rolloverHour = hour
        dayMath = DayMath(hour)
        refresh()
    }

    /** 某一周（以周一标识）的 7 天数据。 */
    fun week(monday: LocalDate): List<DayColumn> {
        val t = System.currentTimeMillis()
        val weekStart = dayMath.startOf(monday)
        val weekEnd = dayMath.startOf(monday.plusDays(7))
        val segs = repo.overlapping(weekStart, weekEnd)
        return dayMath.weekDays(monday).map { date ->
            val s = dayMath.startOf(date)
            val e = dayMath.endOf(date)
            val blocks = ArrayList<WeekBlock>(4)
            var total = 0L
            segs.forEach { seg ->
                val bs = maxOf(seg.startMs, s)
                val be = minOf(seg.endAt(t), e)
                if (be > bs) {
                    blocks += WeekBlock(
                        segmentId = seg.id,
                        startMs = bs,
                        endMs = be,
                        note = seg.note,
                        category = seg.category,
                        isRunning = seg.isRunning,
                        colorIndex = colorOf(seg.id),
                    )
                    total += be - bs
                }
            }
            DayColumn(date, s, e, total, blocks)
        }
    }

    companion object {
        const val LONG_RUN_MS = 4 * 60 * 60 * 1000L
        private fun colorOf(id: Long): Int = ((id - 1).mod(8)).toInt()
    }
}
