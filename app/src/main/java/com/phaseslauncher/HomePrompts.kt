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
import kotlinx.coroutines.CoroutineScope
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

/**
 * The prompts on the home screen: suggestions (an app for a phase, a schedule, a switch), automatic
 * switches waiting for an OK, and newly installed apps to put on phases.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomePrompts(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    prefs: StateFlow<LauncherPrefs>,
    private val homeState: StateFlow<HomeState>,
    private val onSwitchTo: (themeId: Long) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onUpdatePrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
) {
    private val res = container.appContext.resources
    private val themeRepository = container.themeRepository
    private val favoritesRepository = container.favoritesRepository
    private val settingsRepository = container.settingsRepository
    private val autoSwitcher = container.autoSwitcher
    private val history = container.history

    /** Offers to switch to [suggestion]'s phase once home is back, e.g. after opening an app from another phase. */
    fun suggest(suggestion: Suggestion) {
        liveSuggestion.value = suggestion
    }

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
            .stateIn(scope, SharingStarted.Eagerly, null)

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
        scope.launch { computedSuggestions.collect { lastComputed = it } }
    }

    /** Opens the editor for a theme with a change already made (e.g. a suggested schedule) to fine-tune. */
    private val _editDraft = Channel<Pair<Long, ThemeDraft>>(Channel.BUFFERED)
    val editDraft: Flow<Pair<Long, ThemeDraft>> = _editDraft.receiveAsFlow()

    fun acceptBanner(banner: HomeBanner) {
        if (banner.isAutoPrompt) return autoSwitcher.acceptPrompt()
        val suggestion = currentSuggestion(banner.id) ?: return
        liveSuggestion.value = null
        when (suggestion) {
            is Suggestion.AddApp -> scope.launch {
                themeRepository.addApp(suggestion.themeId, suggestion.appKey)
                onMessage(res.getString(R.string.added_to, homeState.value.themes.firstOrNull { it.id == suggestion.themeId }?.name ?: res.getString(R.string.the_phase)))
            }

            is Suggestion.Schedule -> scope.launch {
                themeRepository.addRule(suggestion.themeId, suggestion.rule)
                onMessage(res.getString(R.string.schedule_added_change_it_in_the_phase_s_settings))
            }

            is Suggestion.SwitchForApp -> onSwitchTo(suggestion.themeId)
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
        val until = if (forever) Long.MAX_VALUE else System.currentTimeMillis() + WEEK_MS
        onUpdatePrefs { it.copy(dismissedSuggestions = it.dismissedSuggestions + (banner.id to until)) }
    }

    fun resetSuggestions() {
        onUpdatePrefs { it.copy(dismissedSuggestions = emptyMap()) }
        onMessage(res.getString(R.string.dismissed_suggestions_can_show_again))
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
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // On the first run only apps installed from now on count as new.
        scope.launch {
            if (settingsRepository.newAppsSeenUntil.first() == null) settingsRepository.markNewAppsSeen(System.currentTimeMillis())
        }
    }

    /** Puts [app] on [themeIds] (none: skipped) and moves on to the next new app. */
    fun answerNewApp(app: AppInfo, themeIds: Collection<Long>) {
        scope.launch {
            themeIds.forEach { themeRepository.addApp(it, app.key) }
            settingsRepository.markNewAppsSeen(app.installTime)
            if (themeIds.isNotEmpty()) {
                val names = homeState.value.themes.filter { it.id in themeIds }.joinToString { it.name }
                onMessage(res.getString(R.string.added_to, names))
            }
        }
    }

    fun clearHistory() {
        scope.launch {
            history.clear()
            onMessage(res.getString(R.string.activity_history_cleared))
        }
    }

    private companion object {
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
