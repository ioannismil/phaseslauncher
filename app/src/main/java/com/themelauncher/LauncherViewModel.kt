package com.themelauncher

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.AppShortcut
import com.themelauncher.data.apps.ProfileState
import com.themelauncher.data.apps.ProfileType
import com.themelauncher.data.apps.filterApps
import com.themelauncher.data.db.AppCustomizationEntity
import com.themelauncher.data.db.FavoriteEntity
import com.themelauncher.data.icons.IconPackInfo
import com.themelauncher.data.rules.ThemeRule
import com.themelauncher.data.search.ContactResult
import com.themelauncher.data.search.SettingsResult
import com.themelauncher.data.settings.LauncherPrefs
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.themes.AddFavoriteResult
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.data.themes.ThemeIconKey
import com.themelauncher.data.widgets.ThemeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.exp

data class DrawerState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    /** Personal plus any work profile / private space; tabs are shown when there's more than one. */
    val profiles: List<ProfileState> = emptyList(),
    val selectedProfile: ProfileType = ProfileType.Personal,
    /** Frequently and recently used apps, shown above the list when not searching. */
    val suggestions: List<AppInfo> = emptyList(),
    val contacts: List<ContactResult> = emptyList(),
    val settingsResults: List<SettingsResult> = emptyList(),
    /** Offer to enable contact search (searching, contact search on, permission not granted). */
    val askContactsPermission: Boolean = false,
) {
    val selectedProfileState: ProfileState? get() = profiles.firstOrNull { it.type == selectedProfile }
}

data class HomeState(
    val isLoading: Boolean = true,
    val themes: List<Theme> = emptyList(),
    val activeTheme: Theme? = null,
    /** The active theme's apps that are currently installed and launchable, in order. */
    val themeApps: List<AppInfo> = emptyList(),
    /** The dock being shown: the active theme's own dock, or the shared one. */
    val favorites: List<AppInfo> = emptyList(),
    val dockId: Long = FavoriteEntity.SHARED_DOCK,
    /** Widgets per theme id. */
    val widgets: Map<Long, List<ThemeWidget>> = emptyMap(),
    /** Every launchable app by key (custom names applied). */
    val apps: Map<String, AppInfo> = emptyMap(),
) {
    val canAddFavorite: Boolean get() = favorites.size < ThemeDefaults.MAX_FAVORITES

    /** A theme's launchable apps in order; unavailable ones are skipped. */
    fun appsFor(theme: Theme): List<AppInfo> = theme.appKeys.mapNotNull(apps::get)

    fun widgetsFor(theme: Theme): List<ThemeWidget> = widgets[theme.id].orEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LauncherViewModel(private val container: AppContainer) : ViewModel() {

    private val appsRepository = container.appsRepository
    private val themeRepository = container.themeRepository
    private val favoritesRepository = container.favoritesRepository
    private val settingsRepository = container.settingsRepository
    private val searchRepository = container.searchRepository
    val notificationDots = container.notificationDots
    val widgets = container.widgetRepository
    val themeEffects = container.themeEffects
    val autoSwitcher = container.autoSwitcher

    val customizations: StateFlow<Map<String, AppCustomizationEntity>> =
        container.customizationRepository.customizations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val prefs: StateFlow<LauncherPrefs> =
        settingsRepository.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPrefs())

    private val selectedProfile = MutableStateFlow(ProfileType.Personal)

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** One-off messages for the user (e.g. "Dock is full"). */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Installed apps with custom names applied, re-sorted by the shown name. */
    private val apps: Flow<List<AppInfo>?> =
        combine(appsRepository.apps, container.customizationRepository.customizations) { apps, custom ->
            if (apps == null) return@combine null
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            apps.map { app -> custom[app.key]?.label?.let { app.copy(label = it) } ?: app }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }.flowOn(Dispatchers.Default)

    private val suggestions: Flow<List<String>> =
        container.database.usageDao().observeAll().map { usage ->
            val now = System.currentTimeMillis()
            // Frequency, weighted toward recent use (half-life of about a week).
            usage.sortedByDescending { it.launchCount * exp(-(now - it.lastLaunched) / WEEK_MS) }
                .take(SUGGESTION_COUNT * 2)
                .map { it.appKey }
        }

    private val searchRefresh = MutableStateFlow(0)

    private val searchExtras: Flow<Triple<List<ContactResult>, List<SettingsResult>, Boolean>> =
        combine(_query.debounce(120), prefs.map { it.contactSearch }.distinctUntilChanged(), searchRefresh) { q, contactSearch, _ ->
            q to contactSearch
        }
            .mapLatest { (q, contactSearch) ->
                if (q.isBlank()) return@mapLatest Triple(emptyList(), emptyList(), false)
                val canSearchContacts = contactSearch && searchRepository.hasContactsPermission()
                Triple(
                    if (canSearchContacts) searchRepository.searchContacts(q) else emptyList(),
                    searchRepository.searchSettings(q),
                    contactSearch && !canSearchContacts,
                )
            }

    val drawerState: StateFlow<DrawerState> =
        combine(
            combine(apps, appsRepository.profiles, ::Pair),
            _query,
            prefs.map { it.hiddenApps }.distinctUntilChanged(),
            selectedProfile,
            combine(suggestions, searchExtras, ::Pair),
        ) { (apps, profiles), query, hidden, selected, (suggestedKeys, extras) ->
            val tab = selected.takeIf { type -> profiles.any { it.type == type } } ?: ProfileType.Personal
            val visible = apps.orEmpty().filter { it.key !in hidden }
            val byKey = visible.associateBy { it.key }
            DrawerState(
                isLoading = apps == null,
                // Searching looks across every profile; browsing shows the selected tab.
                apps = if (query.isBlank()) visible.filter { it.profile == tab } else filterApps(visible, query),
                profiles = profiles,
                selectedProfile = tab,
                suggestions = if (query.isBlank() && tab == ProfileType.Personal) {
                    suggestedKeys.mapNotNull(byKey::get).take(SUGGESTION_COUNT)
                } else {
                    emptyList()
                },
                contacts = extras.first,
                settingsResults = extras.second,
                askContactsPermission = extras.third,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerState())

    /** Set on tap so the switch is instant; cleared whenever the persisted value changes. */
    private val selectedThemeId = MutableStateFlow<Long?>(null)

    private val activeThemeId: Flow<Long?> =
        combine(selectedThemeId, settingsRepository.activeThemeId) { selected, stored -> selected ?: stored }

    val homeState: StateFlow<HomeState> =
        combine(
            apps,
            themeRepository.themes,
            favoritesRepository.docks,
            activeThemeId,
            widgets.widgets,
        ) { apps, themes, docks, activeId, widgets ->
            val byKey = apps.orEmpty().associateBy { it.key }
            // A missing or deleted active theme falls back to the first one.
            val active = themes.firstOrNull { it.id == activeId } ?: themes.firstOrNull()
            val dockId = if (active?.ownDock == true) active.id else FavoriteEntity.SHARED_DOCK
            HomeState(
                isLoading = apps == null,
                themes = themes,
                activeTheme = active,
                themeApps = active?.appKeys.orEmpty().mapNotNull(byKey::get),
                favorites = docks[dockId].orEmpty().mapNotNull(byKey::get),
                dockId = dockId,
                widgets = widgets,
                apps = byKey,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** null while loading, so the UI can avoid flashing home before onboarding. */
    val onboardingComplete: StateFlow<Boolean?> =
        settingsRepository.onboardingComplete
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Automatic switching (and restores) write the persisted value; let it win over a stale tap.
        viewModelScope.launch { settingsRepository.activeThemeId.collect { selectedThemeId.value = null } }
    }

    // region Settings

    fun updatePrefs(transform: (LauncherPrefs) -> LauncherPrefs) {
        viewModelScope.launch { settingsRepository.updatePrefs(transform) }
    }

    fun setSwitcherSide(side: SwitcherSide) = updatePrefs { it.copy(switcherSide = side) }

    fun hideApp(app: AppInfo) {
        updatePrefs { it.copy(hiddenApps = it.hiddenApps + app.key) }
        message("${app.label} hidden. Unhide it in Launcher settings.")
    }

    suspend fun installedIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) { container.iconPacks.installedPacks() }

    suspend fun iconPackDrawables(pack: String): List<String> = withContext(Dispatchers.IO) { container.iconPacks.drawableNames(pack) }

    fun iconPackManager() = container.iconPacks

    fun resetCustomization(app: AppInfo) = customizeApp(app, null, null, null)

    fun customizeApp(app: AppInfo, label: String?, iconPack: String?, iconName: String?) {
        viewModelScope.launch {
            container.customizationRepository.set(app.key, label?.takeIf { it != app.defaultLabel }, iconPack, iconName)
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.backupRepository.exportTo(uri) }
                .onSuccess { message("Backed up ${it.themes} themes and ${it.favorites} favorites") }
                .onFailure { message("Backup failed: ${it.message}") }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            // Widget ids belong to this phone's widget host; the restored themes start without widgets.
            widgets.removeAll()
            runCatching { container.backupRepository.importFrom(uri) }
                .onSuccess { message("Restored ${it.themes} themes and ${it.favorites} favorites") }
                .onFailure { message("Couldn't restore: ${it.message}") }
        }
    }

    // endregion

    // region Profiles

    fun selectProfileTab(type: ProfileType) {
        selectedProfile.value = type
    }

    /** Pause/resume the work profile, or lock/unlock the private space. */
    fun setProfileQuiet(profile: ProfileState, quiet: Boolean) = appsRepository.setProfileQuiet(profile.user, quiet)

    // endregion

    // region Onboarding

    /** Saves the dock and creates the chosen themes; the first one with apps becomes active. */
    fun completeOnboarding(favoriteKeys: List<String>, themes: List<ThemeDraft>) {
        viewModelScope.launch {
            favoritesRepository.setFavorites(favoriteKeys)
            val ids = themes.map { themeRepository.createTheme(it) }
            val firstWithApps = themes.indexOfFirst { it.appKeys.isNotEmpty() }
            ids.getOrNull(firstWithApps.coerceAtLeast(0))?.let { selectTheme(it) }
            settingsRepository.setOnboardingComplete(true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    // endregion

    // region Drawer & search

    fun openDrawer() {
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
        _query.value = ""
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    /** The Home button was pressed while we were already in front. */
    fun onHomePressed() = closeDrawer()

    /** Re-runs the current search, e.g. after the contacts permission was granted. */
    fun refreshSearch() = searchRefresh.update { it + 1 }

    fun openContact(contact: ContactResult) = searchRepository.openContact(contact)

    fun openSettingsResult(result: SettingsResult) = searchRepository.openSettings(result)

    fun searchWeb(query: String) = searchRepository.searchWeb(query)

    // endregion

    // region Apps

    fun launch(app: AppInfo, sourceBounds: Rect?) {
        if (appsRepository.launch(app, sourceBounds)) {
            viewModelScope.launch { container.database.usageDao().recordLaunch(app.key, System.currentTimeMillis()) }
        } else {
            message("${app.label} is not available")
        }
    }

    fun openAppInfo(app: AppInfo, sourceBounds: Rect?) = appsRepository.openAppInfo(app, sourceBounds)

    fun uninstall(app: AppInfo) = appsRepository.requestUninstall(app)

    suspend fun shortcutsFor(app: AppInfo): List<AppShortcut> = container.shortcutsRepository.shortcutsFor(app)

    fun launchShortcut(shortcut: AppShortcut, sourceBounds: Rect?) {
        if (!container.shortcutsRepository.launch(shortcut, sourceBounds)) message("Couldn't open “${shortcut.label}”")
    }

    // endregion

    // region Themes

    fun selectTheme(themeId: Long) {
        selectedThemeId.value = themeId
        viewModelScope.launch { settingsRepository.setActiveThemeId(themeId) }
    }

    /** Creates a theme and makes it active. */
    fun createTheme(draft: ThemeDraft) {
        viewModelScope.launch {
            selectTheme(themeRepository.createTheme(draft))
            refreshAfterRuleChange(draft)
        }
    }

    fun createTheme(name: String, icon: ThemeIconKey, accentColor: Int, appKeys: List<String> = emptyList()) =
        createTheme(ThemeDraft(name, icon, accentColor, appKeys))

    fun updateTheme(themeId: Long, draft: ThemeDraft) {
        viewModelScope.launch {
            themeRepository.updateTheme(themeId, draft)
            cleanUpWallpapers()
            refreshAfterRuleChange(draft)
        }
    }

    fun deleteTheme(themeId: Long) {
        viewModelScope.launch {
            val themes = homeState.value.themes
            if (themes.size <= 1) {
                message("You need at least one theme")
                return@launch
            }
            widgets.removeForTheme(themeId)
            themeRepository.deleteTheme(themeId)
            cleanUpWallpapers()
            // If the active theme was deleted, move to its neighbour rather than jumping to the first.
            if (activeThemeId.first() == themeId) {
                val index = themes.indexOfFirst { it.id == themeId }
                val remaining = themes.filter { it.id != themeId }
                remaining.getOrNull((index - 1).coerceAtLeast(0))?.let { selectTheme(it.id) }
            }
        }
    }

    suspend fun importWallpaper(uri: Uri): String? = themeEffects.importWallpaper(uri)

    private suspend fun cleanUpWallpapers() {
        val inUse = themeRepository.themes.first().mapNotNull { it.wallpaperPath }.toSet()
        themeEffects.deleteUnusedWallpapers(inUse)
    }

    private fun refreshAfterRuleChange(draft: ThemeDraft) {
        if (draft.rules.any { it is ThemeRule.Bluetooth }) autoSwitcher.refreshBluetooth()
    }

    fun reorderThemes(orderedIds: List<Long>) {
        viewModelScope.launch { themeRepository.reorderThemes(orderedIds) }
    }

    fun addAppToThemes(app: AppInfo, themeIds: Collection<Long>) {
        viewModelScope.launch {
            themeIds.forEach { themeRepository.addApp(it, app.key) }
        }
    }

    fun removeAppFromActiveTheme(app: AppInfo) {
        val theme = homeState.value.activeTheme ?: return
        viewModelScope.launch { themeRepository.removeApp(theme.id, app.key) }
    }

    /** [visibleOrder] covers only the apps on screen; hidden (unavailable) ones keep their place at the end. */
    fun reorderThemeApps(themeId: Long, visibleOrder: List<String>) {
        val theme = homeState.value.themes.firstOrNull { it.id == themeId } ?: return
        viewModelScope.launch { themeRepository.setThemeApps(themeId, withHidden(visibleOrder, theme.appKeys)) }
    }

    // endregion

    // region Widgets

    /**
     * Adds a bound (and, if needed, configured) widget to the active theme. Called by the widget
     * flow once the system has granted the binding.
     */
    fun placeWidget(appWidgetId: Int, info: AppWidgetProviderInfo, density: Float) {
        val theme = homeState.value.activeTheme ?: return
        viewModelScope.launch { widgets.add(appWidgetId, theme.id, info, density) }
    }

    fun removeWidget(appWidgetId: Int) {
        viewModelScope.launch { widgets.remove(appWidgetId) }
    }

    fun resizeWidget(appWidgetId: Int, heightDp: Int) {
        viewModelScope.launch { widgets.setHeight(appWidgetId, heightDp) }
    }

    fun moveWidget(theme: Theme, appWidgetId: Int, delta: Int) {
        val ids = homeState.value.widgetsFor(theme).map { it.appWidgetId }.toMutableList()
        val from = ids.indexOf(appWidgetId)
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { widgets.reorder(ids) }
    }

    // endregion

    // region Favorites (the dock shown on the active theme)

    private val dockId get() = homeState.value.dockId

    fun addFavorite(app: AppInfo) {
        viewModelScope.launch { reportFavoriteResult(app, favoritesRepository.add(app.key, dockId)) }
    }

    /** Adds the app to the dock and, if that worked, takes it out of the active theme's grid. */
    fun moveToFavorites(app: AppInfo) {
        viewModelScope.launch {
            val result = favoritesRepository.add(app.key, dockId)
            reportFavoriteResult(app, result)
            if (result == AddFavoriteResult.Added) {
                homeState.value.activeTheme?.let { themeRepository.removeApp(it.id, app.key) }
            }
        }
    }

    fun removeFavorite(app: AppInfo) {
        viewModelScope.launch { favoritesRepository.remove(app.key, dockId) }
    }

    fun reorderFavorites(visibleOrder: List<String>) {
        val dock = dockId
        viewModelScope.launch {
            favoritesRepository.setFavorites(withHidden(visibleOrder, favoritesRepository.getKeys(dock)), dock)
        }
    }

    private fun withHidden(visibleOrder: List<String>, allKeys: List<String>): List<String> =
        visibleOrder + allKeys.filter { it !in visibleOrder }

    private fun reportFavoriteResult(app: AppInfo, result: AddFavoriteResult) = when (result) {
        AddFavoriteResult.Added -> Unit
        AddFavoriteResult.AlreadyFavorite -> message("${app.label} is already in the dock")
        AddFavoriteResult.Full -> message("The dock holds ${ThemeDefaults.MAX_FAVORITES} apps. Remove one first.")
    }

    // endregion

    fun message(text: String) {
        _messages.trySend(text)
    }

    companion object {
        private const val SUGGESTION_COUNT = 5
        private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000.0

        val Factory = viewModelFactory {
            initializer { LauncherViewModel(this[APPLICATION_KEY]!!.appContainer) }
        }
    }
}
