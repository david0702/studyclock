package com.dsh.studyclock.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.ROOT)
    private val dateFmt = SimpleDateFormat("M月d日", Locale.ROOT)
    private val weekFmt = SimpleDateFormat("M月d日", Locale.ROOT)
    private val csvFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /** 计时器大字：01:23:45，超过 99 小时照常显示。 */
    fun hms(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
    }

    fun clock(ms: Long): String = clockFmt.format(Date(ms))

    fun date(ms: Long): String = dateFmt.format(Date(ms))

    fun weekRange(startMs: Long, endMs: Long): String =
        "${weekFmt.format(Date(startMs))} – ${weekFmt.format(Date(endMs))}"

    fun csvTime(ms: Long): String = csvFmt.format(Date(ms))

    /** 「1 小时 12 分」这种自然语言时长。 */
    fun dur(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        if (safe < 60_000L) return "${safe / 1000} 秒"
        val totalMin = safe / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m 分"
            m == 0L -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }

    /** 周历列头用的紧凑时长：45分 / 1h20。 */
    fun durShort(ms: Long): String {
        val totalMin = (ms.coerceAtLeast(0)) / 60_000L
        if (totalMin < 60) return "${totalMin}分"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (m == 0L) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
    }

    /** 分段的时间范围；跨天时给开始日加个日期前缀。 */
    fun range(startMs: Long, endMs: Long, crossesDay: Boolean): String {
        val start = if (crossesDay) "昨 ${clock(startMs)}" else clock(startMs)
        return "$start – ${clock(endMs)}"
    }

    /** 字节数：3.4 MB / 812 KB。 */
    fun bytes(n: Long): String {
        if (n <= 0) return "0 B"
        val kb = n / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.ROOT, "%.1f MB", mb)
    }

    /** 网速：1.2 MB/s。 */
    fun speed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "—"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB/s", kb)
        return String.format(Locale.ROOT, "%.1f MB/s", kb / 1024.0)
    }
}