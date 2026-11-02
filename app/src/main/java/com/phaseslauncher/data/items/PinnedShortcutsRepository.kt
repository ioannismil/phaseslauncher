package com.phaseslauncher.data.items

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.db.PinnedShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Shortcuts pinned by other apps ("Add to home screen" in Chrome, Maps, Contacts…).
 *
 * The system keeps a shortcut only while some launcher has it pinned, so shortcuts that no theme
 * or folder uses any more are unpinned again ([pruneUnused]).
 */
class PinnedShortcutsRepository(context: Context, private val database: LauncherDatabase) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val dao = database.pinnedShortcutDao()
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val iconSizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    /** Every stored shortcut whose profile still exists. */
    val shortcuts: Flow<List<PinnedShortcut>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    /**
     * Pins the shortcut of an "Add to home screen" request and stores it. Returns its item key, or
     * null if the request was cancelled or expired meanwhile.
     */
    suspend fun accept(request: LauncherApps.PinItemRequest): String? = withContext(Dispatchers.IO) {
        val info = request.shortcutInfo ?: return@withContext null
        val accepted = runCatching { request.isValid && request.accept() }
            .onFailure { Log.w(TAG, "Could not accept pin request", it) }
            .getOrDefault(false)
        if (!accepted) return@withContext null
        val serial = userManager.getSerialNumberForUser(info.userHandle)
        val id = dao.find(info.`package`, info.id, serial)?.id
            ?: dao.insert(PinnedShortcutEntity(packageName = info.`package`, shortcutId = info.id, userSerial = serial, label = labelOf(info)))
        ItemKeys.shortcut(id)
    }

    fun labelOf(info: ShortcutInfo): String = (info.shortLabel ?: info.longLabel ?: "").toString()

    suspend fun icon(shortcut: PinnedShortcut): ImageBitmap? = icons[shortcut.key] ?: withContext(Dispatchers.IO) {
        val info = info(shortcut) ?: return@withContext null
        render(info)?.also { icons[shortcut.key] = it }
    }

    fun render(info: ShortcutInfo): ImageBitmap? = runCatching {
        launcherApps.getShortcutIconDrawable(info, densityDpi)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
    }.getOrNull()

    /** Shortcut icons can change when their app updates; there are few, so just reload them all. */
    fun invalidateIcons() = icons.clear()

    fun launch(shortcut: PinnedShortcut, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startShortcut(shortcut.packageName, shortcut.shortcutId, sourceBounds, options, shortcut.user)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not start shortcut ${shortcut.key}", e)
        false
    }

    /** Forgets shortcuts that no theme or folder uses any more, and unpins them. */
    suspend fun pruneUnused() = withContext(Dispatchers.IO) {
        val used = (database.themeDao().getAllAssignedKeys() + database.folderDao().getAllItemKeys()).toSet()
        val all = dao.getAll()
        val unused = all.filter { ItemKeys.shortcut(it.id) !in used }
        if (unused.isEmpty()) return@withContext
        dao.delete(unused.map { it.id })
        unused.forEach { icons.remove(ItemKeys.shortcut(it.id)) }
        val remaining = all - unused.toSet()
        unused.map { it.packageName to it.userSerial }.distinct().forEach { (pkg, serial) ->
            val user = userManager.getUserForSerialNumber(serial) ?: return@forEach
            val keep = remaining.filter { it.packageName == pkg && it.userSerial == serial }.map { it.shortcutId }
            // Replaces the package's pinned set; only allowed while we're the default home app.
            runCatching { launcherApps.pinShortcuts(pkg, keep, user) }
                .onFailure { Log.w(TAG, "Could not unpin shortcuts of $pkg", it) }
        }
    }

    private fun info(shortcut: PinnedShortcut): ShortcutInfo? {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(shortcut.packageName)
            .setShortcutIds(listOf(shortcut.shortcutId))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        return runCatching { launcherApps.getShortcuts(query, shortcut.user) }.getOrNull()?.firstOrNull()
    }

    private fun PinnedShortcutEntity.toModel(): PinnedShortcut? =
        userManager.getUserForSerialNumber(userSerial)?.let { PinnedShortcut(id, packageName, shortcutId, it, label) }

    private companion object {
        const val TAG = "PinnedShortcuts"
        const val ICON_SIZE_DP = 96 // as large as an auto-fitted home icon
    }
}
