package com.phaseslauncher.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ThemeEntity::class,
        ThemeAppEntity::class,
        FavoriteEntity::class,
        ThemeWidgetEntity::class,
        AppCustomizationEntity::class,
        AppUsageEntity::class,
        ThemeRuleEntity::class,
        FolderEntity::class,
        FolderItemEntity::class,
        PinnedShortcutEntity::class,
        LaunchEventEntity::class,
        ThemeSwitchEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: per-theme wallpaper/sound/dock, widgets, app customizations, usage, rules.
        AutoMigration(from = 1, to = 2),
        // v3: folders and pinned shortcuts.
        AutoMigration(from = 2, to = 3),
        // v4: per-theme icon pack and home grid.
        AutoMigration(from = 3, to = 4),
        // v5: per-theme automation and focus settings, launch and switch history for suggestions.
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun widgetDao(): WidgetDao
    abstract fun customizationDao(): CustomizationDao
    abstract fun usageDao(): UsageDao
    abstract fun ruleDao(): RuleDao
    abstract fun folderDao(): FolderDao
    abstract fun pinnedShortcutDao(): PinnedShortcutDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db").build()
    }
}
