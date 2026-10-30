package com.themelauncher

import android.app.Application
import android.content.Context
import com.themelauncher.data.apps.AppsRepository
import com.themelauncher.data.apps.IconCache
import com.themelauncher.data.apps.ShortcutsRepository
import com.themelauncher.data.backup.BackupRepository
import com.themelauncher.data.db.LauncherDatabase
import com.themelauncher.data.icons.CustomizationRepository
import com.themelauncher.data.icons.IconPackManager
import com.themelauncher.data.notifications.NotificationDotsRepository
import com.themelauncher.data.rules.AutoSwitcher
import com.themelauncher.data.search.SearchRepository
import com.themelauncher.data.settings.SettingsRepository
import com.themelauncher.data.themes.FavoritesRepository
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.ThemeEffects
import com.themelauncher.data.themes.ThemeRepository
import com.themelauncher.data.themes.UninstalledAppCleaner
import com.themelauncher.data.widgets.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
    val iconPacks = IconPackManager(context)
    val iconCache = IconCache(context, appsRepository, iconPacks)

    val shortcutsRepository = ShortcutsRepository(context)
    val notificationDots = NotificationDotsRepository(context)
    val searchRepository = SearchRepository(context)

    val database = LauncherDatabase.create(context)
    val themeRepository = ThemeRepository(database)
    val favoritesRepository = FavoritesRepository(database.favoritesDao())
    val customizationRepository = CustomizationRepository(database.customizationDao())
    val widgetRepository = WidgetRepository(context, database.widgetDao())
    val settingsRepository = SettingsRepository(context)
    val backupRepository = BackupRepository(context, database, settingsRepository)
    val themeEffects = ThemeEffects(context, settingsRepository)
    val autoSwitcher = AutoSwitcher(context, settingsRepository)
    private val cleaner = UninstalledAppCleaner(appsRepository::isInstalled, database)

    /** The persisted active theme (falling back to the first theme). */
    val activeTheme: Flow<Theme?> =
        combine(themeRepository.themes, settingsRepository.activeThemeId) { themes, id ->
            themes.firstOrNull { it.id == id } ?: themes.firstOrNull()
        }

    init {
        appScope.launch {
            appsRepository.packageEvents.collect { iconCache.invalidate(it.packageName) }
        }
        // Icon pack and hand-picked icons feed the icon cache.
        appScope.launch {
            combine(settingsRepository.prefs.map { it.iconPack }, customizationRepository.customizations) { pack, custom -> pack to custom }
                .collect { (pack, custom) -> iconCache.setIconSources(pack, custom) }
        }
        // Warm the icon cache in the background so the drawer opens fully drawn.
        appScope.launch {
            appsRepository.apps.filterNotNull().collect { iconCache.preload(it) }
        }
        // Drop uninstalled apps (and their widgets) everywhere: anything removed while we weren't
        // running, then each removal as it happens.
        appScope.launch(Dispatchers.IO) {
            appsRepository.packageEvents.filter { it.removed }.collect {
                cleaner.prune(it.packageName)
                widgetRepository.pruneOrphans()
            }
        }
        appScope.launch(Dispatchers.IO) {
            cleaner.prune()
            widgetRepository.pruneOrphans()
        }
        themeEffects.start(appScope, activeTheme)
        autoSwitcher.start(appScope, themeRepository.themes)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ThemeLauncherApp).container
