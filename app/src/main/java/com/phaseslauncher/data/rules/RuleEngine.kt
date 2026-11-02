package com.phaseslauncher.data.rules

import com.phaseslauncher.data.themes.Theme
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A location reading. */
data class GeoFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

/** What the device is connected to (and where it is) right now, for matching rules. */
data class Conditions(
    val bluetooth: Set<String> = emptySet(),
    val controller: Boolean = false,
    val wifiSsid: String? = null,
    val headphones: Boolean = false,
    val charging: Boolean = false,
    val location: GeoFix? = null,
    val events: List<CalendarEvent> = emptyList(),
)

/** A theme whose conditions apply, and the rule that best explains why. */
data class ThemeMatch(val themeId: Long, val rule: ThemeRule)

/**
 * Decides which theme the rules want. Pure, so it is unit-tested; [AutoSwitcher] feeds it the
 * device's state.
 */
object RuleEngine {

    fun isMet(rule: ThemeRule, now: LocalDateTime, nowMillis: Long, c: Conditions): Boolean = when (rule) {
        is ThemeRule.Schedule -> rule.isActive(now)
        is ThemeRule.Bluetooth -> rule.address in c.bluetooth
        ThemeRule.Controller -> c.controller
        is ThemeRule.Wifi -> c.wifiSsid != null && rule.ssid == c.wifiSsid
        ThemeRule.Headphones -> c.headphones
        ThemeRule.Charging -> c.charging
        is ThemeRule.Location -> c.location?.let { isInside(rule, it) } ?: false
        is ThemeRule.Calendar -> c.events.any { rule.isActive(it, nowMillis) }
    }

    /** The theme's most specific met rule if its conditions apply (any or all, per its settings), else null. */
    fun match(theme: Theme, now: LocalDateTime, nowMillis: Long, c: Conditions): ThemeMatch? {
        val rules = theme.activeRules
        if (rules.isEmpty()) return null
        val met = rules.filter { isMet(it, now, nowMillis, c) }
        val applies = when (theme.automation.match) {
            RuleMatch.Any -> met.isNotEmpty()
            RuleMatch.All -> met.size == rules.size
        }
        if (!applies) return null
        return ThemeMatch(theme.id, met.minBy { it.type.ordinal })
    }

    /** Every theme whose conditions apply now. */
    fun matches(themes: List<Theme>, now: LocalDateTime, nowMillis: Long, c: Conditions): List<ThemeMatch> =
        themes.mapNotNull { match(it, now, nowMillis, c) }

    /**
     * The winner among [candidates]: the highest priority, then the most specific rule (a game
     * controller beats a schedule), then the theme that comes first in the switcher.
     */
    fun pick(themes: List<Theme>, candidates: List<ThemeMatch>): ThemeMatch? {
        val order = themes.withIndex().associate { (i, t) -> t.id to i }
        val byId = themes.associateBy { it.id }
        return candidates.minWithOrNull(
            compareByDescending<ThemeMatch> { byId[it.themeId]?.automation?.priority?.ordinal ?: 0 }
                .thenBy { it.rule.type.ordinal }
                .thenBy { order[it.themeId] ?: Int.MAX_VALUE },
        )
    }

    /** Inside the place, allowing for the reading's uncertainty (up to the place's own radius). */
    fun isInside(place: ThemeRule.Location, fix: GeoFix): Boolean =
        distanceMeters(place.latitude, place.longitude, fix.latitude, fix.longitude) <=
            place.radiusMeters + min(fix.accuracyMeters.toDouble(), place.radiusMeters.toDouble())

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** The next moment a schedule or calendar rule may start or stop applying, after [now]. */
    fun nextChangeMillis(themes: List<Theme>, now: LocalDateTime, nowMillis: Long, events: List<CalendarEvent>, zoneOffsetMillis: (LocalDateTime) -> Long): Long? {
        val rules = themes.flatMap { it.activeRules }
        val schedule = rules.filterIsInstance<ThemeRule.Schedule>().minOfOrNull { zoneOffsetMillis(it.nextChange(now)) }
        val calendar = rules.filterIsInstance<ThemeRule.Calendar>().flatMap { rule ->
            events.filter(rule::matches).flatMap { listOf(it.begin - rule.minutesBefore * 60_000L, it.end) }
        }.filter { it > nowMillis }.minOrNull()
        return listOfNotNull(schedule, calendar).minOrNull()
    }

    private const val EARTH_RADIUS_M = 6_371_000.0
}
