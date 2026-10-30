package com.themelauncher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Which screen edge the theme switcher bar sits on. */
enum class SwitcherSide { Left, Right }

enum class IconSize(val dp: Int, val label: String) { Small(48, "Small"), Medium(56, "Medium"), Large(64, "Large") }

/**
 * How the date under the home clock is written. Each is a skeleton that Android turns into the
 * local pattern, so "30/10/2026" vs "10/30/2026" and month names follow the phone's language.
 */
enum class DateFormatOption(val skeleton: String) {
    Full("EEEEMMMMd"), // Friday, October 30
    Short("EEEMMMd"), // Fri, Oct 30
    FullWithYear("EEEEMMMMdy"), // Friday, October 30, 2026
    DayMonth("dMMMM"), // October 30 / 30 October
    Numeric("yMMdd"), // 30/10/2026
}

/** User-adjustable launcher settings. */
data class LauncherPrefs(
    val switcherSide: SwitcherSide = SwitcherSide.Right,
    val gridColumns: Int = DEFAULT_COLUMNS,
    val iconSize: IconSize = IconSize.Medium,
    val showLabels: Boolean = true,
    val notificationDots: Boolean = true,
    val swipeDownForNotifications: Boolean = true,
    /** App keys hidden from the drawer (they can still be in themes and the dock). */
    val hiddenApps: Set<String> = emptySet(),
    /** Package of the icon pack applied to all apps, or null for the apps' own icons. */
    val iconPack: String? = null,
    val doubleTapToLock: Boolean = false,
    /** Show matching contacts in drawer search (needs the contacts permission). */
    val contactSearch: Boolean = true,
    /** The clock at the top of home. Off hides the whole header so apps use the full height. */
    val showClock: Boolean = true,
    /** Date under the clock (only shown with the clock). */
    val showDate: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.Full,
    /** Battery percentage next to the date (only shown with the clock). */
    val showBattery: Boolean = false,
    /** The system status bar while ThemeLauncher is on screen. */
    val showStatusBar: Boolean = true,
) {
    companion object {
        const val DEFAULT_COLUMNS = 4
        val COLUMN_RANGE = 3..6
    }
}

/** Small persisted settings. Survives restarts and reboots. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    /** null until the user has picked a theme (or after it was deleted). */
    val activeThemeId: Flow<Long?> = dataStore.data.map { it[ACTIVE_THEME_ID] }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    val prefs: Flow<LauncherPrefs> = dataStore.data.map { it.toPrefs() }.distinctUntilChanged()

    suspend fun setActiveThemeId(id: Long) {
        dataStore.edit { it[ACTIVE_THEME_ID] = id }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun updatePrefs(transform: (LauncherPrefs) -> LauncherPrefs) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toPrefs())) }
    }

    private fun Preferences.toPrefs() = LauncherPrefs(
        switcherSide = SwitcherSide.entries.firstOrNull { it.name == this[SWITCHER_SIDE] } ?: SwitcherSide.Right,
        gridColumns = (this[GRID_COLUMNS] ?: LauncherPrefs.DEFAULT_COLUMNS).coerceIn(LauncherPrefs.COLUMN_RANGE),
        iconSize = IconSize.entries.firstOrNull { it.name == this[ICON_SIZE] } ?: IconSize.Medium,
        showLabels = this[SHOW_LABELS] ?: true,
        notificationDots = this[NOTIFICATION_DOTS] ?: true,
        swipeDownForNotifications = this[SWIPE_DOWN_NOTIFICATIONS] ?: true,
        hiddenApps = this[HIDDEN_APPS].orEmpty(),
        iconPack = this[ICON_PACK]?.ifBlank { null },
        doubleTapToLock = this[DOUBLE_TAP_LOCK] ?: false,
        contactSearch = this[CONTACT_SEARCH] ?: true,
        showClock = this[SHOW_CLOCK] ?: true,
        showDate = this[SHOW_DATE] ?: true,
        dateFormat = DateFormatOption.entries.firstOrNull { it.name == this[DATE_FORMAT] } ?: DateFormatOption.Full,
        showBattery = this[SHOW_BATTERY] ?: false,
        showStatusBar = this[SHOW_STATUS_BAR] ?: true,
    )

    private fun MutablePreferences.write(prefs: LauncherPrefs) {
        this[SWITCHER_SIDE] = prefs.switcherSide.name
        this[GRID_COLUMNS] = prefs.gridColumns.coerceIn(LauncherPrefs.COLUMN_RANGE)
        this[ICON_SIZE] = prefs.iconSize.name
        this[SHOW_LABELS] = prefs.showLabels
        this[NOTIFICATION_DOTS] = prefs.notificationDots
        this[SWIPE_DOWN_NOTIFICATIONS] = prefs.swipeDownForNotifications
        this[HIDDEN_APPS] = prefs.hiddenApps
        this[ICON_PACK] = prefs.iconPack.orEmpty()
        this[DOUBLE_TAP_LOCK] = prefs.doubleTapToLock
        this[CONTACT_SEARCH] = prefs.contactSearch
        this[SHOW_CLOCK] = prefs.showClock
        this[SHOW_DATE] = prefs.showDate
        this[DATE_FORMAT] = prefs.dateFormat.name
        this[SHOW_BATTERY] = prefs.showBattery
        this[SHOW_STATUS_BAR] = prefs.showStatusBar
    }

    // region Bookkeeping for theme side effects (not user settings)

    /** Wallpaper file last applied by a theme, so it isn't re-applied on every switch. */
    suspend fun lastAppliedWallpaper(): String? = dataStore.data.first()[LAST_WALLPAPER]

    suspend fun setLastAppliedWallpaper(path: String?) {
        dataStore.edit { if (path == null) it.remove(LAST_WALLPAPER) else it[LAST_WALLPAPER] = path }
    }

    /** The user's interruption filter and ringer mode from before a theme changed them. */
    suspend fun savedSound(): Pair<Int, Int>? {
        val prefs = dataStore.data.first()
        val filter = prefs[SAVED_FILTER] ?: return null
        val ringer = prefs[SAVED_RINGER] ?: return null
        return filter to ringer
    }

    suspend fun setSavedSound(sound: Pair<Int, Int>?) {
        dataStore.edit {
            if (sound == null) {
                it.remove(SAVED_FILTER)
                it.remove(SAVED_RINGER)
            } else {
                it[SAVED_FILTER] = sound.first
                it[SAVED_RINGER] = sound.second
            }
        }
    }

    /** The theme to return to when an automatic switch ends, and the theme it switched to. */
    suspend fun autoSwitchState(): Pair<Long, Long>? {
        val prefs = dataStore.data.first()
        val previous = prefs[AUTO_PREVIOUS] ?: return null
        val auto = prefs[AUTO_THEME] ?: return null
        return previous to auto
    }

    suspend fun setAutoSwitchState(state: Pair<Long, Long>?) {
        dataStore.edit {
            if (state == null) {
                it.remove(AUTO_PREVIOUS)
                it.remove(AUTO_THEME)
            } else {
                it[AUTO_PREVIOUS] = state.first
                it[AUTO_THEME] = state.second
            }
        }
    }

    // endregion

    private companion object {
        val ACTIVE_THEME_ID = longPreferencesKey("active_theme_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SWITCHER_SIDE = stringPreferencesKey("switcher_side")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val ICON_SIZE = stringPreferencesKey("icon_size")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val NOTIFICATION_DOTS = booleanPreferencesKey("notification_dots")
        val SWIPE_DOWN_NOTIFICATIONS = booleanPreferencesKey("swipe_down_notifications")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val CONTACT_SEARCH = booleanPreferencesKey("contact_search")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val SHOW_BATTERY = booleanPreferencesKey("show_battery")
        val SHOW_STATUS_BAR = booleanPreferencesKey("show_status_bar")
        val LAST_WALLPAPER = stringPreferencesKey("last_wallpaper")
        val SAVED_FILTER = intPreferencesKey("saved_interruption_filter")
        val SAVED_RINGER = intPreferencesKey("saved_ringer_mode")
        val AUTO_PREVIOUS = longPreferencesKey("auto_previous_theme")
        val AUTO_THEME = longPreferencesKey("auto_theme")
    }
}
