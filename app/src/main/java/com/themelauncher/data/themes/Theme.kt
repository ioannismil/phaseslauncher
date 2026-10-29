package com.themelauncher.data.themes

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

data class ThemePreset(val name: String, val icon: ThemeIconKey, val accentColor: Int)

object ThemeDefaults {
    const val MAX_FAVORITES = 5

    /** Accent choices offered in the theme editor; readable on both light and dark wallpapers. */
    val AccentColors: List<Int> = listOf(
        0xFF7C4DFF, 0xFF448AFF, 0xFF00B8D4, 0xFF00BFA5, 0xFF64DD17,
        0xFFFFAB00, 0xFFFF6D00, 0xFFFF1744, 0xFFF50057, 0xFF8D6E63,
    ).map { it.toInt() }

    val Presets: List<ThemePreset> = listOf(
        ThemePreset("Gaming", ThemeIconKey.Gaming, AccentColors[0]),
        ThemePreset("Work", ThemeIconKey.Work, AccentColors[1]),
        ThemePreset("Social", ThemeIconKey.Social, AccentColors[8]),
    )
}
