package com.themelauncher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Which screen edge the theme switcher bar sits on. */
enum class SwitcherSide { Left, Right }

/** Small persisted settings. Survives restarts and reboots. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    /** null until the user has picked a theme (or after it was deleted). */
    val activeThemeId: Flow<Long?> = dataStore.data.map { it[ACTIVE_THEME_ID] }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    val switcherSide: Flow<SwitcherSide> = dataStore.data.map { prefs ->
        SwitcherSide.entries.firstOrNull { it.name == prefs[SWITCHER_SIDE] } ?: SwitcherSide.Right
    }

    suspend fun setActiveThemeId(id: Long) {
        dataStore.edit { it[ACTIVE_THEME_ID] = id }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setSwitcherSide(side: SwitcherSide) {
        dataStore.edit { it[SWITCHER_SIDE] = side.name }
    }

    private companion object {
        val ACTIVE_THEME_ID = longPreferencesKey("active_theme_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SWITCHER_SIDE = stringPreferencesKey("switcher_side")
    }
}
