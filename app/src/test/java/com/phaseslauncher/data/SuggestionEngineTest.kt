package com.phaseslauncher.data

import com.phaseslauncher.data.db.LaunchEventEntity
import com.phaseslauncher.data.db.ThemeSwitchEntity
import com.phaseslauncher.data.focus.BlockMode
import com.phaseslauncher.data.focus.LimitScope
import com.phaseslauncher.data.focus.NotificationFilter
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeRule
import com.phaseslauncher.data.suggestions.Suggestion
import com.phaseslauncher.data.suggestions.SuggestionEngine
import com.phaseslauncher.data.suggestions.SuggestionSettings
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeIconKey
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, 26 + day, hour, minute).toInstant(zone).toEpochMilli() // 26 Oct 2026 is a Monday

    private val work = Theme(1, "Work", ThemeIconKey.Work, 0, listOf("mail/.Main"))
    private val social = Theme(2, "Social", ThemeIconKey.Social, 0, listOf("chat/.Main"))
    private val themes = listOf(work, social)

    private fun compute(
        launches: List<LaunchEventEntity> = emptyList(),
        switches: List<ThemeSwitchEntity> = emptyList(),
        settings: SuggestionSettings = SuggestionSettings(),
        themes: List<Theme> = this.themes,
    ) = SuggestionEngine.compute(themes, launches, switches, settings, { it.appKeys.toSet() }, { setOf("phone/.Main") }, zone, now = at(4, 12))

    @Test
    fun anAppOpenedOftenOnAThemeIsSuggestedForIt() {
        val launches = List(5) { LaunchEventEntity(appKey = "slack/.Main", themeId = 1, time = at(0, 9 + it)) }
        val suggestions = compute(launches)
        assertEquals(listOf(Suggestion.AddApp("slack/.Main", 1, 5)), suggestions)
    }

    @Test
    fun appsAlreadyThereInTheDockOrRarelyUsedAreNotSuggested() {
        val inTheme = List(6) { LaunchEventEntity(appKey = "mail/.Main", themeId = 1, time = at(0, 9)) }
        val inDock = List(6) { LaunchEventEntity(appKey = "phone/.Main", themeId = 1, time = at(0, 9)) }
        val rare = List(4) { LaunchEventEntity(appKey = "slack/.Main", themeId = 1, time = at(0, 9)) }
        assertTrue(compute(inTheme + inDock + rare).isEmpty())
        // ...unless the threshold is lowered.
        assertEquals(1, compute(rare, settings = SuggestionSettings(minLaunches = 3)).size)
    }

    @Test
    fun anAppUsedEverywhereEquallyIsNotTiedToOneTheme() {
        val launches = List(6) { LaunchEventEntity(appKey = "maps/.Main", themeId = 1, time = at(0, 9)) } +
            List(10) { LaunchEventEntity(appKey = "maps/.Main", themeId = 2, time = at(0, 20)) }
        // Mostly on Social, so only there.
        assertEquals(listOf(2L), compute(launches).map { it.themeId })
    }

    @Test
    fun switchingAroundTheSameTimeOnWeekdaysSuggestsAWeekdaySchedule() {
        val switches = listOf(
            ThemeSwitchEntity(themeId = 1, time = at(0, 8, 55)),
            ThemeSwitchEntity(themeId = 2, time = at(0, 17, 30)),
            ThemeSwitchEntity(themeId = 1, time = at(1, 9, 5)),
            ThemeSwitchEntity(themeId = 2, time = at(1, 17, 40)),
            ThemeSwitchEntity(themeId = 1, time = at(2, 9, 10)),
        )
        val schedule = compute(switches = switches).filterIsInstance<Suggestion.Schedule>().single { it.themeId == 1L }
        assertEquals(DayOfWeek.entries.take(5).toSet(), schedule.rule.days)
        assertEquals(9 * 60, schedule.rule.startMinute)
        assertEquals(17 * 60 + 45, schedule.rule.endMinute)
        assertEquals(3, schedule.days)
    }

    @Test
    fun aThemeAlreadyOnThatScheduleGetsNoSuggestion() {
        val switches = List(3) { ThemeSwitchEntity(themeId = 1, time = at(it, 9)) }
        val scheduled = work.copy(rules = listOf(RuleSetting(ThemeRule.WORK_HOURS)))
        assertTrue(compute(switches = switches, themes = listOf(scheduled, social)).isEmpty())
        // Two days isn't a pattern yet.
        assertTrue(compute(switches = switches.take(2)).isEmpty())
    }

    @Test
    fun oldHistoryDoesNotCount() {
        val launches = List(6) { LaunchEventEntity(appKey = "slack/.Main", themeId = 1, time = at(-20, 9)) }
        assertTrue(compute(launches).isEmpty())
    }

    @Test
    fun turnedOffKindsAreNotSuggested() {
        val launches = List(8) { LaunchEventEntity(appKey = "slack/.Main", themeId = 1, time = at(0, 9)) }
        val switches = List(3) { ThemeSwitchEntity(themeId = 1, time = at(it, 9)) }
        assertTrue(compute(launches, switches, SuggestionSettings(apps = false, schedules = false)).isEmpty())
    }

    @Test
    fun focusSettingsSurviveTheirJson() {
        val focus = ThemeFocus(
            blockedApps = setOf("a/.A", "b/.B"),
            blockOtherApps = true,
            blockMode = BlockMode.Block,
            pauseSeconds = 10,
            allowMinutes = 15,
            hideBlockedApps = true,
            notificationFilter = NotificationFilter.Chosen,
            allowedNotificationApps = setOf("c/.C"),
            hideNotifications = true,
            allowUrgent = false,
            appLimits = mapOf("d/.D" to 45),
            themeLimitMinutes = 60,
            limitScope = LimitScope.Always,
            limitMode = BlockMode.Block,
        )
        assertEquals(focus, ThemeFocus.fromJson(focus.toJson()))
        assertTrue(focus.blocks("z/.Z", themeKeys = setOf("a/.A")))
        assertTrue(!focus.copy(blockOtherApps = false).blocks("z/.Z", themeKeys = emptySet()))
    }
}
