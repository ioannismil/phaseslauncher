package com.themelauncher.data.db

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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: per-theme wallpaper/sound/dock, widgets, app customizations, usage, rules.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun widgetDao(): WidgetDao
    abstract fun customizationDao(): CustomizationDao
    abstract fun usageDao(): UsageDao
    abstract fun ruleDao(): RuleDao

    companion object {
        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db").build()
    }
}
