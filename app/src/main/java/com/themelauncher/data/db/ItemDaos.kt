package com.themelauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folder_items ORDER BY folder_id, position")
    fun observeItems(): Flow<List<FolderItemEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun get(folderId: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE theme_id = :themeId")
    suspend fun getForTheme(themeId: Long): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :folderId")
    suspend fun rename(folderId: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun delete(folderId: Long)

    @Query("SELECT item_key FROM folder_items WHERE folder_id = :folderId ORDER BY position")
    suspend fun getItemKeys(folderId: Long): List<String>

    @Query("SELECT DISTINCT item_key FROM folder_items")
    suspend fun getAllItemKeys(): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM folder_items WHERE folder_id = :folderId")
    suspend fun maxPosition(folderId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<FolderItemEntity>)

    @Query("DELETE FROM folder_items WHERE folder_id = :folderId AND item_key = :itemKey")
    suspend fun removeItem(folderId: Long, itemKey: String)

    @Query("DELETE FROM folder_items WHERE item_key IN (:itemKeys)")
    suspend fun removeItemsEverywhere(itemKeys: List<String>)

    @Query("DELETE FROM folder_items WHERE folder_id = :folderId")
    suspend fun clearItems(folderId: Long)

    /** Appends [itemKey] to the end of the folder; no-op if it is already there. */
    @Transaction
    suspend fun addItem(folderId: Long, itemKey: String) {
        insertItems(listOf(FolderItemEntity(folderId, itemKey, maxPosition(folderId) + 1)))
    }

    @Transaction
    suspend fun setItems(folderId: Long, orderedKeys: List<String>) {
        clearItems(folderId)
        insertItems(orderedKeys.distinct().mapIndexed { i, key -> FolderItemEntity(folderId, key, i) })
    }
}

@Dao
interface PinnedShortcutDao {
    @Query("SELECT * FROM pinned_shortcuts")
    fun observeAll(): Flow<List<PinnedShortcutEntity>>

    @Query("SELECT * FROM pinned_shortcuts")
    suspend fun getAll(): List<PinnedShortcutEntity>

    @Query("SELECT * FROM pinned_shortcuts WHERE package_name = :packageName AND shortcut_id = :shortcutId AND user_serial = :userSerial")
    suspend fun find(packageName: String, shortcutId: String, userSerial: Long): PinnedShortcutEntity?

    @Insert
    suspend fun insert(shortcut: PinnedShortcutEntity): Long

    @Query("DELETE FROM pinned_shortcuts WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
