package com.themelauncher.data.focus

import org.json.JSONArray
import org.json.JSONObject

/** What opening a blocked app (or one over its time limit) does. */
enum class BlockMode(val label: String) {
    Pause("Show a pause screen first"),
    Block("Don't open it"),
}

/** Which apps may show notification dots, and (optionally) notifications, while a theme is on. */
enum class NotificationFilter(val label: String) {
    Off("All apps"),
    ThemeApps("Only apps in this theme and the dock"),
    Chosen("Only apps I choose"),
}

/** When a theme's daily limits count. */
enum class LimitScope(val label: String) {
    WhileActive("Only while this theme is on"),
    Always("All day, whichever theme is on"),
}

/**
 * A theme's focus settings. They apply while the theme is on (limits can apply all day, see
 * [limitScope]). The launcher can only stop apps opened from it, not from notifications or other apps.
 */
data class ThemeFocus(
    /** App keys that can't be opened (see [blockMode]) while the theme is on. */
    val blockedApps: Set<String> = emptySet(),
    /** Also block every app that isn't in this theme or the dock. */
    val blockOtherApps: Boolean = false,
    val blockMode: BlockMode = BlockMode.Pause,
    /** How long the pause screen makes you wait before "Open anyway" works. */
    val pauseSeconds: Int = 5,
    /** After waiting it out, the app opens freely for this long. */
    val allowMinutes: Int = 5,
    /** Leave blocked apps out of the drawer while the theme is on. */
    val hideBlockedApps: Boolean = false,
    val notificationFilter: NotificationFilter = NotificationFilter.Off,
    /** For [NotificationFilter.Chosen], and extra apps allowed with [NotificationFilter.ThemeApps]. */
    val allowedNotificationApps: Set<String> = emptySet(),
    /** Also keep filtered notifications out of the notification shade (they return when the theme ends). */
    val hideNotifications: Boolean = false,
    /** Calls, alarms and reminders always get through. */
    val allowUrgent: Boolean = true,
    /** Daily minutes per app key. */
    val appLimits: Map<String, Int> = emptyMap(),
    /** Daily minutes for all of this theme's apps together; 0 means no limit. */
    val themeLimitMinutes: Int = 0,
    val limitScope: LimitScope = LimitScope.WhileActive,
    val limitMode: BlockMode = BlockMode.Pause,
) {
    val blocksAnything: Boolean get() = blockedApps.isNotEmpty() || blockOtherApps
    val hasLimits: Boolean get() = appLimits.isNotEmpty() || themeLimitMinutes > 0
    val isDefault: Boolean get() = this == ThemeFocus()

    /** True if [appKey] is blocked while this theme is on. [themeKeys] are the theme's and the dock's app keys. */
    fun blocks(appKey: String, themeKeys: Set<String>): Boolean =
        appKey in blockedApps || (blockOtherApps && appKey !in themeKeys)

    fun toJson(): String = JSONObject()
        .put("blocked", JSONArray(blockedApps.sorted()))
        .put("blockOthers", blockOtherApps)
        .put("blockMode", blockMode.name)
        .put("pauseSeconds", pauseSeconds)
        .put("allowMinutes", allowMinutes)
        .put("hideBlocked", hideBlockedApps)
        .put("notifications", notificationFilter.name)
        .put("allowedNotifications", JSONArray(allowedNotificationApps.sorted()))
        .put("hideNotifications", hideNotifications)
        .put("allowUrgent", allowUrgent)
        .put("limits", JSONObject().apply { appLimits.forEach { (key, minutes) -> put(key, minutes) } })
        .put("themeLimit", themeLimitMinutes)
        .put("limitScope", limitScope.name)
        .put("limitMode", limitMode.name)
        .toString()

    /** The same settings with app keys mapped (e.g. when importing a shared theme). */
    fun mapKeys(transform: (String) -> String?): ThemeFocus = copy(
        blockedApps = blockedApps.mapNotNullTo(HashSet(), transform),
        allowedNotificationApps = allowedNotificationApps.mapNotNullTo(HashSet(), transform),
        appLimits = appLimits.mapNotNull { (k, v) -> transform(k)?.let { it to v } }.toMap(),
    )

    companion object {
        val PAUSE_CHOICES = listOf(0, 3, 5, 10, 15, 30, 60)
        val ALLOW_CHOICES = listOf(1, 2, 5, 10, 15, 30, 60)
        val LIMIT_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

        fun fromJson(json: String?): ThemeFocus {
            if (json.isNullOrBlank()) return ThemeFocus()
            return runCatching {
                val o = JSONObject(json)
                ThemeFocus(
                    blockedApps = o.optJSONArray("blocked").strings().toSet(),
                    blockOtherApps = o.optBoolean("blockOthers", false),
                    blockMode = BlockMode.entries.firstOrNull { it.name == o.optString("blockMode") } ?: BlockMode.Pause,
                    pauseSeconds = o.optInt("pauseSeconds", 5).coerceIn(0, 300),
                    allowMinutes = o.optInt("allowMinutes", 5).coerceIn(1, 240),
                    hideBlockedApps = o.optBoolean("hideBlocked", false),
                    notificationFilter = NotificationFilter.entries.firstOrNull { it.name == o.optString("notifications") }
                        ?: NotificationFilter.Off,
                    allowedNotificationApps = o.optJSONArray("allowedNotifications").strings().toSet(),
                    hideNotifications = o.optBoolean("hideNotifications", false),
                    allowUrgent = o.optBoolean("allowUrgent", true),
                    appLimits = o.optJSONObject("limits")?.let { limits ->
                        limits.keys().asSequence().associateWith { limits.optInt(it) }.filterValues { it > 0 }
                    }.orEmpty(),
                    themeLimitMinutes = o.optInt("themeLimit", 0).coerceAtLeast(0),
                    limitScope = LimitScope.entries.firstOrNull { it.name == o.optString("limitScope") } ?: LimitScope.WhileActive,
                    limitMode = BlockMode.entries.firstOrNull { it.name == o.optString("limitMode") } ?: BlockMode.Pause,
                )
            }.getOrDefault(ThemeFocus())
        }

        private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
    }
}
