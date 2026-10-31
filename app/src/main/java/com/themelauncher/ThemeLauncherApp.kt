package com.themelauncher

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.content.Context
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.AppsRepository
import com.themelauncher.data.apps.IconCache
import com.themelauncher.data.apps.ShortcutsRepository
import com.themelauncher.data.backup.BackupRepository
import com.themelauncher.data.db.FavoriteEntity
import com.themelauncher.data.db.LauncherDatabase
import com.themelauncher.data.focus.FocusManager
import com.themelauncher.data.focus.NotificationFilter
import com.themelauncher.data.icons.CustomizationRepository
import com.themelauncher.data.icons.IconPackManager
import com.themelauncher.data.importer.LauncherImporter
import com.themelauncher.data.items.FolderRepository
import com.themelauncher.data.items.ItemKeys
import com.themelauncher.data.items.PinnedShortcutsRepository
import com.themelauncher.data.notifications.NotificationDotsRepository
import com.themelauncher.data.notifications.NotificationPolicy
import com.themelauncher.data.rules.AutoSwitcher
import com.themelauncher.data.search.SearchRepository
import com.themelauncher.data.settings.LauncherPrefs
import com.themelauncher.data.settings.SettingsRepository
import com.themelauncher.data.share.ThemeSharing
import com.themelauncher.data.suggestions.HistoryRepository
import com.themelauncher.data.themes.FavoritesRepository
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.ThemeEffects
import com.themelauncher.data.themes.ThemeLook
import com.themelauncher.data.themes.ThemeRepository
import com.themelauncher.data.themes.UninstalledAppCleaner
import com.themelauncher.data.widgets.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ThemeLauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug builds log disk and network work on the main thread, and leaked resources.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        container = AppContainer(this)
    }
}

/** Manual dependency container; app-wide singletons live here. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
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
    val folderRepository = FolderRepository(database)
    val pinnedShortcuts = PinnedShortcutsRepository(context, database)
    val customizationRepository = CustomizationRepository(database.customizationDao())
    val widgetRepository = WidgetRepository(context, database.widgetDao())
    val settingsRepository = SettingsRepository(context)
    val backupRepository = BackupRepository(context, database, settingsRepository)
    val themeEffects = ThemeEffects(context, settingsRepository)
    val autoSwitcher = AutoSwitcher(context, settingsRepository)
    val focusManager = FocusManager(context)
    val history = HistoryRepository(database.historyDao())
    val themeSharing = ThemeSharing(context, database, themeRepository)
    val launcherImporter = LauncherImporter(context, database, themeRepository)
    private val cleaner = UninstalledAppCleaner(
        isInstalled = appsRepository::isInstalled,
        isPackageInstalled = { pkg, serial -> appsRepository.isPackageInstalled(pkg, serial) },
        database = database,
        folders = folderRepository,
    )

    /** The persisted active theme (falling back to the first theme). */
    val activeTheme: Flow<Theme?> =
        combine(themeRepository.themes, settingsRepository.activeThemeId) { themes, id ->
            themes.firstOrNull { it.id == id } ?: themes.firstOrNull()
        }

    init {
        appScope.launch {
            appsRepository.packageEvents.collect {
                iconCache.invalidate(it.packageName)
                pinnedShortcuts.invalidateIcons()
            }
        }
        // Themed icons follow the active theme's color.
        appScope.launch {
            combine(settingsRepository.prefs.map { it.themedIcons }, activeTheme.map { it?.accentColor }) { on, accent ->
                accent.takeIf { on }
            }.distinctUntilChanged().collect(iconCache::setThemedTint)
        }
        // Fitted home icons can be larger than the icon size setting; render them sharp.
        appScope.launch {
            combine(settingsRepository.prefs, themeRepository.themes) { prefs, themes ->
                // Any theme fitting its grid can show icons up to the fitted maximum.
                val anyFit = prefs.autoFitIcons || themes.any { it.gridLayout == ThemeLook.GRID_FIT }
                if (anyFit) LauncherPrefs.AUTO_FIT_MAX_ICON_DP else prefs.iconSize.dp
            }
                .distinctUntilChanged()
                .collect(iconCache::setIconSizeDp)
        }
        // Icon pack and hand-picked icons feed the icon cache.
        appScope.launch {
            // A theme's own icon pack wins over the launcher setting.
            val pack = combine(settingsRepository.prefs.map { it.iconPack }, activeTheme.map { it?.iconPack }) { global, theme ->
                when (theme) {
                    null -> global
                    ThemeLook.NO_ICON_PACK -> null
                    else -> theme
                }
            }
            combine(pack, customizationRepository.customizations) { p, custom -> p to custom }
                .collect { (p, custom) -> iconCache.setIconSources(p, custom) }
        }
        // Warm the icon cache in the background so the drawer opens fully drawn.
        appScope.launch {
            combine(appsRepository.apps.filterNotNull(), iconCache.generation) { apps, _ -> apps }
                .collectLatest { iconCache.preload(it) }
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
        // The active theme's notification filter, for the notification listener.
        appScope.launch {
            combine(activeTheme, favoritesRepository.docks, folderRepository.folders) { theme, docks, folders ->
                val focus = theme?.focus ?: return@combine null
                val allowed = when (focus.notificationFilter) {
                    NotificationFilter.Off -> return@combine null
                    NotificationFilter.ThemeApps -> {
                        val dock = docks[if (theme.ownDock) theme.id else FavoriteEntity.SHARED_DOCK].orEmpty()
                        val inFolders = folders.values.filter { it.themeId == theme.id }.flatMap { it.itemKeys }
                        theme.appKeys + inFolders + dock + focus.allowedNotificationApps
                    }
                    NotificationFilter.Chosen -> focus.allowedNotificationApps
                }
                NotificationPolicy(
                    allowedPackages = allowed.filter(ItemKeys::isApp).mapTo(HashSet(), AppInfo::packageOf),
                    hide = focus.hideNotifications,
                    allowUrgent = focus.allowUrgent,
                )
            }.distinctUntilChanged().collect { notificationDots.policy.value = it }
        }
        appScope.launch(Dispatchers.IO) { history.prune() }
        themeEffects.start(appScope, activeTheme)
        autoSwitcher.start(appScope, themeRepository.themes)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ThemeLauncherApp).container
