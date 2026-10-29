package com.themelauncher

import android.app.Application
import android.content.Context
import com.themelauncher.data.apps.AppsRepository
import com.themelauncher.data.apps.IconCache
import com.themelauncher.data.apps.ShortcutsRepository
import com.themelauncher.data.backup.BackupRepository
import com.themelauncher.data.db.LauncherDatabase
import com.themelauncher.data.notifications.NotificationDotsRepository
import com.themelauncher.data.settings.SettingsRepository
import com.themelauncher.data.themes.FavoritesRepository
import com.themelauncher.data.themes.ThemeRepository
import com.themelauncher.data.themes.UninstalledAppCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThemeLauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container; app-wide singletons live here. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appsRepository = AppsRepository(context, appScope)
    val iconCache = IconCache(context, appsRepository)

    val shortcutsRepository = ShortcutsRepository(context)
    val notificationDots = NotificationDotsRepository(context)

    private val database = LauncherDatabase.create(context)
    val themeRepository = ThemeRepository(database.themeDao())
    val favoritesRepository = FavoritesRepository(database.favoritesDao())
    val settingsRepository = SettingsRepository(context)
    val backupRepository = BackupRepository(context, database, settingsRepository)
    private val cleaner = UninstalledAppCleaner(appsRepository::isInstalled, database.themeDao(), database.favoritesDao())

    init {
        appScope.launch {
            appsRepository.packageEvents.collect { iconCache.invalidate(it.packageName) }
        }
        // Warm the icon cache in the background so the drawer opens fully drawn.
        appScope.launch {
            appsRepository.apps.filterNotNull().collect { iconCache.preload(it) }
        }
        // Drop uninstalled apps from themes and favorites: anything removed while we weren't
        // running, then each removal as it happens.
        appScope.launch(Dispatchers.IO) {
            appsRepository.packageEvents.filter { it.removed }.collect { cleaner.prune(it.packageName) }
        }
        appScope.launch(Dispatchers.IO) { cleaner.prune() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ThemeLauncherApp).container
