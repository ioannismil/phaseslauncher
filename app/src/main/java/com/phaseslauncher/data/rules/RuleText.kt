package com.phaseslauncher.data.rules

import android.content.res.Resources
import com.phaseslauncher.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Short descriptions of rules, for the editor and the home screen's prompts. */
object RuleText {

    fun title(res: Resources, type: RuleType): String = when (type) {
        RuleType.Schedule -> res.getString(R.string.on_a_schedule)
        RuleType.Bluetooth -> res.getString(R.string.when_a_bluetooth_device_connects)
        RuleType.Controller -> res.getString(R.string.when_a_game_controller_connects)
        RuleType.Headphones -> res.getString(R.string.when_headphones_are_connected)
        RuleType.Wifi -> res.getString(R.string.on_a_wi_fi_network)
        RuleType.Charging -> res.getString(R.string.while_charging)
        RuleType.Location -> res.getString(R.string.at_a_place)
        RuleType.Calendar -> res.getString(R.string.during_calendar_events)
    }

    fun hint(res: Resources, type: RuleType): String = when (type) {
        RuleType.Schedule -> res.getString(R.string.e_g_work_hours)
        RuleType.Bluetooth -> res.getString(R.string.e_g_your_car_or_headphones)
        RuleType.Controller -> res.getString(R.string.any_bluetooth_or_usb_gamepad)
        RuleType.Headphones -> res.getString(R.string.wired_usb_or_bluetooth)
        RuleType.Wifi -> res.getString(R.string.e_g_home_or_the_office)
        RuleType.Charging -> res.getString(R.string.e_g_a_bedside_or_desk_theme)
        RuleType.Location -> res.getString(R.string.e_g_the_gym_or_a_parent_s_house)
        RuleType.Calendar -> res.getString(R.string.e_g_meetings_or_events_with_focus_in_the_title)
    }

    fun describe(res: Resources, rule: ThemeRule): String = when (rule) {
        is ThemeRule.Schedule -> res.getString(
            R.string.schedule_description,
            days(res, rule.days),
            minute(rule.startMinute),
            minute(rule.endMinute),
        )

        is ThemeRule.Bluetooth -> rule.name.ifBlank { rule.address }

        ThemeRule.Controller -> res.getString(R.string.any_game_controller)

        ThemeRule.Headphones -> res.getString(R.string.headphones)

        is ThemeRule.Wifi -> rule.ssid

        ThemeRule.Charging -> res.getString(R.string.charging)

        is ThemeRule.Location -> res.getString(
            R.string.within_2,
            rule.name.ifBlank { res.getString(R.string.a_place) },
            distance(res, rule.radiusMeters),
        )

        is ThemeRule.Calendar -> buildList {
            add(
                if (rule.keywords.isEmpty()) {
                    res.getString(R.string.any_event)
                } else {
                    res.getString(R.string.events_with, rule.keywords.joinToString(res.getString(R.string.or)))
                },
            )
            add(
                if (rule.calendarIds.isEmpty()) {
                    res.getString(R.string.all_calendars_lower)
                } else {
                    res.getQuantityString(R.plurals.calendars_count, rule.calendarIds.size, rule.calendarIds.size)
                },
            )
            if (rule.minutesBefore > 0) add(res.getString(R.string.min_early, rule.minutesBefore))
            if (rule.includeAllDay) add(res.getString(R.string.all_day_too))
            if (!rule.busyOnly) add(res.getString(R.string.free_time_too))
        }.joinToString(", ")
    }

    /** "Work hours started", for the prompt asking whether to switch. */
    fun reason(res: Resources, rule: ThemeRule): String = when (rule) {
        is ThemeRule.Schedule -> res.getString(R.string.it_s, minute(rule.startMinute), minute(rule.endMinute))
        is ThemeRule.Bluetooth -> res.getString(R.string.bluetooth_connected, rule.name.ifBlank { res.getString(R.string.a_bluetooth_device) })
        ThemeRule.Controller -> res.getString(R.string.a_game_controller_connected)
        ThemeRule.Headphones -> res.getString(R.string.headphones_connected)
        is ThemeRule.Wifi -> res.getString(R.string.connected_to_2, rule.ssid)
        ThemeRule.Charging -> res.getString(R.string.charging)
        is ThemeRule.Location -> res.getString(R.string.you_re_at, rule.name.ifBlank { res.getString(R.string.a_saved_place) })
        is ThemeRule.Calendar -> res.getString(R.string.a_calendar_event_is_on)
    }

    fun minute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    fun days(res: Resources, days: Set<DayOfWeek>): String = when (days) {
        DayOfWeek.entries.toSet() -> res.getString(R.string.every_day)
        DayOfWeek.entries.take(5).toSet() -> res.getString(R.string.weekdays)
        setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> res.getString(R.string.weekends)
        else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    fun distance(res: Resources, meters: Int): String =
        if (meters < 1000) res.getString(R.string.distance_m, meters) else res.getString(R.string.distance_km, meters / 1000.0)
}
