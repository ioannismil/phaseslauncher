package com.phaseslauncher.data

import com.phaseslauncher.data.rules.AutoPriority
import com.phaseslauncher.data.rules.CalendarEvent
import com.phaseslauncher.data.rules.Conditions
import com.phaseslauncher.data.rules.GeoFix
import com.phaseslauncher.data.rules.RuleEngine
import com.phaseslauncher.data.rules.RuleMatch
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeAutomation
import com.phaseslauncher.data.rules.ThemeRule
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeIconKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RuleEngineTest {

    // 2026-10-26 is a Monday.
    private val mondayTen = LocalDateTime.of(2026, 10, 26, 10, 0)
    private val now = 1_000_000L

    private fun theme(
        id: Long,
        vararg rules: ThemeRule,
        automation: ThemeAutomation = ThemeAutomation(),
        disabled: Set<ThemeRule> = emptySet(),
    ) = Theme(
        id = id,
        name = "T$id",
        icon = ThemeIconKey.Star,
        accentColor = 0,
        appKeys = emptyList(),
        rules = rules.map { RuleSetting(it, enabled = it !in disabled) },
        automation = automation,
    )

    @Test
    fun aMoreSpecificRuleWinsAtTheSamePriority() {
        val work = theme(1, ThemeRule.WORK_HOURS)
        val gaming = theme(2, ThemeRule.Controller)
        val c = Conditions(controller = true)
        val matches = RuleEngine.matches(listOf(work, gaming), mondayTen, now, c)
        assertEquals(2L, RuleEngine.pick(listOf(work, gaming), matches)?.themeId)
    }

    @Test
    fun priorityBeatsSpecificity() {
        val work = theme(1, ThemeRule.WORK_HOURS, automation = ThemeAutomation(priority = AutoPriority.Highest))
        val gaming = theme(2, ThemeRule.Controller)
        val matches = RuleEngine.matches(listOf(work, gaming), mondayTen, now, Conditions(controller = true))
        assertEquals(1L, RuleEngine.pick(listOf(work, gaming), matches)?.themeId)
    }

    @Test
    fun allConditionsMustHoldWhenTheThemeSaysSo() {
        val bedtime = theme(
            1,
            ThemeRule.Charging,
            ThemeRule.WORK_HOURS,
            automation = ThemeAutomation(match = RuleMatch.All),
        )
        assertNull(RuleEngine.match(bedtime, mondayTen, now, Conditions(charging = false)))
        assertEquals(ThemeRule.Charging, RuleEngine.match(bedtime, mondayTen, now, Conditions(charging = true))?.rule)
    }

    @Test
    fun disabledRulesAreIgnored() {
        val work = theme(1, ThemeRule.WORK_HOURS, disabled = setOf(ThemeRule.WORK_HOURS))
        assertNull(RuleEngine.match(work, mondayTen, now, Conditions()))
    }

    @Test
    fun placesAllowForTheReadingsUncertainty() {
        val gym = ThemeRule.Location("Gym", 51.5, -0.12, 100)
        // About 150 m north.
        val nearby = GeoFix(51.50135, -0.12, accuracyMeters = 60f)
        assertTrue(RuleEngine.isInside(gym, nearby))
        assertFalse(RuleEngine.isInside(gym, nearby.copy(accuracyMeters = 10f)))
        assertFalse(RuleEngine.isInside(gym, GeoFix(51.6, -0.12, 20f)))
    }

    @Test
    fun calendarRulesMatchKeywordsCalendarsAndLeadTime() {
        val event = CalendarEvent(calendarId = 7, title = "Deep Focus block", begin = now + 4 * 60_000, end = now + 60 * 60_000, allDay = false, busy = true)
        val c = Conditions(events = listOf(event))
        val early = ThemeRule.Calendar(keywords = listOf("focus"), minutesBefore = 5)
        val onTime = ThemeRule.Calendar(keywords = listOf("focus"))
        assertTrue(RuleEngine.isMet(early, mondayTen, now, c))
        assertFalse(RuleEngine.isMet(onTime, mondayTen, now, c))
        assertFalse(RuleEngine.isMet(early.copy(keywords = listOf("gym")), mondayTen, now, c))
        assertFalse(RuleEngine.isMet(early.copy(calendarIds = setOf(3)), mondayTen, now, c))
        assertFalse(RuleEngine.isMet(early, mondayTen, now, Conditions(events = listOf(event.copy(busy = false)))))
    }

    @Test
    fun nextChangeIncludesCalendarEvents() {
        val event = CalendarEvent(1, "Meeting", begin = now + 30 * 60_000, end = now + 90 * 60_000, allDay = false, busy = true)
        val t = theme(1, ThemeRule.Calendar(minutesBefore = 10))
        val next = RuleEngine.nextChangeMillis(listOf(t), mondayTen, now, listOf(event)) { Long.MAX_VALUE }
        assertEquals(now + 20 * 60_000, next)
    }

    @Test
    fun rulesSurviveTheirJson() {
        val rules = listOf(
            ThemeRule.WORK_HOURS,
            ThemeRule.Location("Home", 1.5, -2.25, 250),
            ThemeRule.Calendar(setOf(4L, 9L), listOf("gym", "run"), includeAllDay = true, busyOnly = false, minutesBefore = 15),
            ThemeRule.Wifi("Office"),
            ThemeRule.Charging,
        )
        rules.forEach { rule ->
            assertEquals(rule, ThemeRule.fromJson(rule.type.name, ThemeRule.configOf(rule).toString()))
        }
    }

    @Test
    fun automationSettingsSurviveTheirJson() {
        val a = ThemeAutomation(AutoPriority.Low, RuleMatch.All, com.phaseslauncher.data.rules.AutoEndAction.Stay, askFirst = true, delayMinutes = 10)
        assertEquals(a, ThemeAutomation.fromJson(a.toJson()))
        assertEquals(ThemeAutomation(), ThemeAutomation.fromJson(""))
    }
}
