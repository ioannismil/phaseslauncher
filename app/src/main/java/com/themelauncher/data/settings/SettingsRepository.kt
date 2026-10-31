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
/** Where the time/date/battery block sits on the home screen. */
enum class ClockPosition(val label: String) { Left("Left"), Center("Center"), Right("Right") }

/**
 * Where a theme's first app sits on home and which way the rest follow. Rows fill sideways from
 * that corner; bottom arrangements stack rows upward, so the first apps are nearest the thumb.
 */
enum class IconArrangement(val label: String, val fromBottom: Boolean, val rightToLeft: Boolean) {
    TopLeft("Top left, left to right", fromBottom = false, rightToLeft = false),
    TopRight("Top right, right to left", fromBottom = false, rightToLeft = true),
    BottomLeft("Bottom left, left to right", fromBottom = true, rightToLeft = false),
    BottomRight("Bottom right, right to left", fromBottom = true, rightToLeft = true),
}

enum class DateFormatOption(val skeleton: String) {
    Full("EEEEMMMMd"), // Friday, October 30
    Short("EEEMMMd"), // Fri, Oct 30
    FullWithYear("EEEEMMMMdy"), // Friday, October 30, 2026
    DayMonth("dMMMM"), // October 30 / 30 October
    Numeric("yMMdd"), // 30/10/2026
}

/** Light or dark colors for the launcher's own screens (drawer, settings, dialogs). */
enum class AppearanceMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/** How the drawer orders apps when not searching. */
enum class DrawerSort(val label: String) {
    Name("Name"),
    MostUsed("Most used"),
    Newest("Recently installed"),
}

/** What a home-screen gesture does. */
enum class GestureKind(val label: String) {
    None("Do nothing"),
    Drawer("Open the app drawer"),
    Search("Search"),
    Notifications("Notifications"),
    QuickSettings("Quick settings"),
    Lock("Lock the screen"),
    ThemeSwitcher("Theme switcher"),
    NextTheme("Next theme"),
    PreviousTheme("Previous theme"),
    OpenApp("Open an app"),
}

/** A gesture's action; [appKey] is the app for [GestureKind.OpenApp]. */
data class GestureAction(val kind: GestureKind, val appKey: String? = null) {
    fun serialize(): String = if (kind == GestureKind.OpenApp && appKey != null) "${kind.name}:$appKey" else kind.name

    companion object {
        fun parse(value: String?): GestureAction? {
            if (value.isNullOrBlank()) return null
            val kind = GestureKind.entries.firstOrNull { it.name == value.substringBefore(':') } ?: return null
            val app = value.substringAfter(':', missingDelimiterValue = "").ifBlank { null }
            return if (kind == GestureKind.OpenApp && app == null) null else GestureAction(kind, app)
        }
    }
}

/** User-adjustable launcher settings. */
data class LauncherPrefs(
    val switcherSide: SwitcherSide = SwitcherSide.Right,
    val gridColumns: Int = DEFAULT_COLUMNS,
    val iconSize: IconSize = IconSize.Medium,
    val showLabels: Boolean = true,
    val notificationDots: Boolean = true,
    /** App keys hidden from the drawer (they can still be in themes and the dock). */
    val hiddenApps: Set<String> = emptySet(),
    /** Package of the icon pack applied to all apps, or null for the apps' own icons. */
    val iconPack: String? = null,
    /** Monochrome app icons tinted with the active theme's color (Android 13+, apps that provide one). */
    val themedIcons: Boolean = false,
    /**
     * Home grid columns and icon size are picked so all of a theme's apps fit without scrolling;
     * [gridColumns] and [iconSize] then only apply to the dock.
     */
    val autoFitIcons: Boolean = false,
    val iconArrangement: IconArrangement = IconArrangement.TopLeft,
    val swipeUp: GestureAction = GestureAction(GestureKind.Drawer),
    val swipeDown: GestureAction = GestureAction(GestureKind.Notifications),
    val doubleTap: GestureAction = GestureAction(GestureKind.None),
    val drawerSort: DrawerSort = DrawerSort.Name,
    /** Open the keyboard (search) whenever the drawer opens. */
    val drawerKeyboard: Boolean = false,
    /** Show matching contacts in drawer search (needs the contacts permission). */
    val contactSearch: Boolean = true,
    /**
     * The time, date and battery at the top of home are each optional. With all three off the
     * header disappears and apps use the full height.
     */
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.Full,
    val showBattery: Boolean = false,
    val clockPosition: ClockPosition = ClockPosition.Left,
    /** The pill with the active theme's name under the clock (tap opens the theme arc). */
    val showThemeLabel: Boolean = true,
    /** The system status bar while ThemeLauncher is on screen. */
    val showStatusBar: Boolean = true,
    val appearance: AppearanceMode = AppearanceMode.System,
    /** Suggest adding apps you keep opening on a theme to that theme. */
    val suggestApps: Boolean = true,
    /** Suggest a schedule for a theme you keep switching to around the same time. */
    val suggestSchedules: Boolean = true,
    /** Offer to switch to an app's theme when it's opened from another theme. */
    val suggestSwitching: Boolean = true,
    /** Launches on a theme before an app is suggested for it. */
    val suggestionMinLaunches: Int = 5,
    /** Dismissed suggestion ids and until when (ms since epoch; [Long.MAX_VALUE] for good). */
    val dismissedSuggestions: Map<String, Long> = emptyMap(),
) {
    companion object {
        const val DEFAULT_COLUMNS = 4
        val COLUMN_RANGE = 3..6

        /** The largest icon "Fit icons to the screen" uses; icons are rendered at this size then. */
        const val AUTO_FIT_MAX_ICON_DP = 88

        val SUGGESTION_LAUNCH_CHOICES = listOf(3, 5, 8, 12, 20)
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
        // The swipe-down and double-tap switches of earlier versions become their gestures' defaults.
        swipeUp = GestureAction.parse(this[GESTURE_SWIPE_UP]) ?: GestureAction(GestureKind.Drawer),
        swipeDown = GestureAction.parse(this[GESTURE_SWIPE_DOWN])
            ?: GestureAction(if (this[SWIPE_DOWN_NOTIFICATIONS] != false) GestureKind.Notifications else GestureKind.None),
        doubleTap = GestureAction.parse(this[GESTURE_DOUBLE_TAP])
            ?: GestureAction(if (this[DOUBLE_TAP_LOCK] == true) GestureKind.Lock else GestureKind.None),
        drawerSort = DrawerSort.entries.firstOrNull { it.name == this[DRAWER_SORT] } ?: DrawerSort.Name,
        drawerKeyboard = this[DRAWER_KEYBOARD] ?: false,
        hiddenApps = this[HIDDEN_APPS].orEmpty(),
        iconPack = this[ICON_PACK]?.ifBlank { null },
        themedIcons = this[THEMED_ICONS] ?: false,
        autoFitIcons = this[AUTO_FIT_ICONS] ?: false,
        iconArrangement = IconArrangement.entries.firstOrNull { it.name == this[ICON_ARRANGEMENT] } ?: IconArrangement.TopLeft,
        contactSearch = this[CONTACT_SEARCH] ?: true,
        showTime = this[SHOW_CLOCK] ?: true,
        showDate = this[SHOW_DATE] ?: true,
        dateFormat = DateFormatOption.entries.firstOrNull { it.name == this[DATE_FORMAT] } ?: DateFormatOption.Full,
        showBattery = this[SHOW_BATTERY] ?: false,
        clockPosition = ClockPosition.entries.firstOrNull { it.name == this[CLOCK_POSITION] } ?: ClockPosition.Left,
        showStatusBar = this[SHOW_STATUS_BAR] ?: true,
        showThemeLabel = this[SHOW_THEME_LABEL] ?: true,
        appearance = AppearanceMode.entries.firstOrNull { it.name == this[APPEARANCE] } ?: AppearanceMode.System,
        suggestApps = this[SUGGEST_APPS] ?: true,
        suggestSchedules = this[SUGGEST_SCHEDULES] ?: true,
        suggestSwitching = this[SUGGEST_SWITCHING] ?: true,
        suggestionMinLaunches = this[SUGGESTION_MIN_LAUNCHES] ?: 5,
        dismissedSuggestions = this[DISMISSED_SUGGESTIONS].orEmpty().mapNotNull { entry ->
            val until = entry.substringAfterLast('|').toLongOrNull() ?: return@mapNotNull null
            entry.substringBeforeLast('|') to until
        }.toMap(),
    )

    private fun MutablePreferences.write(prefs: LauncherPrefs) {
        this[SWITCHER_SIDE] = prefs.switcherSide.name
        this[GRID_COLUMNS] = prefs.gridColumns.coerceIn(LauncherPrefs.COLUMN_RANGE)
        this[ICON_SIZE] = prefs.iconSize.name
        this[SHOW_LABELS] = prefs.showLabels
        this[NOTIFICATION_DOTS] = prefs.notificationDots
        this[GESTURE_SWIPE_UP] = prefs.swipeUp.serialize()
        this[GESTURE_SWIPE_DOWN] = prefs.swipeDown.serialize()
        this[GESTURE_DOUBLE_TAP] = prefs.doubleTap.serialize()
        this[DRAWER_SORT] = prefs.drawerSort.name
        this[DRAWER_KEYBOARD] = prefs.drawerKeyboard
        this[HIDDEN_APPS] = prefs.hiddenApps
        this[ICON_PACK] = prefs.iconPack.orEmpty()
        this[THEMED_ICONS] = prefs.themedIcons
        this[AUTO_FIT_ICONS] = prefs.autoFitIcons
        this[ICON_ARRANGEMENT] = prefs.iconArrangement.name
        this[CONTACT_SEARCH] = prefs.contactSearch
        this[SHOW_CLOCK] = prefs.showTime
        this[SHOW_DATE] = prefs.showDate
        this[DATE_FORMAT] = prefs.dateFormat.name
        this[SHOW_BATTERY] = prefs.showBattery
        this[CLOCK_POSITION] = prefs.clockPosition.name
        this[SHOW_STATUS_BAR] = prefs.showStatusBar
        this[SHOW_THEME_LABEL] = prefs.showThemeLabel
        this[APPEARANCE] = prefs.appearance.name
        this[SUGGEST_APPS] = prefs.suggestApps
        this[SUGGEST_SCHEDULES] = prefs.suggestSchedules
        this[SUGGEST_SWITCHING] = prefs.suggestSwitching
        this[SUGGESTION_MIN_LAUNCHES] = prefs.suggestionMinLaunches
        // Expired dismissals are dropped.
        val now = System.currentTimeMillis()
        this[DISMISSED_SUGGESTIONS] = prefs.dismissedSuggestions.filterValues { it > now }.mapTo(HashSet()) { (id, until) -> "$id|$until" }
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
        val SWIPE_DOWN_NOTIFICATIONS = booleanPreferencesKey("swipe_down_notifications") // read for migration only
        val GESTURE_SWIPE_UP = stringPreferencesKey("gesture_swipe_up")
        val GESTURE_SWIPE_DOWN = stringPreferencesKey("gesture_swipe_down")
        val GESTURE_DOUBLE_TAP = stringPreferencesKey("gesture_double_tap")
        val DRAWER_SORT = stringPreferencesKey("drawer_sort")
        val DRAWER_KEYBOARD = booleanPreferencesKey("drawer_keyboard")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val AUTO_FIT_ICONS = booleanPreferencesKey("auto_fit_icons")
        val ICON_ARRANGEMENT = stringPreferencesKey("icon_arrangement")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock") // read for migration only
        val CONTACT_SEARCH = booleanPreferencesKey("contact_search")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock") // the time; key kept for existing installs
        val CLOCK_POSITION = stringPreferencesKey("clock_position")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val SHOW_BATTERY = booleanPreferencesKey("show_battery")
        val SHOW_STATUS_BAR = booleanPreferencesKey("show_status_bar")
        val SHOW_THEME_LABEL = booleanPreferencesKey("show_theme_label")
        val APPEARANCE = stringPreferencesKey("appearance")
        val SUGGEST_APPS = booleanPreferencesKey("suggest_apps")
        val SUGGEST_SCHEDULES = booleanPreferencesKey("suggest_schedules")
        val SUGGEST_SWITCHING = booleanPreferencesKey("suggest_switching")
        val SUGGESTION_MIN_LAUNCHES = intPreferencesKey("suggestion_min_launches")
        val DISMISSED_SUGGESTIONS = stringSetPreferencesKey("dismissed_suggestions")
        val LAST_WALLPAPER = stringPreferencesKey("last_wallpaper")
        val SAVED_FILTER = intPreferencesKey("saved_interruption_filter")
        val SAVED_RINGER = intPreferencesKey("saved_ringer_mode")
        val AUTO_PREVIOUS = longPreferencesKey("auto_previous_theme")
        val AUTO_THEME = longPreferencesKey("auto_theme")
    }
}
