package com.phaseslauncher.data.items

import androidx.room.withTransaction
import com.phaseslauncher.data.db.FolderEntity
import com.phaseslauncher.data.db.LauncherDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Folders on theme grids. A folder sits in its theme's list like an app (under its folder key);
 * the apps inside it are not also in the theme's list.
 */
class FolderRepository(private val database: LauncherDatabase) {

    private val dao = database.folderDao()
    private val themeDao = database.themeDao()

    /** Every folder by key. */
    val folders: Flow<Map<String, Folder>> =
        combine(dao.observeFolders(), dao.observeItems()) { folders, items ->
            val keysByFolder = items.groupBy({ it.folderId }, { it.itemKey })
            folders.associate { entity ->
                val folder = Folder(entity.id, entity.themeId, entity.name, keysByFolder[entity.id].orEmpty())
                folder.key to folder
            }
        }

    /** Puts a new folder holding [itemKey] where that item was on the theme's grid. Returns its id. */
    suspend fun createWith(themeId: Long, itemKey: String, name: String): Long = database.withTransaction {
        val folderId = dao.insert(FolderEntity(themeId = themeId, name = name.trim().ifBlank { DEFAULT_NAME }))
        val folderKey = ItemKeys.folder(folderId)
        val item = themeDao.getAssignments(themeId).firstOrNull { it.appKey == itemKey }
        val keys = themeDao.getThemeAppKeys(themeId)
        themeDao.setApps(themeId, if (itemKey in keys) keys.map { if (it == itemKey) folderKey else it } else keys + folderKey)
        // The folder takes the item's cell.
        if (item?.cellX != null) themeDao.setCell(themeId, folderKey, item.cellX, item.cellY)
        dao.addItem(folderId, itemKey)
        folderId
    }

    /** Moves [itemKey] from the theme's grid into the folder. */
    suspend fun moveInto(folderId: Long, itemKey: String) = database.withTransaction {
        val folder = dao.get(folderId) ?: return@withTransaction
        themeDao.removeApp(folder.themeId, itemKey)
        dao.addItem(folderId, itemKey)
    }

    /** Moves [itemKey] out of the folder to the end of the theme's grid. An emptied folder is removed. */
    suspend fun moveOut(folderId: Long, itemKey: String) = database.withTransaction {
        val folder = dao.get(folderId) ?: return@withTransaction
        dao.removeItem(folderId, itemKey)
        themeDao.addApp(folder.themeId, itemKey)
        if (dao.getItemKeys(folderId).isEmpty()) delete(folder)
    }

    /** Takes [itemKey] out of the folder without putting it back on the grid. An emptied folder is removed. */
    suspend fun remove(folderId: Long, itemKey: String) = database.withTransaction {
        val folder = dao.get(folderId) ?: return@withTransaction
        dao.removeItem(folderId, itemKey)
        if (dao.getItemKeys(folderId).isEmpty()) delete(folder)
    }

    /** Removes the folder and puts its contents on the theme's grid where the folder was. */
    suspend fun ungroup(folderId: Long) = database.withTransaction {
        val folder = dao.get(folderId) ?: return@withTransaction
        val folderKey = ItemKeys.folder(folderId)
        val contents = dao.getItemKeys(folderId)
        val keys = themeDao.getThemeAppKeys(folder.themeId)
        val others = keys.filter { it != folderKey }.toSet()
        val inserted = contents.filter { it !in others }
        val newKeys = if (folderKey in keys) keys.flatMap { if (it == folderKey) inserted else listOf(it) } else keys + inserted
        val cell = themeDao.getAssignments(folder.themeId).firstOrNull { it.appKey == folderKey }
        themeDao.setApps(folder.themeId, newKeys)
        // The first app takes the folder's cell; the others find free ones.
        val first = inserted.firstOrNull()
        if (first != null && cell?.cellX != null) themeDao.setCell(folder.themeId, first, cell.cellX, cell.cellY)
        dao.delete(folderId)
    }

    suspend fun rename(folderId: Long, name: String) = dao.rename(folderId, name.trim().ifBlank { DEFAULT_NAME })

    suspend fun reorder(folderId: Long, orderedKeys: List<String>) = dao.setItems(folderId, orderedKeys)

    /** Deletes folders left with nothing in them (e.g. all their apps were uninstalled). */
    suspend fun deleteEmpty() = database.withTransaction {
        dao.getAll().filter { dao.getItemKeys(it.id).isEmpty() }.forEach { delete(it) }
    }

    private suspend fun delete(folder: FolderEntity) {
        themeDao.removeApp(folder.themeId, ItemKeys.folder(folder.id))
        dao.delete(folder.id)
    }

    companion object {
        const val DEFAULT_NAME = "Folder"
    }
}
