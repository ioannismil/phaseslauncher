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
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Which screen edge the theme switcher bar sits on. */
enum class SwitcherSide { Left, Right }

enum class IconSize(val dp: Int, val label: String) { Small(48, "Small"), Medium(56, "Medium"), Large(64, "Large") }

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
    )

    private fun MutablePreferences.write(prefs: LauncherPrefs) {
        this[SWITCHER_SIDE] = prefs.switcherSide.name
        this[GRID_COLUMNS] = prefs.gridColumns.coerceIn(LauncherPrefs.COLUMN_RANGE)
        this[ICON_SIZE] = prefs.iconSize.name
        this[SHOW_LABELS] = prefs.showLabels
        this[NOTIFICATION_DOTS] = prefs.notificationDots
        this[SWIPE_DOWN_NOTIFICATIONS] = prefs.swipeDownForNotifications
        this[HIDDEN_APPS] = prefs.hiddenApps
    }

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
    }
}
