package com.phaseslauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A [com.phaseslauncher.data.themes.ThemeIconKey] name. */
    @ColumnInfo(name = "icon_key") val iconKey: String,
    /** ARGB. */
    @ColumnInfo(name = "accent_color") val accentColor: Int,
    /** Order in the theme switcher, ascending. */
    val position: Int,
    /** Image file (in app storage) applied as the wallpaper when this theme becomes active. */
    @ColumnInfo(name = "wallpaper_path") val wallpaperPath: String? = null,
    /** A [com.phaseslauncher.data.themes.SoundProfile] name. */
    @ColumnInfo(name = "sound_profile", defaultValue = "Unchanged") val soundProfile: String = "Unchanged",
    /** When true the theme shows its own dock instead of the shared one. */
    @ColumnInfo(name = "own_dock", defaultValue = "0") val ownDock: Boolean = false,
    /** Icon pack for this theme: null follows the launcher setting, "" means the apps' own icons. */
    @ColumnInfo(name = "icon_pack") val iconPack: String? = null,
    /** Home grid for this theme: null follows the launcher setting, "fit" fits the screen, else a column count. */
    @ColumnInfo(name = "grid_layout") val gridLayout: String? = null,
    /** [com.phaseslauncher.data.rules.ThemeAutomation] as JSON; empty for the defaults. */
    @ColumnInfo(defaultValue = "''") val automation: String = "",
    /** [com.phaseslauncher.data.focus.ThemeFocus] as JSON; empty for the defaults. */
    @ColumnInfo(defaultValue = "''") val focus: String = "",
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
    /** Reading order on the home grid (row by row), which the editor and sharing list them in. */
    val position: Int,
    /** The item's cell on the home grid; null until it's placed (it then takes the first free one). */
    @ColumnInfo(name = "cell_x") val cellX: Int? = null,
    @ColumnInfo(name = "cell_y") val cellY: Int? = null,
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
    /** Height before widgets had a size in cells; still decides [spanY] for widgets not yet placed. */
    @ColumnInfo(name = "height_dp") val heightDp: Int,
    /** Top-left cell and size in cells on the home grid; null until it's placed. */
    @ColumnInfo(name = "cell_x") val cellX: Int? = null,
    @ColumnInfo(name = "cell_y") val cellY: Int? = null,
    @ColumnInfo(name = "span_x") val spanX: Int? = null,
    @ColumnInfo(name = "span_y") val spanY: Int? = null,
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
    /** A [com.phaseslauncher.data.rules.RuleType] name. */
    val type: String,
    val config: String,
    val enabled: Boolean = true,
)

/** A folder on a theme's home grid. The theme lists it as [com.phaseslauncher.data.items.ItemKeys.folder]. */
@Entity(
    tableName = "folders",
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
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "theme_id") val themeId: Long,
    val name: String,
)

/** An app (or pinned shortcut) inside a folder. */
@Entity(
    tableName = "folder_items",
    primaryKeys = ["folder_id", "item_key"],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_key")],
)
data class FolderItemEntity(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "item_key") val itemKey: String,
    val position: Int,
)

/**
 * A shortcut another app pinned with "Add to home screen" (a Chrome page, a contact, a route).
 * Themes and folders list it as [com.phaseslauncher.data.items.ItemKeys.shortcut].
 */
@Entity(
    tableName = "pinned_shortcuts",
    indices = [Index(value = ["package_name", "shortcut_id", "user_serial"], unique = true)],
)
data class PinnedShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String,
    /** [android.os.UserManager.getSerialNumberForUser] of the profile the shortcut belongs to. */
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    /** The label at pin time; shown when the system can't give us the shortcut (e.g. not the default home). */
    val label: String,
)

/** An app opened from the launcher, and the theme that was on; feeds suggestions. Kept for a few weeks. */
@Entity(tableName = "launch_events", indices = [Index("time")])
data class LaunchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "theme_id") val themeId: Long?,
    /** ms since epoch. */
    val time: Long,
)

/** The user switched to a theme by hand; feeds schedule suggestions. Kept for a few weeks. */
@Entity(tableName = "theme_switches", indices = [Index("time")])
data class ThemeSwitchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "theme_id") val themeId: Long,
    /** ms since epoch. */
    val time: Long,
)
