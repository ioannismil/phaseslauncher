package com.themelauncher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small persisted settings. Survives restarts and reboots. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    /** null until the user has picked a theme (or after it was deleted). */
    val activeThemeId: Flow<Long?> = dataStore.data.map { it[ACTIVE_THEME_ID] }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    suspend fun setActiveThemeId(id: Long) {
        dataStore.edit { it[ACTIVE_THEME_ID] = id }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    private companion object {
        val ACTIVE_THEME_ID = longPreferencesKey("active_theme_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
