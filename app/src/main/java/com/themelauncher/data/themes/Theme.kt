package com.themelauncher.data.themes

import android.content.pm.ApplicationInfo
import com.themelauncher.data.apps.AppInfo

/** Icons a theme can use. Stored by name; the UI maps each to a Material icon. */
enum class ThemeIconKey {
    Gaming, Work, Social, Music, Home, Fitness, Travel, Study,
    Shopping, Photo, Code, Movie, Food, Night, Star, Heart;

    companion object {
        fun fromName(name: String): ThemeIconKey = entries.firstOrNull { it.name == name } ?: Star
    }
}

data class Theme(
    val id: Long,
    val name: String,
    val icon: ThemeIconKey,
    /** ARGB. */
    val accentColor: Int,
    /** Assigned app keys in display order. May include apps that are currently unavailable. */
    val appKeys: List<String>,
)

/** A theme that hasn't been saved yet (editor and onboarding). */
data class ThemeDraft(
    val name: String,
    val icon: ThemeIconKey,
    val accentColor: Int,
    /** In display order. */
    val appKeys: List<String> = emptyList(),
)

data class ThemePreset(
    val name: String,
    val icon: ThemeIconKey,
    val accentColor: Int,
    /** Play Store categories ([ApplicationInfo.category]) whose apps are suggested for this theme. */
    val categories: Set<Int> = emptySet(),
    /** Well-known apps that often don't declare a category. */
    val packages: Set<String> = emptySet(),
) {
    fun suggest(apps: List<AppInfo>): List<AppInfo> =
        apps.filter { it.category in categories || it.packageName in packages }
}

object ThemeDefaults {
    const val MAX_FAVORITES = 5

    /** Accent choices offered in the theme editor; readable on both light and dark wallpapers. */
    val AccentColors: List<Int> = listOf(
        0xFF7C4DFF, 0xFF448AFF, 0xFF00B8D4, 0xFF00BFA5, 0xFF64DD17,
        0xFFFFAB00, 0xFFFF6D00, 0xFFFF1744, 0xFFF50057, 0xFF8D6E63,
    ).map { it.toInt() }

    val Presets: List<ThemePreset> = listOf(
        ThemePreset(
            name = "Gaming",
            icon = ThemeIconKey.Gaming,
            accentColor = AccentColors[0],
            categories = setOf(ApplicationInfo.CATEGORY_GAME),
            packages = setOf(
                "com.google.android.play.games",
                "com.samsung.android.game.gamehome",
                "com.discord",
                "com.valvesoftware.android.steam.community",
                "tv.twitch.android.app",
            ),
        ),
        ThemePreset(
            name = "Work",
            icon = ThemeIconKey.Work,
            accentColor = AccentColors[1],
            categories = setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY),
            packages = setOf(
                "com.google.android.gm",
                "com.google.android.calendar",
                "com.samsung.android.calendar",
                "com.google.android.apps.docs",
                "com.google.android.apps.docs.editors.docs",
                "com.google.android.apps.docs.editors.sheets",
                "com.google.android.apps.docs.editors.slides",
                "com.microsoft.office.outlook",
                "com.microsoft.teams",
                "com.microsoft.office.officehubrow",
                "com.Slack",
                "us.zoom.videomeetings",
                "notion.id",
                "com.linkedin.android",
            ),
        ),
        ThemePreset(
            name = "Social",
            icon = ThemeIconKey.Social,
            accentColor = AccentColors[8],
            categories = setOf(ApplicationInfo.CATEGORY_SOCIAL),
            packages = setOf(
                "com.whatsapp",
                "com.instagram.android",
                "com.facebook.katana",
                "com.facebook.orca",
                "org.telegram.messenger",
                "org.thoughtcrime.securesms",
                "com.twitter.android",
                "com.snapchat.android",
                "com.reddit.frontpage",
                "com.zhiliaoapp.musically",
                "com.viber.voip",
                "com.bereal.ft",
            ),
        ),
    )
}
