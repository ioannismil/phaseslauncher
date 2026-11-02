package com.phaseslauncher

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.AppShortcut
import com.phaseslauncher.data.apps.ProfileState
import com.phaseslauncher.data.apps.ProfileType
import com.phaseslauncher.data.apps.filterApps
import com.phaseslauncher.data.db.AppCustomizationEntity
import com.phaseslauncher.data.db.FavoriteEntity
import com.phaseslauncher.data.focus.FocusBlock
import com.phaseslauncher.data.icons.IconPackInfo
import com.phaseslauncher.data.importer.LauncherLayout
import com.phaseslauncher.data.importer.LayoutImportOptions
import com.phaseslauncher.data.items.AppItem
import com.phaseslauncher.data.items.Folder
import com.phaseslauncher.data.items.FolderItem
import com.phaseslauncher.data.items.FolderRepository
import com.phaseslauncher.data.items.GridPlacement
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.items.PinnedShortcut
import com.phaseslauncher.data.items.ShortcutItem
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.RuleText
import com.phaseslauncher.data.rules.ThemeRule
import com.phaseslauncher.data.runCatchingCancellable
import com.phaseslauncher.data.search.Calculation
import com.phaseslauncher.data.search.Calculator
import com.phaseslauncher.data.search.ContactResult
import com.phaseslauncher.data.search.SettingsResult
import com.phaseslauncher.data.settings.DrawerSort
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.share.ImportOptions
import com.phaseslauncher.data.share.Resolution
import com.phaseslauncher.data.share.ShareOptions
import com.phaseslauncher.data.share.SharedTheme
import com.phaseslauncher.data.suggestions.Suggestion
import com.phaseslauncher.data.suggestions.SuggestionEngine
import com.phaseslauncher.data.suggestions.SuggestionSettings
import com.phaseslauncher.data.themes.AddFavoriteResult
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.themes.ThemeDraft
import com.phaseslauncher.data.themes.ThemeIconKey
import com.phaseslauncher.data.themes.toDraft
import com.phaseslauncher.data.widgets.ThemeWidget
import com.phaseslauncher.ui.components.HomeDrop
import java.text.Collator
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LauncherViewModel(private val container: AppContainer) : ViewModel() {

    private val res = container.appContext.resources
    private val appsRepository = container.appsRepository
    private val themeRepository = container.themeRepository
    private val favoritesRepository = container.favoritesRepository
    private val settingsRepository = container.settingsRepository
    private val searchRepository = container.searchRepository
    val notificationDots = container.notificationDots
    val widgets = container.widgetRepository
    val themeEffects = container.themeEffects
    val autoSwitcher = container.autoSwitcher
    val focusManager = container.focusManager
    private val history = container.history

    val customizations: StateFlow<Map<String, AppCustomizationEntity>> =
        container.customizationRepository.customizations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val prefs: StateFlow<LauncherPrefs> =
        settingsRepository.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPrefs())

    private val selectedProfile = MutableStateFlow(ProfileType.Personal)

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    /** Whether the drawer opened for searching (keyboard up) rather than browsing. */
    private val _drawerKeyboard = MutableStateFlow(false)
    val drawerKeyboard: StateFlow<Boolean> = _drawerKeyboard.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** One-off messages for the user (e.g. "Dock is full"). */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Fires when the launcher comes back after opening an app, so home can zoom back in. */
    private val _returnedFromApp = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnedFromApp: SharedFlow<Unit> = _returnedFromApp.asSharedFlow()
    private var appInFront = false

    /** Installed apps with custom names applied, re-sorted by the shown name. */
    private val apps: Flow<List<AppInfo>?> =
        combine(appsRepository.apps, container.customizationRepository.customizations) { apps, custom ->
            if (apps == null) return@combine null
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            apps.map { app -> custom[app.key]?.label?.let { app.copy(label = it) } ?: app }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }.flowOn(Dispatchers.Default)

    private val usage = container.database.usageDao().observeAll().shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val drawerSort: Flow<Pair<DrawerSort, Map<String, Int>>> =
        combine(prefs.map { it.drawerSort }.distinctUntilChanged(), usage) { sort, rows -> sort to rows.associate { it.appKey to it.launchCount } }

    private val suggestions: Flow<List<String>> =
        usage.map { usage ->
            val now = System.currentTimeMillis()
            // Frequency, weighted toward recent use (half-life of about a week).
            usage.sortedByDescending { it.launchCount * exp(-(now - it.lastLaunched) / WEEK_MS) }
                .take(SUGGESTION_COUNT * 2)
                .map { it.appKey }
        }

    private val searchRefresh = MutableStateFlow(0)

    /** Apps the active theme blocks and hides from the drawer ("Hide blocked apps"). */
    private val blockedInDrawer = MutableStateFlow<Set<String>>(emptySet())

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
            combine(prefs.map { it.hiddenApps }.distinctUntilChanged(), blockedInDrawer) { hidden, blocked -> hidden + blocked },
            selectedProfile,
            combine(suggestions, searchExtras, drawerSort, ::Triple),
        ) { (apps, profiles), query, hidden, selected, (suggestedKeys, extras, sorting) ->
            val tab = selected.takeIf { type -> profiles.any { it.type == type } } ?: ProfileType.Personal
            val visible = apps.orEmpty().filter { it.key !in hidden }
            val byKey = visible.associateBy { it.key }
            val (sort, launchCounts) = sorting
            // Ties keep the alphabetical order the apps arrive in.
            val browsing = when (sort) {
                DrawerSort.Name -> visible
                DrawerSort.MostUsed -> visible.sortedByDescending { launchCounts[it.key] ?: 0 }
                DrawerSort.Newest -> visible.sortedByDescending { it.installTime }
            }
            DrawerState(
                isLoading = apps == null,
                // Searching looks across every profile; browsing shows the selected tab.
                apps = if (query.isBlank()) browsing.filter { it.profile == tab } else filterApps(visible, query),
                sort = sort,
                calculation = if (query.isBlank()) null else Calculator.evaluate(query),
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
            combine(apps, container.pinnedShortcuts.shortcuts, container.folderRepository.folders, ::Triple),
            themeRepository.themes,
            favoritesRepository.docks,
            activeThemeId,
            widgets.widgets,
        ) { (apps, shortcuts, folders), themes, docks, activeId, widgets ->
            val byKey = apps.orEmpty().associateBy { it.key }
            // A shortcut shows only while its app does (e.g. not on a paused work profile).
            val appByPackage = apps.orEmpty().associateBy { it.packageName to it.user }
            val shortcutItems = shortcuts.mapNotNull { s -> appByPackage[s.packageName to s.user]?.let { ShortcutItem(s, it) } }
                .associateBy { it.key }
            // A missing or deleted active theme falls back to the first one.
            val active = themes.firstOrNull { it.id == activeId } ?: themes.firstOrNull()
            val dockId = if (active?.ownDock == true) active.id else FavoriteEntity.SHARED_DOCK
            HomeState(
                isLoading = apps == null,
                themes = themes,
                activeTheme = active,
                favorites = docks[dockId].orEmpty().mapNotNull(byKey::get),
                dockId = dockId,
                widgets = widgets,
                apps = byKey,
                shortcuts = shortcutItems,
                folders = folders,
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
        viewModelScope.launch {
            homeState.collect { state ->
                val theme = state.activeTheme
                val focus = theme?.focus
                blockedInDrawer.value = if (focus == null || !focus.hideBlockedApps) {
                    emptySet()
                } else {
                    val inTheme = state.appKeysOf(theme) + state.favorites.map { it.key }
                    state.apps.keys.filterTo(HashSet()) { focus.blocks(it, inTheme) }
                }
            }
        }
    }

    // region Focus

    private val _pendingLaunch = MutableStateFlow<PendingLaunch?>(null)

    /** A launch waiting on the pause screen (or refused); the UI shows why. */
    val pendingLaunch: StateFlow<PendingLaunch?> = _pendingLaunch.asStateFlow()

    /** The pause screen was waited out: open the app and let it be for the theme's allowance. */
    fun proceedPendingLaunch() {
        val pending = _pendingLaunch.value ?: return
        _pendingLaunch.value = null
        focusManager.allow(pending.block)
        pending.proceed()
    }

    fun cancelPendingLaunch() {
        _pendingLaunch.value = null
    }

    /** Runs [launch] unless the active theme's focus settings hold [app] back. */
    private fun guarded(app: AppInfo, launch: () -> Unit) {
        val state = homeState.value
        val anyFocus = state.themes.any { it.focus.blocksAnything || it.focus.hasLimits }
        if (!anyFocus) return launch()
        viewModelScope.launch {
            val block = focusManager.check(
                app = app,
                active = state.activeTheme,
                themes = state.themes,
                dockKeys = state.favorites.mapTo(HashSet()) { it.key },
                appsOf = state::appKeysOf,
            )
            if (block == null) launch() else _pendingLaunch.value = PendingLaunch(block, launch)
        }
    }

    // endregion

    // region Backup, sharing and importing

    val transfers = PhaseTransfers(container, viewModelScope, homeState, onActivate = ::activate, onMessage = ::message)

    // endregion

    // region Prompts (suggestions, automatic switches asking first, new apps)

    val prompts = HomePrompts(container, viewModelScope, prefs, homeState, ::selectTheme, ::message, ::updatePrefs)

    // endregion

    // region Settings

    fun updatePrefs(transform: (LauncherPrefs) -> LauncherPrefs) {
        viewModelScope.launch { settingsRepository.updatePrefs(transform) }
    }

    fun setSwitcherSide(side: SwitcherSide) = updatePrefs { it.copy(switcherSide = side) }

    fun hideApp(app: AppInfo) {
        updatePrefs { it.copy(hiddenApps = it.hiddenApps + app.key) }
        message(res.getString(R.string.hidden_unhide_it_in_launcher_settings, app.label))
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
            ids.getOrNull(firstWithApps.coerceAtLeast(0))?.let { activate(it) }
            settingsRepository.setOnboardingComplete(true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    // endregion

    // region Drawer & search

    /** [withKeyboard]: open for searching; by default the "Open keyboard" setting decides. */
    fun openDrawer(withKeyboard: Boolean = prefs.value.drawerKeyboard) {
        _drawerKeyboard.value = withKeyboard
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

    /** [options] carry the launch animation (see LaunchAnimation). */
    fun launch(app: AppInfo, sourceBounds: Rect?, options: Bundle? = null) = guarded(app) {
        if (appsRepository.launch(app, sourceBounds, options)) {
            appInFront = true
            val state = homeState.value
            val now = System.currentTimeMillis()
            viewModelScope.launch {
                container.database.usageDao().recordLaunch(app.key, now)
                history.recordLaunch(app.key, state.activeTheme?.id, now)
            }
            suggestSwitchFor(app, state)
        } else {
            message(res.getString(R.string.is_not_available, app.label))
        }
    }

    /** Opening an app that lives on exactly one other theme offers to switch there. */
    private fun suggestSwitchFor(app: AppInfo, state: HomeState) {
        if (!prefs.value.suggestSwitching) return
        val active = state.activeTheme ?: return
        if (app.key in state.appKeysOf(active) || state.favorites.any { it.key == app.key }) return
        val home = state.themes.filter { it.id != active.id && app.key in state.appKeysOf(it) }.singleOrNull() ?: return
        prompts.suggest(Suggestion.SwitchForApp(app.key, home.id))
    }

    /** Launches an app or pinned shortcut. Folders are opened by the UI. */
    fun open(item: HomeItem, sourceBounds: Rect?, options: Bundle?) {
        when (item) {
            is AppItem -> launch(item.app, sourceBounds, options)

            is ShortcutItem -> guarded(item.app) {
                if (container.pinnedShortcuts.launch(item.shortcut, sourceBounds, options)) {
                    appInFront = true
                } else {
                    message(res.getString(R.string.couldn_t_open_its_app_may_have_removed_it, item.label))
                }
            }

            is FolderItem -> Unit
        }
    }

    /** The launcher is in front again. */
    fun onResumed() {
        if (appInFront) {
            appInFront = false
            _returnedFromApp.tryEmit(Unit)
        }
    }

    fun openAppInfo(app: AppInfo, sourceBounds: Rect?) = appsRepository.openAppInfo(app, sourceBounds)

    fun uninstall(app: AppInfo) = appsRepository.requestUninstall(app)

    suspend fun shortcutsFor(app: AppInfo): List<AppShortcut> = container.shortcutsRepository.shortcutsFor(app)

    fun launchShortcut(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle? = null) {
        if (container.shortcutsRepository.launch(shortcut, sourceBounds, options)) {
            appInFront = true
        } else {
            message(res.getString(R.string.couldn_t_open, shortcut.label))
        }
    }

    suspend fun pinnedShortcutIcon(shortcut: PinnedShortcut) = container.pinnedShortcuts.icon(shortcut)

    // endregion

    // region Themes

    /** Moves [delta] themes along the switcher's order, wrapping around (the next/previous theme gestures). */
    fun stepTheme(delta: Int) {
        val themes = homeState.value.themes
        if (themes.size < 2) return
        val index = themes.indexOfFirst { it.id == homeState.value.activeTheme?.id }.coerceAtLeast(0)
        val next = themes[Math.floorMod(index + delta, themes.size)]
        selectTheme(next.id)
        message(next.name)
    }

    /** The user picked a theme (it's remembered for schedule suggestions). */
    fun selectTheme(themeId: Long) {
        val changed = themeId != homeState.value.activeTheme?.id
        activate(themeId)
        if (changed) viewModelScope.launch { history.recordManualSwitch(themeId) }
    }

    private fun activate(themeId: Long) {
        selectedThemeId.value = themeId
        viewModelScope.launch { settingsRepository.setActiveThemeId(themeId) }
    }

    /** Creates a theme and makes it active. */
    fun createTheme(draft: ThemeDraft) {
        viewModelScope.launch {
            activate(themeRepository.createTheme(draft))
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
                message(res.getString(R.string.you_need_at_least_one_phase))
                return@launch
            }
            widgets.removeForTheme(themeId)
            themeRepository.deleteTheme(themeId)
            container.pinnedShortcuts.pruneUnused()
            cleanUpWallpapers()
            // If the active theme was deleted, move to its neighbour rather than jumping to the first.
            if (activeThemeId.first() == themeId) {
                val index = themes.indexOfFirst { it.id == themeId }
                val remaining = themes.filter { it.id != themeId }
                remaining.getOrNull((index - 1).coerceAtLeast(0))?.let { activate(it.id) }
            }
        }
    }

    suspend fun importWallpaper(uri: Uri): String? = themeEffects.importWallpaper(uri)

    private suspend fun cleanUpWallpapers() {
        val inUse = themeRepository.themes.first().mapNotNull { it.wallpaperPath }.toSet()
        themeEffects.deleteUnusedWallpapers(inUse)
    }

    private fun refreshAfterRuleChange(draft: ThemeDraft) {
        val rules = draft.rules.map { it.rule }
        if (rules.any { it is ThemeRule.Bluetooth }) autoSwitcher.refreshBluetooth()
        if (rules.any { it is ThemeRule.Calendar }) autoSwitcher.calendar.refresh()
    }

    // endregion

    // region Phase contents

    fun reorderThemes(orderedIds: List<Long>) {
        viewModelScope.launch { themeRepository.reorderThemes(orderedIds) }
    }

    fun addAppToThemes(app: AppInfo, themeIds: Collection<Long>) {
        viewModelScope.launch {
            themeIds.forEach { themeRepository.addApp(it, app.key) }
        }
    }

    fun removeFromActiveTheme(item: HomeItem) {
        val theme = homeState.value.activeTheme ?: return
        viewModelScope.launch {
            themeRepository.removeApp(theme.id, item.key)
            container.pinnedShortcuts.pruneUnused()
        }
    }

    /** Saves where everything on the theme's home grid is, after a move or a widget resize. */
    fun saveHomeLayout(themeId: Long, layout: List<GridPlacement>) {
        viewModelScope.launch { themeRepository.saveLayout(themeId, layout) }
    }

    // endregion

    // region Folders

    private val folders = container.folderRepository

    /** Puts [item] (on the active theme's grid) in a new folder where it was. */
    fun createFolder(item: HomeItem, name: String) {
        val theme = homeState.value.activeTheme ?: return
        viewModelScope.launch { folders.createWith(theme.id, item.key, name) }
    }

    fun moveIntoFolder(item: HomeItem, folder: FolderItem) {
        viewModelScope.launch { folders.moveInto(folder.folder.id, item.key) }
    }

    /** Moves [item] out of [folder] onto the theme's grid. */
    fun moveOutOfFolder(folder: FolderItem, item: HomeItem) {
        viewModelScope.launch { folders.moveOut(folder.folder.id, item.key) }
    }

    /**
     * [dragged] was dropped onto [target]'s middle on the theme's grid: into the folder, or both
     * into a new folder where the target was. [layout] is the grid at the drop; it's saved first so
     * nothing else shifts when the dragged item leaves it.
     */
    fun mergeItems(themeId: Long, layout: List<GridPlacement>, dragged: String, target: String) {
        if (ItemKeys.folderId(dragged) != null) return
        viewModelScope.launch {
            themeRepository.saveLayout(themeId, layout.filter { it.key != dragged })
            val folderId = ItemKeys.folderId(target) ?: folders.createWith(themeId, target, folderNameFor(dragged, target))
            folders.moveInto(folderId, dragged)
        }
    }

    /** An app carried from the drawer was dropped on the active theme's grid. */
    fun dropOnHome(app: AppInfo, drop: HomeDrop?) {
        val state = homeState.value
        val theme = state.activeTheme ?: return
        val inFolder = state.folders.values.firstOrNull { it.themeId == theme.id && app.key in it.itemKeys }
        if (inFolder != null) {
            message(res.getString(R.string.is_already_in_the_folder, app.label, inFolder.name))
            return
        }
        viewModelScope.launch {
            when (drop) {
                is HomeDrop.Into -> if (drop.key != app.key) {
                    val folderId = ItemKeys.folderId(drop.key) ?: folders.createWith(theme.id, drop.key, folderNameFor(app.key, drop.key))
                    folders.moveInto(folderId, app.key)
                }

                is HomeDrop.At -> {
                    // The layout already has the app in the cell it was dropped on.
                    themeRepository.addApp(theme.id, app.key)
                    themeRepository.saveLayout(theme.id, drop.layout)
                }

                null -> if (app.key !in theme.appKeys) themeRepository.addApp(theme.id, app.key)
            }
        }
    }

    /** A new folder is named after the apps' shared Play Store category (e.g. "Games"), else "Folder". */
    private fun folderNameFor(first: String, second: String): String {
        val apps = homeState.value.apps
        val category = listOf(first, second).map { apps[it]?.category ?: -1 }.distinct().singleOrNull()
        return category?.takeIf { it >= 0 }
            ?.let { ApplicationInfo.getCategoryTitle(container.appContext, it)?.toString() }
            ?: FolderRepository.DEFAULT_NAME
    }

    /** Takes [keys] out of whichever of the theme's folders hold them (unchecked in the theme editor). */
    fun removeFromThemeFolders(themeId: Long, keys: Set<String>) {
        val held = homeState.value.folders.values.filter { it.themeId == themeId }
        viewModelScope.launch {
            held.forEach { folder -> folder.itemKeys.filter { it in keys }.forEach { folders.remove(folder.id, it) } }
            container.pinnedShortcuts.pruneUnused()
        }
    }

    fun removeFromFolder(folder: FolderItem, item: HomeItem) {
        viewModelScope.launch {
            folders.remove(folder.folder.id, item.key)
            container.pinnedShortcuts.pruneUnused()
        }
    }

    fun ungroupFolder(folder: FolderItem) {
        viewModelScope.launch { folders.ungroup(folder.folder.id) }
    }

    fun renameFolder(folder: FolderItem, name: String) {
        viewModelScope.launch { folders.rename(folder.folder.id, name) }
    }

    /** [visibleOrder] covers only the items shown; unavailable ones keep their place at the end. */
    fun reorderFolder(folder: FolderItem, visibleOrder: List<String>) {
        viewModelScope.launch { folders.reorder(folder.folder.id, withHidden(visibleOrder, folder.folder.itemKeys)) }
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

    // endregion

    // region Favorites (the dock shown on the active theme)

    private val dockId get() = homeState.value.dockId

    fun addFavorite(app: AppInfo) {
        viewModelScope.launch { reportFavoriteResult(app, favoritesRepository.add(app.key, dockId)) }
    }

    /** Adds apps to the dock in this order, stopping (and saying so) if it fills up. */
    fun addFavorites(apps: List<AppInfo>) {
        val dock = dockId
        viewModelScope.launch {
            for (app in apps) {
                val result = favoritesRepository.add(app.key, dock)
                if (result == AddFavoriteResult.Full) {
                    reportFavoriteResult(app, result)
                    break
                }
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
        AddFavoriteResult.AlreadyFavorite -> message(res.getString(R.string.is_already_in_the_dock, app.label))
        AddFavoriteResult.Full -> message(res.getQuantityString(R.plurals.the_dock_holds_apps_remove_one_first, ThemeDefaults.MAX_FAVORITES, ThemeDefaults.MAX_FAVORITES))
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
