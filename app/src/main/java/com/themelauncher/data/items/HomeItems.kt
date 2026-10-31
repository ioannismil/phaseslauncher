package com.themelauncher.data.items

import android.os.UserHandle
import com.themelauncher.data.apps.AppInfo

/**
 * Themes, docks and folders store their contents as string keys. An app's key is its component
 * ([AppInfo.key]); pinned shortcuts and folders use the prefixed keys made here. Package names
 * can't contain ':', so the prefixes never collide with an app.
 */
object ItemKeys {
    private const val SHORTCUT = "shortcut:"
    private const val FOLDER = "folder:"

    fun shortcut(id: Long) = "$SHORTCUT$id"
    fun folder(id: Long) = "$FOLDER$id"

    fun shortcutId(key: String): Long? = if (key.startsWith(SHORTCUT)) key.removePrefix(SHORTCUT).toLongOrNull() else null
    fun folderId(key: String): Long? = if (key.startsWith(FOLDER)) key.removePrefix(FOLDER).toLongOrNull() else null

    /** True for an installed app's key, false for a shortcut or folder. */
    fun isApp(key: String): Boolean = !key.startsWith(SHORTCUT) && !key.startsWith(FOLDER)
}

/** A shortcut another app pinned with "Add to home screen". */
data class PinnedShortcut(
    val id: Long,
    val packageName: String,
    val shortcutId: String,
    val user: UserHandle,
    val label: String,
) {
    val key: String get() = ItemKeys.shortcut(id)
}

data class Folder(val id: Long, val themeId: Long, val name: String, val itemKeys: List<String>) {
    val key: String get() = ItemKeys.folder(id)
}

/** Something on a theme's home grid. */
sealed interface HomeItem {
    val key: String
    val label: String
}

data class AppItem(val app: AppInfo) : HomeItem {
    override val key get() = app.key
    override val label get() = app.label
}

/** [app] is the app the shortcut belongs to: its icon badges the shortcut and "App info" opens it. */
data class ShortcutItem(val shortcut: PinnedShortcut, val app: AppInfo) : HomeItem {
    override val key get() = shortcut.key
    override val label get() = shortcut.label
}

/** [items] are the folder's launchable apps and shortcuts, in order. */
data class FolderItem(val folder: Folder, val items: List<HomeItem>) : HomeItem {
    override val key get() = folder.key
    override val label get() = folder.name
}
