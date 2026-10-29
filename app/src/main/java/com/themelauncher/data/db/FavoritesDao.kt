package com.themelauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT app_key FROM favorites ORDER BY position")
    fun observeFavoriteKeys(): Flow<List<String>>

    @Query("SELECT app_key FROM favorites ORDER BY position")
    suspend fun getFavoriteKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE app_key IN (:appKeys)")
    suspend fun remove(appKeys: List<String>)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    /** Replaces all favorites with [orderedKeys], in that order. */
    @Transaction
    suspend fun setFavorites(orderedKeys: List<String>) {
        clear()
        insertAll(orderedKeys.distinct().mapIndexed { i, key -> FavoriteEntity(key, i) })
    }
}
