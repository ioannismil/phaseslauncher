package com.themelauncher.data.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** An app shortcut ("New message", "Navigate home") shown in the app's long-press menu. */
data class AppShortcut(val id: String, val label: String, val icon: ImageBitmap?, internal val info: ShortcutInfo)

/**
 * Reads app shortcuts through [LauncherApps]. Only the default home app is allowed to, so this
 * returns nothing while ThemeLauncher isn't the default.
 */
class ShortcutsRepository(context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val iconSizePx = (24 * context.resources.displayMetrics.density).roundToInt()

    suspend fun shortcutsFor(app: AppInfo): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.componentName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        val shortcuts = runCatching { launcherApps.getShortcuts(query, app.user) }
            .onFailure { Log.w(TAG, "Could not load shortcuts for ${app.key}", it) }
            .getOrNull().orEmpty()

        // Like the stock launcher: manifest (static) shortcuts first, each group by rank.
        shortcuts
            .filter { it.isEnabled && !it.shortLabel.isNullOrBlank() }
            .sortedWith(compareBy<ShortcutInfo> { !it.isDeclaredInManifest }.thenBy { it.rank })
            .take(MAX_SHORTCUTS)
            .map { info ->
                AppShortcut(
                    id = info.id,
                    label = (info.shortLabel ?: info.longLabel).toString(),
                    icon = runCatching {
                        launcherApps.getShortcutIconDrawable(info, densityDpi)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
                    }.getOrNull(),
                    info = info,
                )
            }
    }

    fun launch(shortcut: AppShortcut, sourceBounds: Rect?): Boolean = try {
        launcherApps.startShortcut(shortcut.info, sourceBounds, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not start shortcut ${shortcut.id}", e)
        false
    }

    private companion object {
        const val TAG = "ShortcutsRepository"
        const val MAX_SHORTCUTS = 4
    }
}
