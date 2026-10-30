package com.themelauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A [com.themelauncher.data.themes.ThemeIconKey] name. */
    @ColumnInfo(name = "icon_key") val iconKey: String,
    /** ARGB. */
    @ColumnInfo(name = "accent_color") val accentColor: Int,
    /** Order in the theme switcher, ascending. */
    val position: Int,
    /** Image file (in app storage) applied as the wallpaper when this theme becomes active. */
    @ColumnInfo(name = "wallpaper_path") val wallpaperPath: String? = null,
    /** A [com.themelauncher.data.themes.SoundProfile] name. */
    @ColumnInfo(name = "sound_profile", defaultValue = "Unchanged") val soundProfile: String = "Unchanged",
    /** When true the theme shows its own dock instead of the shared one. */
    @ColumnInfo(name = "own_dock", defaultValue = "0") val ownDock: Boolean = false,
)

/** An app assigned to a theme. An app may be in several themes. */
@Entity(
    tableName = "theme_apps",
    primaryKeys = ["theme_id", "app_key"],
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["theme_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("app_key")],
)
data class ThemeAppEntity(
    @ColumnInfo(name = "theme_id") val themeId: Long,
    @ColumnInfo(name = "app_key") val appKey: String,
    val position: Int,
)

/** A dock slot. [dockId] is [SHARED_DOCK] for the dock shared by all themes, else a theme id. */
@Entity(tableName = "favorites", primaryKeys = ["dock_id", "app_key"])
data class FavoriteEntity(
    @ColumnInfo(name = "app_key") val appKey: String,
    val position: Int,
    @ColumnInfo(name = "dock_id", defaultValue = "0") val dockId: Long = SHARED_DOCK,
) {
    companion object {
        const val SHARED_DOCK = 0L
    }
}

/** A home-screen widget on a theme. The id is the system's appWidgetId. */
@Entity(
    tableName = "theme_widgets",
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["theme_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("theme_id")],
)
data class ThemeWidgetEntity(
    @PrimaryKey @ColumnInfo(name = "app_widget_id") val appWidgetId: Int,
    @ColumnInfo(name = "theme_id") val themeId: Long,
    /** Flattened provider ComponentName, kept to detect widgets whose app was uninstalled. */
    val provider: String,
    val position: Int,
    @ColumnInfo(name = "height_dp") val heightDp: Int,
)

/** A user's custom name and/or icon for one app. */
@Entity(tableName = "app_customizations")
data class AppCustomizationEntity(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    val label: String? = null,
    /** Icon pack package and drawable name for a hand-picked icon. */
    @ColumnInfo(name = "icon_pack") val iconPack: String? = null,
    @ColumnInfo(name = "icon_name") val iconName: String? = null,
)

/** How often and how recently each app was opened from the launcher; feeds suggestions. */
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "last_launched") val lastLaunched: Long,
)

/** Switches to a theme automatically. [config] is rule-type specific JSON. */
@Entity(
    tableName = "theme_rules",
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["theme_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("theme_id")],
)
data class ThemeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "theme_id") val themeId: Long,
    /** A [com.themelauncher.data.rules.RuleType] name. */
    val type: String,
    val config: String,
    val enabled: Boolean = true,
)
