package com.themelauncher

import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.AppsRepository
import com.themelauncher.data.apps.filterApps
import com.themelauncher.data.settings.SettingsRepository
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.themes.AddFavoriteResult
import com.themelauncher.data.themes.FavoritesRepository
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.data.themes.ThemeIconKey
import com.themelauncher.data.themes.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
)

data class HomeState(
    val isLoading: Boolean = true,
    val themes: List<Theme> = emptyList(),
    val activeTheme: Theme? = null,
    /** The active theme's apps that are currently installed and launchable, in order. */
    val themeApps: List<AppInfo> = emptyList(),
    val favorites: List<AppInfo> = emptyList(),
    /** Every launchable app by key. */
    val apps: Map<String, AppInfo> = emptyMap(),
) {
    val canAddFavorite: Boolean get() = favorites.size < ThemeDefaults.MAX_FAVORITES

    /** A theme's launchable apps in order; unavailable ones are skipped. */
    fun appsFor(theme: Theme): List<AppInfo> = theme.appKeys.mapNotNull(apps::get)
}

class LauncherViewModel(
    private val appsRepository: AppsRepository,
    private val themeRepository: ThemeRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** One-off messages for the user (e.g. "Dock is full"). */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val drawerState: StateFlow<DrawerState> =
        combine(appsRepository.apps, _query) { apps, query ->
            DrawerState(isLoading = apps == null, apps = filterApps(apps.orEmpty(), query))
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerState())

    /** Set on tap so the switch is instant; the persisted value catches up a moment later. */
    private val selectedThemeId = MutableStateFlow<Long?>(null)

    private val activeThemeId: Flow<Long?> =
        combine(selectedThemeId, settingsRepository.activeThemeId) { selected, stored -> selected ?: stored }

    val homeState: StateFlow<HomeState> =
        combine(
            appsRepository.apps,
            themeRepository.themes,
            favoritesRepository.favoriteKeys,
            activeThemeId,
        ) { apps, themes, favoriteKeys, activeId ->
            val byKey = apps.orEmpty().associateBy { it.key }
            // A missing or deleted active theme falls back to the first one.
            val active = themes.firstOrNull { it.id == activeId } ?: themes.firstOrNull()
            HomeState(
                isLoading = apps == null,
                themes = themes,
                activeTheme = active,
                themeApps = active?.appKeys.orEmpty().mapNotNull(byKey::get),
                favorites = favoriteKeys.mapNotNull(byKey::get),
                apps = byKey,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** null while loading, so the UI can avoid flashing home before onboarding. */
    val onboardingComplete: StateFlow<Boolean?> =
        settingsRepository.onboardingComplete
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val switcherSide: StateFlow<SwitcherSide> =
        settingsRepository.switcherSide
            .stateIn(viewModelScope, SharingStarted.Eagerly, SwitcherSide.Right)

    fun setSwitcherSide(side: SwitcherSide) {
        viewModelScope.launch { settingsRepository.setSwitcherSide(side) }
    }

    // region Onboarding

    /** Saves the dock and creates the chosen themes; the first one with apps becomes active. */
    fun completeOnboarding(favoriteKeys: List<String>, themes: List<ThemeDraft>) {
        viewModelScope.launch {
            favoritesRepository.setFavorites(favoriteKeys)
            val ids = themes.map { themeRepository.createTheme(it.name, it.icon, it.accentColor, it.appKeys) }
            val firstWithApps = themes.indexOfFirst { it.appKeys.isNotEmpty() }
            ids.getOrNull(firstWithApps.coerceAtLeast(0))?.let { selectTheme(it) }
            settingsRepository.setOnboardingComplete(true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    // endregion

    // region Drawer

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

    // endregion

    // region Apps

    fun launch(app: AppInfo, sourceBounds: Rect?) {
        if (!appsRepository.launch(app, sourceBounds)) message("${app.label} is not available")
    }

    fun openAppInfo(app: AppInfo, sourceBounds: Rect?) = appsRepository.openAppInfo(app, sourceBounds)

    fun uninstall(app: AppInfo) = appsRepository.requestUninstall(app)

    // endregion

    // region Themes

    fun selectTheme(themeId: Long) {
        selectedThemeId.value = themeId
        viewModelScope.launch { settingsRepository.setActiveThemeId(themeId) }
    }

    /** Creates a theme and makes it active. */
    fun createTheme(name: String, icon: ThemeIconKey, accentColor: Int, appKeys: List<String> = emptyList()) {
        viewModelScope.launch {
            selectTheme(themeRepository.createTheme(name, icon, accentColor, appKeys))
        }
    }

    fun updateTheme(themeId: Long, name: String, icon: ThemeIconKey, accentColor: Int, appKeys: List<String>) {
        viewModelScope.launch {
            themeRepository.updateTheme(themeId, name, icon, accentColor)
            themeRepository.setThemeApps(themeId, appKeys)
        }
    }

    fun deleteTheme(themeId: Long) {
        viewModelScope.launch {
            val themes = homeState.value.themes
            if (!themeRepository.deleteTheme(themeId)) {
                message("You need at least one theme")
                return@launch
            }
            // If the active theme was deleted, move to its neighbour rather than jumping to the first.
            if (activeThemeId.first() == themeId) {
                val index = themes.indexOfFirst { it.id == themeId }
                val remaining = themes.filter { it.id != themeId }
                remaining.getOrNull((index - 1).coerceAtLeast(0))?.let { selectTheme(it.id) }
            }
        }
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

    // region Favorites

    fun addFavorite(app: AppInfo) {
        viewModelScope.launch { reportFavoriteResult(app, favoritesRepository.add(app.key)) }
    }

    /** Adds the app to the dock and, if that worked, takes it out of the active theme's grid. */
    fun moveToFavorites(app: AppInfo) {
        viewModelScope.launch {
            val result = favoritesRepository.add(app.key)
            reportFavoriteResult(app, result)
            if (result == AddFavoriteResult.Added) {
                homeState.value.activeTheme?.let { themeRepository.removeApp(it.id, app.key) }
            }
        }
    }

    fun removeFavorite(app: AppInfo) {
        viewModelScope.launch { favoritesRepository.remove(app.key) }
    }

    fun reorderFavorites(visibleOrder: List<String>) {
        viewModelScope.launch {
            favoritesRepository.setFavorites(withHidden(visibleOrder, favoritesRepository.favoriteKeys.first()))
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

    private fun message(text: String) {
        _messages.trySend(text)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                LauncherViewModel(
                    appsRepository = container.appsRepository,
                    themeRepository = container.themeRepository,
                    favoritesRepository = container.favoritesRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
