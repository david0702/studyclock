package com.dsh.studyclock

import com.dsh.studyclock.data.DayMath
import com.dsh.studyclock.data.HabitStats
import com.dsh.studyclock.data.csvCell
import com.dsh.studyclock.data.isNewerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PureTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun epoch(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(shanghai).toInstant().toEpochMilli()

    // ---- 版本号 ----

    @Test
    fun versionCompare_threeParts() {
        assertTrue(isNewerVersion("1.10.1", "1.9"))
        assertTrue(isNewerVersion("1.10.1", "1.10.0"))
        assertTrue(isNewerVersion("1.9.1", "1.9"))
        assertFalse(isNewerVersion("1.9", "1.9"))
        assertFalse(isNewerVersion("1.9.0", "1.9.1"))
        assertTrue(isNewerVersion("2.0", "1.99.99"))
    }

    // ---- 换日 / 学习日 ----

    @Test
    fun dayMath_rollover0_midnightBoundary() {
        val dm = DayMath(0, shanghai)
        assertEquals(LocalDate.of(2026, 1, 1), dm.studyDate(epoch(2026, 1, 1, 0, 30)))
        assertEquals(epoch(2026, 1, 1, 0), dm.dayStart(epoch(2026, 1, 1, 0, 30)))
        assertEquals(epoch(2026, 1, 2, 0), dm.dayEnd(epoch(2026, 1, 1, 0, 30)))
    }

    @Test
    fun dayMath_rollover4_countsEarlyMorningAsPreviousDay() {
        val dm = DayMath(4, shanghai)
        assertEquals(LocalDate.of(2025, 12, 31), dm.studyDate(epoch(2026, 1, 1, 2)))
        assertEquals(epoch(2025, 12, 31, 4), dm.dayStart(epoch(2026, 1, 1, 2)))
        assertEquals(epoch(2026, 1, 1, 4), dm.dayEnd(epoch(2026, 1, 1, 2)))
        // 同一时刻按 0 点换日就属于 1 月 1 日
        assertEquals(LocalDate.of(2026, 1, 1), DayMath(0, shanghai).studyDate(epoch(2026, 1, 1, 2)))
    }

    @Test
    fun dayMath_noonIsSameDayUnderAnyRollover() {
        for (rollover in 0..6) {
            assertEquals(
                LocalDate.of(2026, 3, 5),
                DayMath(rollover, shanghai).studyDate(epoch(2026, 3, 5, 12)),
            )
        }
    }

    // ---- 清单统计 ----

    @Test
    fun habitStreak_countsBackFromToday() {
        val today = LocalDate.of(2026, 1, 10)
        val days = setOf("2026-01-10", "2026-01-09", "2026-01-08", "2026-01-06")
        assertEquals(3, HabitStats.streak(days, today))
    }

    @Test
    fun habitStreak_todayUncheckedDoesNotBreakStreak() {
        val today = LocalDate.of(2026, 1, 10)
        val days = setOf("2026-01-09", "2026-01-08")
        assertEquals(2, HabitStats.streak(days, today))
    }

    @Test
    fun habitStreak_emptyIsZero() {
        assertEquals(0, HabitStats.streak(emptySet(), LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun last7Days_oldToNew() {
        val today = LocalDate.of(2026, 1, 10)
        val days = setOf("2026-01-10", "2026-01-05")
        assertEquals(
            listOf(false, true, false, false, false, false, true),
            HabitStats.lastNDays(days, today, 7),
        )
    }

    // ---- CSV 转义 ----

    @Test
    fun csvCell_escapesSpecialChars() {
        assertEquals("abc", csvCell("abc"))
        assertEquals("\"a,b\"", csvCell("a,b"))
        assertEquals("\"he said \"\"hi\"\"\"", csvCell("he said \"hi\""))
        assertEquals("\"line1\nline2\"", csvCell("line1\nline2"))
    }
}
