package com.dsh.studyclock.data

import java.time.LocalDate

/**
 * 纯函数区：不依赖 Android，方便用 JVM 单元测试（`gradle test` 秒级回归）。
 * UI 与仓库层都调这里的实现，避免逻辑漂移。
 */

/** 比较 "1.10.1" 这类版本号，宽松处理非数字段。 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().split(".", "-", "+")
        .mapNotNull { it.toIntOrNull() }
        .let { it + List(maxOf(0, 3 - it.size)) { 0 } }

    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** 每日必做清单的统计逻辑。 */
object HabitStats {

    /** 连续完成天数：今天还没打勾则从昨天往前算（今天不算断）。 */
    fun streak(days: Set<String>, today: LocalDate): Int {
        var d = today
        if (!days.contains(d.toString())) d = d.minusDays(1)
        var n = 0
        while (days.contains(d.toString())) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** 最近 [count] 天（含今天）是否完成，从旧到新。 */
    fun lastNDays(days: Set<String>, today: LocalDate, count: Int): List<Boolean> =
        (count - 1 downTo 0).map { back -> days.contains(today.minusDays(back.toLong()).toString()) }
}

/** CSV 单元格转义（含逗号/引号/换行时加引号并转义）。 */
fun csvCell(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value
