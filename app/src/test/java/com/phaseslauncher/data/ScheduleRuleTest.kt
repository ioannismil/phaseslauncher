package com.phaseslauncher.data

import com.phaseslauncher.data.rules.ThemeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleRuleTest {

    // 2026-10-26 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 26 + day, hour, minute)

    @Test
    fun workHoursApplyOnWeekdaysBetweenStartAndEnd() {
        val rule = ThemeRule.WORK_HOURS
        assertTrue(rule.isActive(at(0, 9)))
        assertTrue(rule.isActive(at(4, 16, 59)))
        assertFalse(rule.isActive(at(0, 8, 59)))
        assertFalse(rule.isActive(at(0, 17)))
        assertFalse(rule.isActive(at(5, 12))) // Saturday
    }

    @Test
    fun overnightRangesWrapPastMidnight() {
        val rule = ThemeRule.Schedule(setOf(DayOfWeek.FRIDAY), 22 * 60, 2 * 60)
        assertTrue(rule.isActive(at(4, 23))) // Friday night
        assertTrue(rule.isActive(at(5, 1))) // early Saturday belongs to Friday's range
        assertFalse(rule.isActive(at(5, 3)))
        assertFalse(rule.isActive(at(3, 23))) // Thursday
    }

    @Test
    fun nextChangeIsTheNearestStartOrEndAfterNow() {
        val rule = ThemeRule.Schedule(DayOfWeek.entries.toSet(), 9 * 60, 17 * 60)
        val morning = LocalDateTime.of(2026, 11, 2, 8, 30)
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), rule.nextChange(morning))
        assertEquals(LocalDateTime.of(2026, 11, 2, 17, 0), rule.nextChange(LocalDateTime.of(2026, 11, 2, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 0), rule.nextChange(LocalDateTime.of(2026, 11, 2, 18, 0)))
    }
}
