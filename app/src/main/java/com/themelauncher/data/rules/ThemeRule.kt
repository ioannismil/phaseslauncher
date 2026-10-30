package com.themelauncher.data.rules

import com.themelauncher.data.db.ThemeRuleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime

enum class RuleType { Schedule, Bluetooth, Controller }

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
    }

    /** Active while this Bluetooth device (e.g. the car) is connected. */
    data class Bluetooth(val address: String, val name: String) : ThemeRule {
        override val type get() = RuleType.Bluetooth
    }

    /** Active while any game controller is connected. */
    data object Controller : ThemeRule {
        override val type get() = RuleType.Controller
    }

    companion object {
        val WORK_HOURS = Schedule(DayOfWeek.entries.take(5).toSet(), 9 * 60, 17 * 60)

        fun fromEntity(entity: ThemeRuleEntity): ThemeRule? = runCatching {
            val json = JSONObject(entity.config)
            when (RuleType.valueOf(entity.type)) {
                RuleType.Schedule -> Schedule(
                    days = json.getJSONArray("days").let { a -> List(a.length()) { DayOfWeek.valueOf(a.getString(it)) }.toSet() },
                    startMinute = json.getInt("start"),
                    endMinute = json.getInt("end"),
                )
                RuleType.Bluetooth -> Bluetooth(json.getString("address"), json.optString("name"))
                RuleType.Controller -> Controller
            }
        }.getOrNull()

        fun toEntity(themeId: Long, rule: ThemeRule): ThemeRuleEntity {
            val config = when (rule) {
                is Schedule -> JSONObject()
                    .put("days", JSONArray(rule.days.sorted().map { it.name }))
                    .put("start", rule.startMinute)
                    .put("end", rule.endMinute)
                is Bluetooth -> JSONObject().put("address", rule.address).put("name", rule.name)
                Controller -> JSONObject()
            }
            return ThemeRuleEntity(themeId = themeId, type = rule.type.name, config = config.toString())
        }
    }
}
