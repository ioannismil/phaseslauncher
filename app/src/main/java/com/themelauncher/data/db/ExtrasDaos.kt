package com.themelauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDao {
    @Query("SELECT * FROM theme_widgets ORDER BY theme_id, position")
    fun observeAll(): Flow<List<ThemeWidgetEntity>>

    @Query("SELECT * FROM theme_widgets")
    suspend fun getAll(): List<ThemeWidgetEntity>

    @Query("SELECT * FROM theme_widgets WHERE theme_id = :themeId")
    suspend fun getForTheme(themeId: Long): List<ThemeWidgetEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM theme_widgets WHERE theme_id = :themeId")
    suspend fun maxPosition(themeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(widget: ThemeWidgetEntity)

    @Query("UPDATE theme_widgets SET height_dp = :heightDp WHERE app_widget_id = :appWidgetId")
    suspend fun setHeight(appWidgetId: Int, heightDp: Int)

    @Query("DELETE FROM theme_widgets WHERE app_widget_id = :appWidgetId")
    suspend fun delete(appWidgetId: Int)

    @Query("UPDATE theme_widgets SET position = :position WHERE app_widget_id = :appWidgetId")
    suspend fun setPosition(appWidgetId: Int, position: Int)

    @Transaction
    suspend fun reorder(orderedIds: List<Int>) = orderedIds.forEachIndexed { i, id -> setPosition(id, i) }
}

@Dao
interface CustomizationDao {
    @Query("SELECT * FROM app_customizations")
    fun observeAll(): Flow<List<AppCustomizationEntity>>

    @Query("SELECT * FROM app_customizations")
    suspend fun getAll(): List<AppCustomizationEntity>

    @Upsert
    suspend fun upsert(customization: AppCustomizationEntity)

    @Query("DELETE FROM app_customizations WHERE app_key = :appKey")
    suspend fun delete(appKey: String)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM app_usage")
    fun observeAll(): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE app_key = :appKey")
    suspend fun get(appKey: String): AppUsageEntity?

    @Upsert
    suspend fun upsert(usage: AppUsageEntity)

    @Query("DELETE FROM app_usage WHERE app_key IN (:appKeys)")
    suspend fun remove(appKeys: List<String>)

    @Transaction
    suspend fun recordLaunch(appKey: String, now: Long) {
        val current = get(appKey)
        upsert(AppUsageEntity(appKey, (current?.launchCount ?: 0) + 1, now))
    }
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM theme_rules ORDER BY theme_id, id")
    fun observeAll(): Flow<List<ThemeRuleEntity>>

    @Query("SELECT * FROM theme_rules WHERE theme_id = :themeId ORDER BY id")
    suspend fun getForThemeOrdered(themeId: Long): List<ThemeRuleEntity>

    @Query("SELECT * FROM theme_rules WHERE theme_id = :themeId")
    suspend fun getForTheme(themeId: Long): List<ThemeRuleEntity>

    @Query("DELETE FROM theme_rules WHERE theme_id = :themeId")
    suspend fun clear(themeId: Long)

    @Insert
    suspend fun insertAll(rules: List<ThemeRuleEntity>)

    @Transaction
    suspend fun setRules(themeId: Long, rules: List<ThemeRuleEntity>) {
        clear(themeId)
        insertAll(rules.map { it.copy(id = 0, themeId = themeId) })
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertLaunch(event: LaunchEventEntity)

    @Insert
    suspend fun insertSwitch(event: ThemeSwitchEntity)

    @Query("SELECT * FROM launch_events WHERE time >= :since ORDER BY time")
    fun observeLaunches(since: Long): Flow<List<LaunchEventEntity>>

    @Query("SELECT * FROM theme_switches WHERE time >= :since ORDER BY time")
    fun observeSwitches(since: Long): Flow<List<ThemeSwitchEntity>>

    @Query("DELETE FROM launch_events WHERE time < :before")
    suspend fun pruneLaunches(before: Long)

    @Query("DELETE FROM theme_switches WHERE time < :before")
    suspend fun pruneSwitches(before: Long)

    @Query("DELETE FROM launch_events")
    suspend fun clearLaunches()

    @Query("DELETE FROM theme_switches")
    suspend fun clearSwitches()
}
