package com.themelauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ThemeEntity::class, ThemeAppEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun favoritesDao(): FavoritesDao

    companion object {
        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db").build()
    }
}
