package com.themelauncher.data.rules

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Short descriptions of rules, for the editor and the home screen's prompts. */
object RuleText {

    fun title(type: RuleType): String = when (type) {
        RuleType.Schedule -> "On a schedule"
        RuleType.Bluetooth -> "When a Bluetooth device connects"
        RuleType.Controller -> "When a game controller connects"
        RuleType.Headphones -> "When headphones are connected"
        RuleType.Wifi -> "On a Wi-Fi network"
        RuleType.Charging -> "While charging"
        RuleType.Location -> "At a place"
        RuleType.Calendar -> "During calendar events"
    }

    fun hint(type: RuleType): String = when (type) {
        RuleType.Schedule -> "e.g. work hours"
        RuleType.Bluetooth -> "e.g. your car or headphones"
        RuleType.Controller -> "Any Bluetooth or USB gamepad"
        RuleType.Headphones -> "Wired, USB or Bluetooth"
        RuleType.Wifi -> "e.g. home or the office"
        RuleType.Charging -> "e.g. a bedside or desk theme"
        RuleType.Location -> "e.g. the gym or a parent's house"
        RuleType.Calendar -> "e.g. meetings, or events with “focus” in the title"
    }

    fun describe(rule: ThemeRule): String = when (rule) {
        is ThemeRule.Schedule -> "${days(rule.days)}, ${minute(rule.startMinute)}–${minute(rule.endMinute)}"
        is ThemeRule.Bluetooth -> rule.name.ifBlank { rule.address }
        ThemeRule.Controller -> "Any game controller"
        ThemeRule.Headphones -> "Headphones"
        is ThemeRule.Wifi -> rule.ssid
        ThemeRule.Charging -> "Charging"
        is ThemeRule.Location -> "${rule.name.ifBlank { "A place" }} (within ${distance(rule.radiusMeters)})"
        is ThemeRule.Calendar -> buildList {
            add(if (rule.keywords.isEmpty()) "Any event" else "Events with “${rule.keywords.joinToString("” or “")}”")
            add(if (rule.calendarIds.isEmpty()) "all calendars" else if (rule.calendarIds.size == 1) "1 calendar" else "${rule.calendarIds.size} calendars")
            if (rule.minutesBefore > 0) add("${rule.minutesBefore} min early")
            if (rule.includeAllDay) add("all-day too")
            if (!rule.busyOnly) add("free time too")
        }.joinToString(", ")
    }

    /** "Work hours started", for the prompt asking whether to switch. */
    fun reason(rule: ThemeRule): String = when (rule) {
        is ThemeRule.Schedule -> "It's ${minute(rule.startMinute)}–${minute(rule.endMinute)}"
        is ThemeRule.Bluetooth -> "${rule.name.ifBlank { "A Bluetooth device" }} connected"
        ThemeRule.Controller -> "A game controller connected"
        ThemeRule.Headphones -> "Headphones connected"
        is ThemeRule.Wifi -> "Connected to ${rule.ssid}"
        ThemeRule.Charging -> "Charging"
        is ThemeRule.Location -> "You're at ${rule.name.ifBlank { "a saved place" }}"
        is ThemeRule.Calendar -> "A calendar event is on"
    }

    fun minute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    fun days(days: Set<DayOfWeek>): String = when (days) {
        DayOfWeek.entries.toSet() -> "Every day"
        DayOfWeek.entries.take(5).toSet() -> "Weekdays"
        setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    fun distance(meters: Int) = if (meters < 1000) "$meters m" else "%.1f km".format(meters / 1000.0)
}
