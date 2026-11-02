package com.phaseslauncher.data.themes

import androidx.room.withTransaction
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.items.FolderRepository
import com.phaseslauncher.data.items.ItemKeys

/**
 * Removes uninstalled apps from every theme, folder, dock, usage history and customizations, and
 * drops the shortcuts they had pinned.
 *
 * Only packages that are truly gone are removed. Apps that are merely disabled, suspended,
 * mid-update or on a paused work profile keep their place and are just hidden by the UI.
 */
class UninstalledAppCleaner(
    private val isInstalled: (appKey: String) -> Boolean,
    private val isPackageInstalled: (packageName: String, userSerial: Long) -> Boolean,
    private val database: LauncherDatabase,
    private val folders: FolderRepository,
) {
    /** Checks every stored app, or only those of [packageName] when given. Returns the removed keys. */
    suspend fun prune(packageName: String? = null): List<String> {
        val themeDao = database.themeDao()
        val favoritesDao = database.favoritesDao()
        val folderDao = database.folderDao()
        val stored = themeDao.getAllAssignedKeys() + folderDao.getAllItemKeys() + favoritesDao.getAllKeys() +
            database.customizationDao().getAll().map { it.appKey }
        val goneApps = stored
            .distinct()
            .filter(ItemKeys::isApp)
            .filter { packageName == null || AppInfo.packageOf(it) == packageName }
            .filterNot(isInstalled)
        val goneShortcuts = database.pinnedShortcutDao().getAll()
            .filter { packageName == null || it.packageName == packageName }
            .filterNot { isPackageInstalled(it.packageName, it.userSerial) }
        val gone = goneApps + goneShortcuts.map { ItemKeys.shortcut(it.id) }
        if (gone.isNotEmpty()) {
            database.withTransaction {
                themeDao.removeAppsEverywhere(gone)
                folderDao.removeItemsEverywhere(gone)
                favoritesDao.removeEverywhere(goneApps)
                database.usageDao().remove(goneApps)
                goneApps.forEach { database.customizationDao().delete(it) }
                database.pinnedShortcutDao().delete(goneShortcuts.map { it.id })
            }
            folders.deleteEmpty()
        }
        return gone
    }
}
