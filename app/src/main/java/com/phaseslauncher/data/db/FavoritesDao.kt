package com.phaseslauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    /** Every dock's slots: the shared dock (id 0) and any theme's own dock. */
    @Query("SELECT * FROM favorites ORDER BY dock_id, position")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT app_key FROM favorites WHERE dock_id = :dockId ORDER BY position")
    suspend fun getFavoriteKeys(dockId: Long = FavoriteEntity.SHARED_DOCK): List<String>

    @Query("SELECT DISTINCT app_key FROM favorites")
    suspend fun getAllKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE dock_id = :dockId AND app_key IN (:appKeys)")
    suspend fun remove(dockId: Long, appKeys: List<String>)

    @Query("DELETE FROM favorites WHERE app_key IN (:appKeys)")
    suspend fun removeEverywhere(appKeys: List<String>)

    @Query("DELETE FROM favorites WHERE dock_id = :dockId")
    suspend fun clear(dockId: Long)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    /** Replaces a dock's favorites with [orderedKeys], in that order. */
    @Transaction
    suspend fun setFavorites(orderedKeys: List<String>, dockId: Long = FavoriteEntity.SHARED_DOCK) {
        clear(dockId)
        insertAll(orderedKeys.distinct().mapIndexed { i, key -> FavoriteEntity(key, i, dockId) })
    }
}
