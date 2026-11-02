package com.phaseslauncher.data.suggestions

import com.phaseslauncher.data.db.HistoryDao
import com.phaseslauncher.data.db.LaunchEventEntity
import com.phaseslauncher.data.db.ThemeSwitchEntity
import com.phaseslauncher.data.rules.ThemeRule
import com.phaseslauncher.data.themes.Theme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** Something the launcher noticed and offers to do. */
sealed interface Suggestion {
    /** Stable, so a dismissal sticks. */
    val id: String
    val themeId: Long

    /** [appKey] keeps being opened while [themeId] is on: add it to that theme? */
    data class AddApp(val appKey: String, override val themeId: Long, val launches: Int) : Suggestion {
        override val id get() = "add:$themeId:$appKey"
    }

    /** The user keeps switching to [themeId] around the same time: switch automatically? */
    data class Schedule(override val themeId: Long, val rule: ThemeRule.Schedule, val days: Int) : Suggestion {
        override val id get() = "schedule:$themeId:${rule.days.sorted().joinToString(",")}:${rule.startMinute}"
    }

    /** [appKey], which lives on [themeId], was opened from another theme: switch to its theme? */
    data class SwitchForApp(val appKey: String, override val themeId: Long) : Suggestion {
        override val id get() = "switch:$themeId:$appKey"
    }
}

/** What the user wants suggested; from Launcher settings. */
data class SuggestionSettings(
    val apps: Boolean = true,
    val schedules: Boolean = true,
    val switching: Boolean = true,
    /** Launches (within [LOOKBACK_DAYS]) before an app is suggested for a theme. */
    val minLaunches: Int = 5,
    /** Different days the user switched around the same time before a schedule is suggested. */
    val minDays: Int = 3,
)

/** Records what the user does, for [SuggestionEngine]. Only the last few weeks are kept. */
class HistoryRepository(private val dao: HistoryDao) {

    fun launches(now: Long = System.currentTimeMillis()): Flow<List<LaunchEventEntity>> =
        dao.observeLaunches(now - SuggestionEngine.LOOKBACK_DAYS * DAY_MS)

    fun switches(now: Long = System.currentTimeMillis()): Flow<List<ThemeSwitchEntity>> =
        dao.observeSwitches(now - SuggestionEngine.SCHEDULE_LOOKBACK_DAYS * DAY_MS)

    suspend fun recordLaunch(appKey: String, themeId: Long?, now: Long = System.currentTimeMillis()) {
        dao.insertLaunch(LaunchEventEntity(appKey = appKey, themeId = themeId, time = now))
        // The launcher's process can live for weeks; trim old history now and then, not only at start.
        if (now - lastPrune > DAY_MS) prune(now)
    }

    @Volatile private var lastPrune = 0L

    suspend fun recordManualSwitch(themeId: Long, now: Long = System.currentTimeMillis()) =
        dao.insertSwitch(ThemeSwitchEntity(themeId = themeId, time = now))

    suspend fun prune(now: Long = System.currentTimeMillis()) {
        lastPrune = now
        dao.pruneLaunches(now - KEEP_DAYS * DAY_MS)
        dao.pruneSwitches(now - KEEP_DAYS * DAY_MS)
    }

    suspend fun clear() {
        dao.clearLaunches()
        dao.clearSwitches()
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val KEEP_DAYS = 35
    }
}

/** Works out suggestions from the launch and switch history. Pure, so it is unit-tested. */
object SuggestionEngine {
    const val LOOKBACK_DAYS = 14
    const val SCHEDULE_LOOKBACK_DAYS = 28

    /** Switches within this many minutes of each other count as "around the same time". */
    private const val WINDOW_MINUTES = 60
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun compute(
        themes: List<Theme>,
        launches: List<LaunchEventEntity>,
        switches: List<ThemeSwitchEntity>,
        settings: SuggestionSettings,
        /** A theme's apps, including those in its folders. */
        appsOf: (Theme) -> Set<String>,
        /** Apps in the dock shown with a theme; they're always at hand, so never suggested. */
        dockOf: (Theme) -> Set<String>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): List<Suggestion> = buildList {
        // The history may reach further back than the windows; only recent behaviour counts.
        val recentLaunches = launches.filter { it.time >= now - LOOKBACK_DAYS * DAY_MS }
        val recentSwitches = switches.filter { it.time >= now - SCHEDULE_LOOKBACK_DAYS * DAY_MS }
        if (settings.apps) addAll(appSuggestions(themes, recentLaunches, settings, appsOf, dockOf))
        if (settings.schedules) addAll(scheduleSuggestions(themes, recentSwitches, settings, zone))
    }

    private fun appSuggestions(
        themes: List<Theme>,
        launches: List<LaunchEventEntity>,
        settings: SuggestionSettings,
        appsOf: (Theme) -> Set<String>,
        dockOf: (Theme) -> Set<String>,
    ): List<Suggestion.AddApp> {
        val totals = launches.groupingBy { it.appKey }.eachCount()
        return launches.filter { it.themeId != null }
            .groupingBy { it.themeId!! to it.appKey }
            .eachCount()
            .mapNotNull { (key, count) ->
                val (themeId, appKey) = key
                val theme = themes.firstOrNull { it.id == themeId } ?: return@mapNotNull null
                val mostlyHere = count * 2 >= (totals[appKey] ?: count)
                if (count < settings.minLaunches || !mostlyHere) return@mapNotNull null
                if (appKey in appsOf(theme) || appKey in dockOf(theme)) return@mapNotNull null
                Suggestion.AddApp(appKey, themeId, count)
            }
            .sortedByDescending { it.launches }
    }

    private fun scheduleSuggestions(
        themes: List<Theme>,
        switches: List<ThemeSwitchEntity>,
        settings: SuggestionSettings,
        zone: ZoneId,
    ): List<Suggestion.Schedule> {
        val times = switches.sortedBy { it.time }.map { it to LocalDateTime.ofInstant(Instant.ofEpochMilli(it.time), zone) }
        return times.groupBy { it.first.themeId }.mapNotNull { (themeId, entries) ->
            val theme = themes.firstOrNull { it.id == themeId } ?: return@mapNotNull null
            // The busiest hour-long window of the day, counted in distinct days.
            val best = entries.map { (_, at) -> at.hour * 60 + at.minute }.distinct().map { start ->
                entries.filter { (_, at) -> (at.hour * 60 + at.minute) in start until start + WINDOW_MINUTES }
            }.maxByOrNull { window -> window.map { it.second.toLocalDate() }.distinct().size } ?: return@mapNotNull null
            val dates = best.map { it.second.toLocalDate() }.distinct()
            if (dates.size < settings.minDays) return@mapNotNull null

            val minutes = best.map { it.second.hour * 60 + it.second.minute }.sorted()
            val start = minutes[minutes.size / 2] / 15 * 15
            val weekdays = dates.map { it.dayOfWeek }.toSet()
            val days = when {
                weekdays.all { it.value <= 5 } -> DayOfWeek.entries.take(5).toSet()
                weekdays.all { it.value >= 6 } -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                else -> DayOfWeek.entries.toSet()
            }
            // It ends when the user typically switched away again that day; two hours if they never did.
            val ends = best.mapNotNull { (switch, at) ->
                times.firstOrNull { (other, otherAt) ->
                    other.time > switch.time && other.themeId != themeId && otherAt.toLocalDate() == at.toLocalDate()
                }?.second?.let { it.hour * 60 + it.minute }
            }.sorted()
            val end = (ends.getOrNull(ends.size / 2)?.let { (it + 14) / 15 * 15 } ?: (start + 120)) % (24 * 60)
            val rule = ThemeRule.Schedule(days, start, if (end == start) (start + 60) % (24 * 60) else end)
            // Already covered by one of the theme's schedules.
            val covered = theme.activeRules.filterIsInstance<ThemeRule.Schedule>().any { existing ->
                days.all { day -> existing.isActive(dates.first().with(java.time.temporal.TemporalAdjusters.nextOrSame(day)).atTime(start / 60, start % 60)) }
            }
            if (covered) null else Suggestion.Schedule(themeId, rule, dates.size)
        }
    }
}
