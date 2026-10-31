package com.themelauncher.data.rules

import com.themelauncher.data.db.ThemeRuleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Rule types, most specific first: when two themes have the same priority, the more specific match wins. */
enum class RuleType { Controller, Bluetooth, Headphones, Location, Wifi, Calendar, Charging, Schedule }

/** A condition that, while true, makes ThemeLauncher switch to the rule's theme. */
sealed interface ThemeRule {
    val type: RuleType

    /** Active on [days] between [startMinute] and [endMinute] (minutes after midnight). Overnight ranges wrap. */
    data class Schedule(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int) : ThemeRule {
        override val type get() = RuleType.Schedule

        fun isActive(now: LocalDateTime): Boolean {
            val minute = now.hour * 60 + now.minute
            return if (startMinute <= endMinute) {
                now.dayOfWeek in days && minute in startMinute until endMinute
            } else {
                // e.g. 22:00-06:00: the evening part belongs to today, the early part to yesterday.
                (now.dayOfWeek in days && minute >= startMinute) ||
                    (now.dayOfWeek.minus(1) in days && minute < endMinute)
            }
        }

        /** The next time (after [now]) this rule may start or stop applying. */
        fun nextChange(now: LocalDateTime): LocalDateTime =
            listOf(startMinute, endMinute).minOf { minute ->
                val today = now.toLocalDate().atTime(LocalTime.of(minute / 60, minute % 60))
                if (today.isAfter(now)) today else today.plusDays(1)
            }
    }

    /** Active while this Bluetooth device (e.g. the car) is connected. */
    data class Bluetooth(val address: String, val name: String) : ThemeRule {
        override val type get() = RuleType.Bluetooth
    }

    /** Active while any game controller is connected. */
    data object Controller : ThemeRule {
        override val type get() = RuleType.Controller
    }

    /** Active while connected to the Wi-Fi network named [ssid] (e.g. home or the office). */
    data class Wifi(val ssid: String) : ThemeRule {
        override val type get() = RuleType.Wifi
    }

    /** Active while wired, USB or Bluetooth headphones are connected. */
    data object Headphones : ThemeRule {
        override val type get() = RuleType.Headphones
    }

    /** Active while the phone is charging. */
    data object Charging : ThemeRule {
        override val type get() = RuleType.Charging
    }

    /** Active while the phone is within [radiusMeters] of a place (e.g. the gym). */
    data class Location(val name: String, val latitude: Double, val longitude: Double, val radiusMeters: Int) : ThemeRule {
        override val type get() = RuleType.Location
    }

    /**
     * Active during a calendar event. [calendarIds] empty means every calendar; [keywords] (any of
     * them, in the title) empty means every event.
     */
    data class Calendar(
        val calendarIds: Set<Long> = emptySet(),
        val keywords: List<String> = emptyList(),
        val includeAllDay: Boolean = false,
        /** Skip events marked "free" (e.g. reminders blocked out on the calendar). */
        val busyOnly: Boolean = true,
        /** Switch this many minutes before the event starts. */
        val minutesBefore: Int = 0,
    ) : ThemeRule {
        override val type get() = RuleType.Calendar

        fun matches(event: CalendarEvent): Boolean =
            (calendarIds.isEmpty() || event.calendarId in calendarIds) &&
                (includeAllDay || !event.allDay) &&
                (!busyOnly || event.busy) &&
                (keywords.isEmpty() || keywords.any { event.title.contains(it, ignoreCase = true) })

        fun isActive(event: CalendarEvent, nowMillis: Long): Boolean =
            matches(event) && nowMillis >= event.begin - minutesBefore * 60_000L && nowMillis < event.end
    }

    companion object {
        val WORK_HOURS = Schedule(DayOfWeek.entries.take(5).toSet(), 9 * 60, 17 * 60)

        fun fromEntity(entity: ThemeRuleEntity): ThemeRule? = fromJson(entity.type, entity.config)

        fun toEntity(themeId: Long, rule: ThemeRule, enabled: Boolean = true): ThemeRuleEntity =
            ThemeRuleEntity(themeId = themeId, type = rule.type.name, config = configOf(rule).toString(), enabled = enabled)

        fun fromJson(type: String, config: String): ThemeRule? = runCatching {
            val json = JSONObject(config)
            when (RuleType.valueOf(type)) {
                RuleType.Schedule -> Schedule(
                    days = json.getJSONArray("days").let { a -> List(a.length()) { DayOfWeek.valueOf(a.getString(it)) }.toSet() },
                    startMinute = json.getInt("start"),
                    endMinute = json.getInt("end"),
                )
                RuleType.Bluetooth -> Bluetooth(json.getString("address"), json.optString("name"))
                RuleType.Controller -> Controller
                RuleType.Wifi -> Wifi(json.getString("ssid"))
                RuleType.Headphones -> Headphones
                RuleType.Charging -> Charging
                RuleType.Location -> Location(
                    name = json.optString("name"),
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lon"),
                    radiusMeters = json.optInt("radius", DEFAULT_RADIUS_M),
                )
                RuleType.Calendar -> Calendar(
                    calendarIds = json.optJSONArray("calendars")?.let { a -> List(a.length()) { a.getLong(it) }.toSet() }.orEmpty(),
                    keywords = json.optJSONArray("keywords")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
                    includeAllDay = json.optBoolean("allDay", false),
                    busyOnly = json.optBoolean("busyOnly", true),
                    minutesBefore = json.optInt("before", 0),
                )
            }
        }.getOrNull()

        fun configOf(rule: ThemeRule): JSONObject = when (rule) {
            is Schedule -> JSONObject()
                .put("days", JSONArray(rule.days.sorted().map { it.name }))
                .put("start", rule.startMinute)
                .put("end", rule.endMinute)
            is Bluetooth -> JSONObject().put("address", rule.address).put("name", rule.name)
            is Wifi -> JSONObject().put("ssid", rule.ssid)
            is Location -> JSONObject()
                .put("name", rule.name)
                .put("lat", rule.latitude)
                .put("lon", rule.longitude)
                .put("radius", rule.radiusMeters)
            is Calendar -> JSONObject()
                .put("calendars", JSONArray(rule.calendarIds.sorted()))
                .put("keywords", JSONArray(rule.keywords))
                .put("allDay", rule.includeAllDay)
                .put("busyOnly", rule.busyOnly)
                .put("before", rule.minutesBefore)
            Controller, Headphones, Charging -> JSONObject()
        }

        const val DEFAULT_RADIUS_M = 150
    }
}

/** A rule as configured on a theme; a disabled rule is kept but ignored. */
data class RuleSetting(val rule: ThemeRule, val enabled: Boolean = true)

/** An occurrence of a calendar event, for [ThemeRule.Calendar]. Times are ms since epoch. */
data class CalendarEvent(
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val busy: Boolean,
)

/** Which theme wins when several themes' conditions apply at once. */
enum class AutoPriority(val label: String) { Low("Low"), Normal("Normal"), High("High"), Highest("Highest") }

/** How a theme's conditions combine. */
enum class RuleMatch(val label: String) {
    Any("Any condition is met"),
    All("All conditions are met"),
}

/** What happens when the theme's conditions stop applying. */
enum class AutoEndAction(val label: String) {
    SwitchBack("Switch back to the previous theme"),
    Stay("Stay on this theme"),
}

/** How a theme switches automatically; see [ThemeRule]. */
data class ThemeAutomation(
    val priority: AutoPriority = AutoPriority.Normal,
    val match: RuleMatch = RuleMatch.Any,
    val onEnd: AutoEndAction = AutoEndAction.SwitchBack,
    /** Show a prompt on the home screen instead of switching. */
    val askFirst: Boolean = false,
    /** The conditions must hold this long before switching (e.g. to ride out a flaky Wi-Fi network). */
    val delayMinutes: Int = 0,
) {
    fun toJson(): String = JSONObject()
        .put("priority", priority.name)
        .put("match", match.name)
        .put("onEnd", onEnd.name)
        .put("askFirst", askFirst)
        .put("delay", delayMinutes)
        .toString()

    companion object {
        val DELAY_CHOICES = listOf(0, 1, 2, 5, 10, 15, 30)

        fun fromJson(json: String?): ThemeAutomation {
            if (json.isNullOrBlank()) return ThemeAutomation()
            return runCatching {
                val o = JSONObject(json)
                ThemeAutomation(
                    priority = AutoPriority.entries.firstOrNull { it.name == o.optString("priority") } ?: AutoPriority.Normal,
                    match = RuleMatch.entries.firstOrNull { it.name == o.optString("match") } ?: RuleMatch.Any,
                    onEnd = AutoEndAction.entries.firstOrNull { it.name == o.optString("onEnd") } ?: AutoEndAction.SwitchBack,
                    askFirst = o.optBoolean("askFirst", false),
                    delayMinutes = o.optInt("delay", 0).coerceAtLeast(0),
                )
            }.getOrDefault(ThemeAutomation())
        }
    }
}
