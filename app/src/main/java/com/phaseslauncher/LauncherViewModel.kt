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
import com.phaseslauncher.data.icons.IconPackInfo
import com.phaseslauncher.data.items.AppItem
import com.phaseslauncher.data.items.Folder
import com.phaseslauncher.data.items.FolderItem
import com.phaseslauncher.data.items.FolderRepository
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.items.PinnedShortcut
import com.phaseslauncher.data.items.ShortcutItem
import com.phaseslauncher.data.focus.FocusBlock
import com.phaseslauncher.data.importer.LauncherLayout
import com.phaseslauncher.data.importer.LayoutImportOptions
import com.phaseslauncher.data.rules.RuleText
import com.phaseslauncher.data.rules.ThemeRule
import com.phaseslauncher.data.share.ImportOptions
import com.phaseslauncher.data.share.Resolution
import com.phaseslauncher.data.share.ShareOptions
import com.phaseslauncher.data.share.SharedTheme
import com.phaseslauncher.data.suggestions.Suggestion
import com.phaseslauncher.data.suggestions.SuggestionEngine
import com.phaseslauncher.data.suggestions.SuggestionSettings
import org.json.JSONObject
import com.phaseslauncher.data.search.Calculation
import com.phaseslauncher.data.search.Calculator
import com.phaseslauncher.data.search.ContactResult
import com.phaseslauncher.data.search.SettingsResult
import com.phaseslauncher.data.settings.DrawerSort
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.AddFavoriteResult
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.themes.ThemeDraft
import com.phaseslauncher.data.themes.ThemeIconKey
import com.phaseslauncher.data.themes.toDraft
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.widgets.ThemeWidget
import com.phaseslauncher.ui.components.HomeDrop
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
    val sort: DrawerSort = DrawerSort.Name,
    /** The query worked out as arithmetic or a unit conversion, if it is one. */
    val calculation: Calculation? = null,
) {
    val selectedProfileState: ProfileState? get() = profiles.firstOrNull { it.type == selectedProfile }
}

data class HomeState(
    val isLoading: Boolean = true,
    val themes: List<Theme> = emptyList(),
    val activeTheme: Theme? = null,
    /** The dock being shown: the active theme's own dock, or the shared one. */
    val favorites: List<AppInfo> = emptyList(),
    val dockId: Long = FavoriteEntity.SHARED_DOCK,
    /** Widgets per theme id. */
    val widgets: Map<Long, List<ThemeWidget>> = emptyMap(),
    /** Every launchable app by key (custom names applied). */
    val apps: Map<String, AppInfo> = emptyMap(),
    /** Pinned shortcuts whose app is launchable, by key. */
    val shortcuts: Map<String, ShortcutItem> = emptyMap(),
    val folders: Map<String, Folder> = emptyMap(),
) {
    val canAddFavorite: Boolean get() = favorites.size < ThemeDefaults.MAX_FAVORITES

    /** A theme's apps, shortcuts and folders in order; unavailable ones (and empty folders) are skipped. */
    fun itemsFor(theme: Theme): List<HomeItem> = theme.appKeys.mapNotNull { resolve(it, allowFolder = true) }

    fun foldersFor(theme: Theme): List<FolderItem> = itemsFor(theme).filterIsInstance<FolderItem>()

    /** Themes that contain [appKey], directly or in one of their folders. */
    fun themesContaining(appKey: String): Set<Long> =
        themes.filter { appKey in it.appKeys }.mapTo(HashSet()) { it.id } +
            folders.values.filter { appKey in it.itemKeys }.map { it.themeId }

    /** [theme]'s app keys, including the apps in its folders. */
    fun appKeysOf(theme: Theme): Set<String> =
        theme.appKeys.filter(ItemKeys::isApp).toSet() + folders.values.filter { it.themeId == theme.id }.flatMap { it.itemKeys }

    /** Apps in [theme]'s folders, with their folder's name. */
    fun folderMembers(theme: Theme): Map<String, String> =
        folders.values.filter { it.themeId == theme.id }.flatMap { f -> f.itemKeys.map { it to f.name } }.toMap()

    /** Launchable apps and shortcuts on [theme], counting what's inside its folders. */
    fun itemCount(theme: Theme): Int = itemsFor(theme).sumOf { if (it is FolderItem) it.items.size else 1 }

    private fun resolve(key: String, allowFolder: Boolean): HomeItem? = when {
        ItemKeys.isApp(key) -> apps[key]?.let(::AppItem)
        ItemKeys.shortcutId(key) != null -> shortcuts[key]
        allowFolder -> folders[key]?.let { folder ->
            FolderItem(folder, folder.itemKeys.mapNotNull { resolve(it, allowFolder = false) }).takeIf { it.items.isNotEmpty() }
        }
        else -> null
    }

    fun widgetsFor(theme: Theme): List<ThemeWidget> = widgets[theme.id].orEmpty()
}

/** A prompt on the home screen: an automatic switch waiting for an OK, or a suggestion. */
data class HomeBanner(
    val id: String,
    val themeId: Long,
    val title: String,
    val body: String,
    val acceptLabel: String,
    /** Offers "Customize…" to open the theme editor with the change in place. */
    val canCustomize: Boolean = false,
    /** An automatic switch ("Ask first"), rather than a suggestion. */
    val isAutoPrompt: Boolean = false,
)

/** A launch held back by a theme's focus settings. */
data class PendingLaunch(val block: FocusBlock, val proceed: () -> Unit)

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

    // region Suggestions and automatic-switch prompts

    /** A suggestion made at launch time ("Switch to Gaming?"), shown once home is back. */
    private val liveSuggestion = MutableStateFlow<Suggestion?>(null)

    private val computedSuggestions: Flow<List<Suggestion>> =
        combine(
            themeRepository.themes,
            history.launches(),
            history.switches(),
            prefs.map { SuggestionSettings(it.suggestApps, it.suggestSchedules, it.suggestSwitching, it.suggestionMinLaunches) }
                .distinctUntilChanged(),
            combine(container.folderRepository.folders, favoritesRepository.docks, ::Pair),
        ) { themes, launches, switches, settings, (folders, docks) ->
            val appsOf = { theme: Theme ->
                theme.appKeys.toSet() + folders.values.filter { it.themeId == theme.id }.flatMap { it.itemKeys }
            }
            val dockOf = { theme: Theme -> docks[if (theme.ownDock) theme.id else FavoriteEntity.SHARED_DOCK].orEmpty().toSet() }
            SuggestionEngine.compute(themes, launches, switches, settings, appsOf, dockOf)
        }.flowOn(Dispatchers.Default)

    val homeBanner: StateFlow<HomeBanner?> =
        combine(autoSwitcher.prompt, liveSuggestion, computedSuggestions, prefs, homeState) { prompt, live, computed, prefs, state ->
            val themes = state.themes.associateBy { it.id }
            if (prompt != null) {
                themes[prompt.themeId]?.let { theme ->
                    return@combine HomeBanner(
                        id = "prompt:${theme.id}",
                        themeId = theme.id,
                        title = res.getString(R.string.switch_to_2, theme.name),
                        body = RuleText.reason(res, prompt.rule),
                        acceptLabel = res.getString(R.string.switch_action),
                        isAutoPrompt = true,
                    )
                }
            }
            val now = System.currentTimeMillis()
            val active = state.activeTheme?.id
            (listOfNotNull(live) + computed)
                .filter { (prefs.dismissedSuggestions[it.id] ?: 0L) <= now }
                // A switch suggestion is moot once you're on that theme.
                .filterNot { it is Suggestion.SwitchForApp && it.themeId == active }
                .firstNotNullOfOrNull { bannerFor(it, themes, state.apps) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun bannerFor(suggestion: Suggestion, themes: Map<Long, Theme>, apps: Map<String, AppInfo>): HomeBanner? {
        val theme = themes[suggestion.themeId] ?: return null
        return when (suggestion) {
            is Suggestion.AddApp -> {
                val app = apps[suggestion.appKey] ?: return null
                HomeBanner(
                    id = suggestion.id,
                    themeId = theme.id,
                    title = res.getString(R.string.add_to_2, app.label, theme.name),
                    body = res.getQuantityString(R.plurals.you_opened_it_times_on_in_the_last_two_weeks, suggestion.launches, suggestion.launches, theme.name),
                    acceptLabel = res.getString(R.string.add),
                )
            }
            is Suggestion.Schedule -> HomeBanner(
                id = suggestion.id,
                themeId = theme.id,
                title = res.getString(R.string.switch_to_automatically, theme.name),
                body = res.getQuantityString(R.plurals.you_switch_to_it_around_days_lately_schedule, suggestion.days, RuleText.minute(suggestion.rule.startMinute), suggestion.days, RuleText.describe(res, suggestion.rule)),
                acceptLabel = res.getString(R.string.add_schedule),
                canCustomize = true,
            )
            is Suggestion.SwitchForApp -> {
                val app = apps[suggestion.appKey] ?: return null
                HomeBanner(
                    id = suggestion.id,
                    themeId = theme.id,
                    title = res.getString(R.string.switch_to_2, theme.name),
                    body = res.getString(R.string.is_on, app.label, theme.name),
                    acceptLabel = res.getString(R.string.switch_action),
                )
            }
        }
    }

    private fun currentSuggestion(id: String): Suggestion? =
        liveSuggestion.value?.takeIf { it.id == id } ?: lastComputed.firstOrNull { it.id == id }

    private var lastComputed: List<Suggestion> = emptyList()

    init {
        viewModelScope.launch { computedSuggestions.collect { lastComputed = it } }
    }

    /** Opens the editor for a theme with a change already made (e.g. a suggested schedule) to fine-tune. */
    private val _editDraft = Channel<Pair<Long, ThemeDraft>>(Channel.BUFFERED)
    val editDraft: Flow<Pair<Long, ThemeDraft>> = _editDraft.receiveAsFlow()

    fun acceptBanner(banner: HomeBanner) {
        if (banner.isAutoPrompt) return autoSwitcher.acceptPrompt()
        val suggestion = currentSuggestion(banner.id) ?: return
        liveSuggestion.value = null
        when (suggestion) {
            is Suggestion.AddApp -> viewModelScope.launch {
                themeRepository.addApp(suggestion.themeId, suggestion.appKey)
                message(res.getString(R.string.added_to, homeState.value.themes.firstOrNull { it.id == suggestion.themeId }?.name ?: res.getString(R.string.the_phase)))
            }
            is Suggestion.Schedule -> viewModelScope.launch {
                themeRepository.addRule(suggestion.themeId, suggestion.rule)
                message(res.getString(R.string.schedule_added_change_it_in_the_phase_s_settings))
            }
            is Suggestion.SwitchForApp -> selectTheme(suggestion.themeId)
        }
    }

    fun customizeBanner(banner: HomeBanner) {
        val suggestion = currentSuggestion(banner.id) as? Suggestion.Schedule ?: return
        val theme = homeState.value.themes.firstOrNull { it.id == suggestion.themeId } ?: return
        _editDraft.trySend(theme.id to theme.toDraft().let { it.copy(rules = it.rules + RuleSetting(suggestion.rule)) })
    }

    /** "Not now" hides it for a week; [forever] for good. */
    fun dismissBanner(banner: HomeBanner, forever: Boolean) {
        if (banner.isAutoPrompt) return autoSwitcher.dismissPrompt()
        if (liveSuggestion.value?.id == banner.id) liveSuggestion.value = null
        val until = if (forever) Long.MAX_VALUE else System.currentTimeMillis() + WEEK_MS.toLong()
        updatePrefs { it.copy(dismissedSuggestions = it.dismissedSuggestions + (banner.id to until)) }
    }

    fun resetSuggestions() {
        updatePrefs { it.copy(dismissedSuggestions = emptyMap()) }
        message(res.getString(R.string.dismissed_suggestions_can_show_again))
    }

    /**
     * The earliest app installed since the last one answered, not on any phase yet, to ask which
     * phases it goes on. Personal profile only: work apps can arrive in batches, and the private
     * space keeps its apps out of sight. Worked out from install times, so apps installed while
     * the launcher wasn't running are asked about too.
     */
    val newApp: StateFlow<AppInfo?> =
        combine(homeState, settingsRepository.newAppsSeenUntil, prefs.map { it.askForNewApps }.distinctUntilChanged()) { state, seenUntil, ask ->
            if (!ask || seenUntil == null || state.isLoading || state.themes.isEmpty()) return@combine null
            state.apps.values
                .filter { it.profile == ProfileType.Personal && it.installTime > seenUntil && state.themesContaining(it.key).isEmpty() }
                .minByOrNull { it.installTime }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // On the first run only apps installed from now on count as new.
        viewModelScope.launch {
            if (settingsRepository.newAppsSeenUntil.first() == null) settingsRepository.markNewAppsSeen(System.currentTimeMillis())
        }
    }

    /** Puts [app] on [themeIds] (none: skipped) and moves on to the next new app. */
    fun answerNewApp(app: AppInfo, themeIds: Collection<Long>) {
        viewModelScope.launch {
            themeIds.forEach { themeRepository.addApp(it, app.key) }
            settingsRepository.markNewAppsSeen(app.installTime)
            if (themeIds.isNotEmpty()) {
                val names = homeState.value.themes.filter { it.id in themeIds }.joinToString { it.name }
                message(res.getString(R.string.added_to, names))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            history.clear()
            message(res.getString(R.string.activity_history_cleared))
        }
    }

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

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.backupRepository.exportTo(uri) }
                .onSuccess { message(res.getString(R.string.backed_up, phasesCount(it.themes), favoritesCount(it.favorites))) }
                .onFailure { message(res.getString(R.string.backup_failed, it.message)) }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            // Widget ids belong to this phone's widget host; the restored themes start without widgets.
            widgets.removeAll()
            runCatching { container.backupRepository.importFrom(uri) }
                .onSuccess { message(res.getString(R.string.restored, phasesCount(it.themes), favoritesCount(it.favorites))) }
                .onFailure { message(res.getString(R.string.couldn_t_restore, it.message)) }
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
        liveSuggestion.value = Suggestion.SwitchForApp(app.key, home.id)
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

    // region Sharing and importing

    suspend fun shareJson(theme: Theme, options: ShareOptions, iconPackLabel: String?): JSONObject {
        val state = homeState.value
        val dock = if (theme.ownDock) favoritesRepository.getKeys(theme.id) else emptyList()
        return container.themeSharing.export(
            theme = theme,
            folders = state.folders,
            dock = dock,
            labelOf = { key -> state.apps[key]?.label },
            iconPackLabel = iconPackLabel,
            options = options,
        )
    }

    fun shareLink(json: JSONObject): String = container.themeSharing.toLink(json)

    fun qrCode(text: String, sizePx: Int) = container.themeSharing.qrCode(text, sizePx)

    suspend fun shareFile(json: JSONObject, name: String): Uri = container.themeSharing.writeFile(json, name)

    suspend fun readSharedTheme(uri: Uri): Result<SharedTheme> = runCatching { container.themeSharing.read(uri) }

    fun parseSharedTheme(text: String): Result<SharedTheme> = runCatching { container.themeSharing.parse(text) }

    fun resolveSharedTheme(shared: SharedTheme): Resolution =
        container.themeSharing.resolve(shared, homeState.value.apps.values.toList())

    fun installSharedTheme(shared: SharedTheme, resolution: Resolution, options: ImportOptions) {
        viewModelScope.launch {
            runCatching { container.themeSharing.install(shared, resolution, options) }
                .onSuccess { id ->
                    activate(id)
                    message(res.getString(R.string.added, options.name.ifBlank { shared.name }))
                }
                .onFailure { message(res.getString(R.string.couldn_t_add_the_phase, it.message)) }
        }
    }

    suspend fun readLauncherBackup(uri: Uri): Result<LauncherLayout> =
        runCatching { container.launcherImporter.read(uri, homeState.value.apps.values.toList()) }

    fun importLauncherLayout(layout: LauncherLayout, options: LayoutImportOptions) {
        viewModelScope.launch {
            runCatching { container.launcherImporter.apply(layout, options) }
                .onSuccess { count -> message(res.getQuantityString(R.plurals.imported_phases, count, count)) }
                .onFailure { message(res.getString(R.string.import_failed, it.message)) }
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

    fun removeFromActiveTheme(item: HomeItem) {
        val theme = homeState.value.activeTheme ?: return
        viewModelScope.launch {
            themeRepository.removeApp(theme.id, item.key)
            container.pinnedShortcuts.pruneUnused()
        }
    }

    /** [visibleOrder] covers only the apps on screen; hidden (unavailable) ones keep their place at the end. */
    fun reorderThemeApps(themeId: Long, visibleOrder: List<String>) {
        val theme = homeState.value.themes.firstOrNull { it.id == themeId } ?: return
        viewModelScope.launch { themeRepository.setThemeApps(themeId, withHidden(visibleOrder, theme.appKeys)) }
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
     * into a new folder where the target was. [order] is the grid order at the drop.
     */
    fun mergeItems(themeId: Long, order: List<String>, dragged: String, target: String) {
        val state = homeState.value
        val theme = state.themes.firstOrNull { it.id == themeId } ?: return
        if (ItemKeys.folderId(dragged) != null) return
        viewModelScope.launch {
            themeRepository.setThemeApps(themeId, withHidden(order, theme.appKeys))
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
                    // The index counts the items on screen; place before the one there, keeping hidden ones in place.
                    val visible = state.itemsFor(theme).map { it.key }.filter { it != app.key }
                    val before = visible.getOrNull(drop.index)
                    val keys = theme.appKeys.filter { it != app.key }.toMutableList()
                    val at = before?.let(keys::indexOf)?.takeIf { it >= 0 } ?: keys.size
                    keys.add(at, app.key)
                    themeRepository.setThemeApps(theme.id, keys)
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

    private fun phasesCount(n: Int) = res.getQuantityString(R.plurals.phases_count, n, n)

    private fun favoritesCount(n: Int) = res.getQuantityString(R.plurals.favorites_count, n, n)

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
