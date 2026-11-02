package com.phaseslauncher.data.themes

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.phaseslauncher.data.settings.SettingsRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Applies the active theme's wallpaper and sound profile whenever the active theme changes.
 *
 * Wallpaper: themes without one keep whatever is set. Sound: before the first change the user's
 * own ringer and Do Not Disturb settings are saved, and they're restored when switching to a
 * theme that doesn't change sound.
 */
class ThemeEffects(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun start(scope: CoroutineScope, activeTheme: Flow<Theme?>) {
        scope.launch(Dispatchers.IO) {
            activeTheme.map { it?.wallpaperPath }.distinctUntilChanged().collect { path -> applyWallpaper(path) }
        }
        scope.launch(Dispatchers.IO) {
            activeTheme.map { it?.soundProfile ?: SoundProfile.Unchanged }.distinctUntilChanged().collect { applySound(it) }
        }
    }

    fun hasPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun openPolicyAccessSettings(from: Context) {
        try {
            from.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Copies a picked image into app storage so it survives the original being moved or deleted. */
    suspend fun importWallpaper(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.img")
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }.onFailure { Log.w(TAG, "Could not import wallpaper", it) }.getOrNull()
    }

    /** Deletes wallpaper files no theme uses any more. */
    suspend fun deleteUnusedWallpapers(inUse: Set<String>) = withContext(Dispatchers.IO) {
        File(context.filesDir, "wallpapers").listFiles()?.filter { it.absolutePath !in inUse }?.forEach { it.delete() }
    }

    private suspend fun applyWallpaper(path: String?) {
        if (path == null || path == settings.lastAppliedWallpaper()) return
        val file = File(path)
        if (!file.exists()) return
        runCatching {
            file.inputStream().use { WallpaperManager.getInstance(context).setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
            settings.setLastAppliedWallpaper(path)
        }.onFailure { Log.w(TAG, "Could not set wallpaper", it) }
    }

    private suspend fun applySound(profile: SoundProfile) {
        runCatching {
            if (profile == SoundProfile.Unchanged) {
                settings.savedSound()?.let { (filter, ringer) ->
                    setFilter(filter)
                    audioManager.ringerMode = ringer
                    settings.setSavedSound(null)
                }
                return
            }
            if (profile.needsPolicyAccess && !hasPolicyAccess()) return
            if (settings.savedSound() == null) {
                settings.setSavedSound(notificationManager.currentInterruptionFilter to audioManager.ringerMode)
            }
            when (profile) {
                SoundProfile.Normal -> {
                    setFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }

                SoundProfile.Vibrate -> {
                    setFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }

                SoundProfile.Silent -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT

                SoundProfile.DndPriority -> setFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

                SoundProfile.DndAlarms -> setFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)

                SoundProfile.Unchanged -> Unit
            }
        }.onFailure { Log.w(TAG, "Could not apply sound profile $profile", it) }
    }

    /** Changing Do Not Disturb needs the user's grant; without it, leave DND alone. */
    private fun setFilter(filter: Int) {
        if (hasPolicyAccess() && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            notificationManager.setInterruptionFilter(filter)
        }
    }

    private companion object {
        const val TAG = "ThemeEffects"
    }
}
