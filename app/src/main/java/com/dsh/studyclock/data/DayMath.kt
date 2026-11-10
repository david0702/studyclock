package com.dsh.studyclock.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「学习日」的边界计算。
 *
 * 默认一天从 00:00 起，但可以在设置里改成 0–6 点任意时刻（夜猫子用）。
 * 注意：**数据永远不按天拆分**，跨零点的段仍是一条记录，只有在周历/汇总展示时才按天裁切。
 */
class DayMath(val rolloverHour: Int) {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 某个时刻属于哪个「学习日」（本地日期）。 */
    fun studyDate(t: Long): LocalDate {
        val z = Instant.ofEpochMilli(t).atZone(zone)
        return if (z.hour < rolloverHour) z.toLocalDate().minusDays(1) else z.toLocalDate()
    }

    fun startOf(date: LocalDate): Long =
        date.atTime(rolloverHour, 0).atZone(zone).toInstant().toEpochMilli()

    fun endOf(date: LocalDate): Long = startOf(date.plusDays(1))

    fun dayStart(t: Long): Long = startOf(studyDate(t))

    fun dayEnd(t: Long): Long = endOf(studyDate(t))

    /** 学习日对应的周一。 */
    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** 某一周（以周一标识）的 7 个学习日。 */
    fun weekDays(monday: LocalDate): List<LocalDate> = (0..6).map { monday.plusDays(it.toLong()) }
}
