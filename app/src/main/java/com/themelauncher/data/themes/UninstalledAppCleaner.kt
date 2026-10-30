package com.themelauncher.data.themes

import androidx.room.withTransaction
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.db.LauncherDatabase

/**
 * Removes uninstalled apps from every theme, every dock, usage history and customizations.
 *
 * Only packages that are truly gone are removed. Apps that are merely disabled, suspended,
 * mid-update or on a paused work profile keep their place and are just hidden by the UI.
 */
class UninstalledAppCleaner(
    private val isInstalled: (appKey: String) -> Boolean,
    private val database: LauncherDatabase,
) {
    /** Checks every stored app, or only those of [packageName] when given. Returns the removed keys. */
    suspend fun prune(packageName: String? = null): List<String> {
        val themeDao = database.themeDao()
        val favoritesDao = database.favoritesDao()
        val stored = themeDao.getAllAssignedKeys() + favoritesDao.getAllKeys() +
            database.customizationDao().getAll().map { it.appKey }
        val gone = stored
            .distinct()
            .filter { packageName == null || AppInfo.packageOf(it) == packageName }
            .filterNot(isInstalled)
        if (gone.isNotEmpty()) {
            database.withTransaction {
                themeDao.removeAppsEverywhere(gone)
                favoritesDao.removeEverywhere(gone)
                database.usageDao().remove(gone)
                gone.forEach { database.customizationDao().delete(it) }
            }
        }
        return gone
    }
}
