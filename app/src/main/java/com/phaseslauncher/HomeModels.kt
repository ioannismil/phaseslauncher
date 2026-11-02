package com.phaseslauncher

import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.ProfileState
import com.phaseslauncher.data.apps.ProfileType
import com.phaseslauncher.data.db.FavoriteEntity
import com.phaseslauncher.data.focus.FocusBlock
import com.phaseslauncher.data.items.AppItem
import com.phaseslauncher.data.items.Folder
import com.phaseslauncher.data.items.FolderItem
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.items.ShortcutItem
import com.phaseslauncher.data.search.Calculation
import com.phaseslauncher.data.search.ContactResult
import com.phaseslauncher.data.search.SettingsResult
import com.phaseslauncher.data.settings.DrawerSort
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.widgets.ThemeWidget
import kotlinx.coroutines.flow.map

data class DrawerState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    /** Personal plus any work profile / private space; tabs are shown when there's more than one. */
    val profiles: List<ProfileState> = emptyList(),
    val selectedProfile: ProfileType = ProfileType.Personal,
    /** Frequently and recently used apps, shown above the list when not searching. */
    val suggestions: List<AppInfo> = emptyList(),
    val contacts: List<ContactResult> = emptyList(),
    val settingsResults: List<SettingsResult> = emptyList(),
    /** Offer to enable contact search (searching, contact search on, permission not granted). */
    val askContactsPermission: Boolean = false,
    val sort: DrawerSort = DrawerSort.Name,
    /** The query worked out as arithmetic or a unit conversion, if it is one. */
    val calculation: Calculation? = null,
) {
    val selectedProfileState: ProfileState? get() = profiles.firstOrNull { it.type == selectedProfile }
}

data class HomeState(
    val isLoading: Boolean = true,
    val themes: List<Theme> = emptyList(),
    val activeTheme: Theme? = null,
    /** The dock being shown: the active theme's own dock, or the shared one. */
    val favorites: List<AppInfo> = emptyList(),
    val dockId: Long = FavoriteEntity.SHARED_DOCK,
    /** Widgets per theme id. */
    val widgets: Map<Long, List<ThemeWidget>> = emptyMap(),
    /** Every launchable app by key (custom names applied). */
    val apps: Map<String, AppInfo> = emptyMap(),
    /** Pinned shortcuts whose app is launchable, by key. */
    val shortcuts: Map<String, ShortcutItem> = emptyMap(),
    val folders: Map<String, Folder> = emptyMap(),
) {
    val canAddFavorite: Boolean get() = favorites.size < ThemeDefaults.MAX_FAVORITES

    /** A theme's apps, shortcuts and folders in order; unavailable ones (and empty folders) are skipped. */
    fun itemsFor(theme: Theme): List<HomeItem> = theme.appKeys.mapNotNull { resolve(it, allowFolder = true) }

    fun foldersFor(theme: Theme): List<FolderItem> = itemsFor(theme).filterIsInstance<FolderItem>()

    /** Themes that contain [appKey], directly or in one of their folders. */
    fun themesContaining(appKey: String): Set<Long> =
        themes.filter { appKey in it.appKeys }.mapTo(HashSet()) { it.id } +
            folders.values.filter { appKey in it.itemKeys }.map { it.themeId }

    /** [theme]'s app keys, including the apps in its folders. */
    fun appKeysOf(theme: Theme): Set<String> =
        theme.appKeys.filter(ItemKeys::isApp).toSet() + folders.values.filter { it.themeId == theme.id }.flatMap { it.itemKeys }

    /** Apps in [theme]'s folders, with their folder's name. */
    fun folderMembers(theme: Theme): Map<String, String> =
        folders.values.filter { it.themeId == theme.id }.flatMap { f -> f.itemKeys.map { it to f.name } }.toMap()

    /** Launchable apps and shortcuts on [theme], counting what's inside its folders. */
    fun itemCount(theme: Theme): Int = itemsFor(theme).sumOf { if (it is FolderItem) it.items.size else 1 }

    private fun resolve(key: String, allowFolder: Boolean): HomeItem? = when {
        ItemKeys.isApp(key) -> apps[key]?.let(::AppItem)

        ItemKeys.shortcutId(key) != null -> shortcuts[key]

        allowFolder -> folders[key]?.let { folder ->
            FolderItem(folder, folder.itemKeys.mapNotNull { resolve(it, allowFolder = false) }).takeIf { it.items.isNotEmpty() }
        }

        else -> null
    }

    fun widgetsFor(theme: Theme): List<ThemeWidget> = widgets[theme.id].orEmpty()
}

/** A prompt on the home screen: an automatic switch waiting for an OK, or a suggestion. */
data class HomeBanner(
    val id: String,
    val themeId: Long,
    val title: String,
    val body: String,
    val acceptLabel: String,
    /** Offers "Customize…" to open the theme editor with the change in place. */
    val canCustomize: Boolean = false,
    /** An automatic switch ("Ask first"), rather than a suggestion. */
    val isAutoPrompt: Boolean = false,
)

/** A launch held back by a theme's focus settings. */
data class PendingLaunch(val block: FocusBlock, val proceed: () -> Unit)
