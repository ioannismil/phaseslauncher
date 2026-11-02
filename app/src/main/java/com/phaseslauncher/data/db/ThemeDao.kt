package com.phaseslauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {

    @Query("SELECT * FROM themes ORDER BY position, id")
    fun observeThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM theme_apps ORDER BY theme_id, position")
    fun observeAssignments(): Flow<List<ThemeAppEntity>>

    @Query("SELECT * FROM themes ORDER BY position, id")
    suspend fun getThemes(): List<ThemeEntity>

    @Query("SELECT COUNT(*) FROM themes")
    suspend fun countThemes(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM themes")
    suspend fun maxThemePosition(): Int

    @Insert
    suspend fun insertTheme(theme: ThemeEntity): Long

    @Update
    suspend fun updateTheme(theme: ThemeEntity)

    @Query("DELETE FROM themes WHERE id = :themeId")
    suspend fun deleteTheme(themeId: Long)

    /** Removes every theme (and, by cascade, every assignment). Used when restoring a backup. */
    @Query("DELETE FROM themes")
    suspend fun deleteAllThemes()

    @Query("UPDATE themes SET position = :position WHERE id = :themeId")
    suspend fun setThemePosition(themeId: Long, position: Int)

    @Transaction
    suspend fun reorderThemes(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setThemePosition(id, index) }
    }

    @Query("SELECT app_key FROM theme_apps WHERE theme_id = :themeId ORDER BY position")
    suspend fun getThemeAppKeys(themeId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM theme_apps WHERE theme_id = :themeId")
    suspend fun maxAppPosition(themeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignments(assignments: List<ThemeAppEntity>)

    @Query("DELETE FROM theme_apps WHERE theme_id = :themeId AND app_key = :appKey")
    suspend fun removeApp(themeId: Long, appKey: String)

    @Query("DELETE FROM theme_apps WHERE theme_id = :themeId")
    suspend fun clearApps(themeId: Long)

    @Query("DELETE FROM theme_apps WHERE app_key IN (:appKeys)")
    suspend fun removeAppsEverywhere(appKeys: List<String>)

    @Query("SELECT DISTINCT app_key FROM theme_apps")
    suspend fun getAllAssignedKeys(): List<String>

    /** Appends [appKey] to the end of the theme; no-op if it is already there. */
    @Transaction
    suspend fun addApp(themeId: Long, appKey: String) {
        insertAssignments(listOf(ThemeAppEntity(themeId, appKey, maxAppPosition(themeId) + 1)))
    }

    /** Replaces the theme's apps with [orderedKeys], in that order. */
    @Transaction
    suspend fun setApps(themeId: Long, orderedKeys: List<String>) {
        clearApps(themeId)
        insertAssignments(orderedKeys.distinct().mapIndexed { i, key -> ThemeAppEntity(themeId, key, i) })
    }

    @Transaction
    suspend fun insertThemeWithApps(theme: ThemeEntity, appKeys: List<String>): Long {
        val id = insertTheme(theme.copy(position = maxThemePosition() + 1))
        setApps(id, appKeys)
        return id
    }
}
