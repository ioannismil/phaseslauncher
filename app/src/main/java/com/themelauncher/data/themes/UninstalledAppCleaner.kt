package com.themelauncher.data.themes

import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.db.FavoritesDao
import com.themelauncher.data.db.ThemeDao

/**
 * Removes uninstalled apps from every theme and from the favorites.
 *
 * Only packages that are truly gone are removed. Apps that are merely disabled, suspended,
 * mid-update or on a paused work profile keep their place and are just hidden by the UI.
 */
class UninstalledAppCleaner(
    private val isInstalled: (appKey: String) -> Boolean,
    private val themeDao: ThemeDao,
    private val favoritesDao: FavoritesDao,
) {
    /** Checks every stored app, or only those of [packageName] when given. Returns the removed keys. */
    suspend fun prune(packageName: String? = null): List<String> {
        val gone = (themeDao.getAllAssignedKeys() + favoritesDao.getFavoriteKeys())
            .distinct()
            .filter { packageName == null || AppInfo.packageOf(it) == packageName }
            .filterNot(isInstalled)
        if (gone.isNotEmpty()) {
            themeDao.removeAppsEverywhere(gone)
            favoritesDao.remove(gone)
        }
        return gone
    }
}
