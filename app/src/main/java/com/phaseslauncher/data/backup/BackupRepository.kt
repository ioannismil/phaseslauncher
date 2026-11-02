package com.phaseslauncher.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.phaseslauncher.R
import com.phaseslauncher.data.db.FolderEntity
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.db.ThemeEntity
import com.phaseslauncher.data.db.ThemeRuleEntity
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.settings.AppearanceMode
import com.phaseslauncher.data.settings.ClockPosition
import com.phaseslauncher.data.settings.DateFormatOption
import com.phaseslauncher.data.settings.DrawerSort
import com.phaseslauncher.data.settings.GestureAction
import com.phaseslauncher.data.settings.GestureKind
import com.phaseslauncher.data.settings.IconArrangement
import com.phaseslauncher.data.settings.IconSize
import com.phaseslauncher.data.settings.SettingsRepository
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.SoundProfile
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.themes.ThemeIconKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports themes, the dock and launcher settings to a JSON file, and restores them.
 * Apps are referenced by component name, so a backup restores onto another phone as long as
 * the same apps are installed; missing ones are simply not shown. Pinned shortcuts aren't included:
 * only the app that made one can pin it again.
 */
class BackupRepository(
    private val context: Context,
    private val database: LauncherDatabase,
    private val settings: SettingsRepository,
) {
    data class Summary(val themes: Int, val favorites: Int)

    suspend fun exportTo(uri: Uri): Summary = withContext(Dispatchers.IO) {
        val themeDao = database.themeDao()
        val themes = themeDao.getThemes()
        val favorites = database.favoritesDao().getFavoriteKeys()
        val prefs = settings.prefs.first()

        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("themes", JSONArray(themes.map { theme ->
                JSONObject()
                    .put("name", theme.name)
                    .put("icon", theme.iconKey)
                    .put("color", theme.accentColor)
                    .put("apps", JSONArray(themeDao.getThemeAppKeys(theme.id).filter { ItemKeys.shortcutId(it) == null }))
                    .put("folders", JSONArray(database.folderDao().getForTheme(theme.id).map { folder ->
                        JSONObject()
                            .put("key", ItemKeys.folder(folder.id))
                            .put("name", folder.name)
                            .put("apps", JSONArray(database.folderDao().getItemKeys(folder.id).filter(ItemKeys::isApp)))
                    }))
                    .put("soundProfile", theme.soundProfile)
                    .put("ownDock", theme.ownDock)
                    .put("iconPack", theme.iconPack ?: JSONObject.NULL)
                    .put("gridLayout", theme.gridLayout ?: JSONObject.NULL)
                    .put("dock", JSONArray(database.favoritesDao().getFavoriteKeys(theme.id)))
                    .put("automation", theme.automation)
                    .put("focus", theme.focus)
                    .put("rules", JSONArray(database.ruleDao().getForThemeOrdered(theme.id).map {
                        JSONObject().put("type", it.type).put("config", it.config).put("enabled", it.enabled)
                    }))
            }))
            .put("favorites", JSONArray(favorites))
            .put("settings", JSONObject()
                .put("switcherSide", prefs.switcherSide.name)
                .put("gridColumns", prefs.gridColumns)
                .put("iconSize", prefs.iconSize.name)
                .put("showLabels", prefs.showLabels)
                .put("notificationDots", prefs.notificationDots)
                .put("swipeUp", prefs.swipeUp.serialize())
                .put("swipeDown", prefs.swipeDown.serialize())
                .put("doubleTap", prefs.doubleTap.serialize())
                .put("drawerSort", prefs.drawerSort.name)
                .put("drawerKeyboard", prefs.drawerKeyboard)
                .put("hiddenApps", JSONArray(prefs.hiddenApps.toList()))
                .put("iconPack", prefs.iconPack ?: JSONObject.NULL)
                .put("themedIcons", prefs.themedIcons)
                .put("autoFitIcons", prefs.autoFitIcons)
                .put("iconArrangement", prefs.iconArrangement.name)
                .put("contactSearch", prefs.contactSearch)
                .put("showTime", prefs.showTime)
                .put("clockPosition", prefs.clockPosition.name)
                .put("showDate", prefs.showDate)
                .put("dateFormat", prefs.dateFormat.name)
                .put("showBattery", prefs.showBattery)
                .put("showStatusBar", prefs.showStatusBar)
                .put("showThemeLabel", prefs.showThemeLabel)
                .put("appearance", prefs.appearance.name)
                .put("suggestApps", prefs.suggestApps)
                .put("suggestSchedules", prefs.suggestSchedules)
                .put("suggestSwitching", prefs.suggestSwitching)
                .put("suggestionMinLaunches", prefs.suggestionMinLaunches))

        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
            ?: error(context.getString(R.string.could_not_open_the_file_for_writing))
        Summary(themes.size, favorites.size)
    }

    /** Replaces all themes, the dock and settings with the backup's. Throws if the file isn't a backup. */
    suspend fun importFrom(uri: Uri): Summary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error(context.getString(R.string.could_not_open_the_file))
        val json = JSONObject(text)
        require(json.optString("format") == FORMAT) { context.getString(R.string.this_isn_t_a_phases_backup) }
        require(json.optInt("version") <= VERSION) { context.getString(R.string.this_backup_is_from_a_newer_version_of_phases) }

        val themes = json.getJSONArray("themes")
        val favorites = json.optJSONArray("favorites").strings()

        database.withTransaction {
            val themeDao = database.themeDao()
            themeDao.deleteAllThemes()
            database.favoritesDao().clearAll()
            for (i in 0 until themes.length()) {
                val theme = themes.getJSONObject(i)
                val id = themeDao.insertThemeWithApps(
                    ThemeEntity(
                        name = theme.getString("name"),
                        iconKey = ThemeIconKey.fromName(theme.optString("icon")).name,
                        accentColor = theme.getInt("color"),
                        position = 0,
                        // Wallpapers are files on the old phone, so they aren't part of a backup.
                        soundProfile = SoundProfile.fromName(theme.optString("soundProfile")).name,
                        ownDock = theme.optBoolean("ownDock"),
                        iconPack = if (theme.isNull("iconPack")) null else theme.optString("iconPack"),
                        gridLayout = if (theme.isNull("gridLayout")) null else theme.optString("gridLayout").ifBlank { null },
                        automation = theme.optString("automation"),
                        focus = theme.optString("focus"),
                    ),
                    emptyList(),
                )
                // Folder ids are new on this install; map the backup's folder keys to them.
                val folderKeys = HashMap<String, String>()
                theme.optJSONArray("folders")?.let { folders ->
                    for (j in 0 until folders.length()) {
                        val folder = folders.getJSONObject(j)
                        val folderId = database.folderDao().insert(FolderEntity(themeId = id, name = folder.optString("name")))
                        database.folderDao().setItems(folderId, folder.optJSONArray("apps").strings())
                        folderKeys[folder.getString("key")] = ItemKeys.folder(folderId)
                    }
                }
                themeDao.setApps(id, theme.optJSONArray("apps").strings().mapNotNull { key -> if (ItemKeys.isApp(key)) key else folderKeys[key] })
                database.favoritesDao().setFavorites(theme.optJSONArray("dock").strings().take(ThemeDefaults.MAX_FAVORITES), id)
                val rules = theme.optJSONArray("rules")
                if (rules != null) {
                    database.ruleDao().setRules(id, List(rules.length()) { j ->
                        val rule = rules.getJSONObject(j)
                        ThemeRuleEntity(
                            themeId = id,
                            type = rule.getString("type"),
                            config = rule.getString("config"),
                            enabled = rule.optBoolean("enabled", true),
                        )
                    })
                }
            }
            database.favoritesDao().setFavorites(favorites.take(ThemeDefaults.MAX_FAVORITES))
        }
        themeDaoFirstId()?.let { settings.setActiveThemeId(it) }

        json.optJSONObject("settings")?.let { s ->
            settings.updatePrefs { current ->
                current.copy(
                    switcherSide = SwitcherSide.entries.firstOrNull { it.name == s.optString("switcherSide") } ?: current.switcherSide,
                    gridColumns = s.optInt("gridColumns", current.gridColumns),
                    iconSize = IconSize.entries.firstOrNull { it.name == s.optString("iconSize") } ?: current.iconSize,
                    showLabels = s.optBoolean("showLabels", current.showLabels),
                    notificationDots = s.optBoolean("notificationDots", current.notificationDots),
                    // Older backups had switches for these two gestures.
                    swipeUp = GestureAction.parse(s.optString("swipeUp")) ?: current.swipeUp,
                    swipeDown = GestureAction.parse(s.optString("swipeDown"))
                        ?: if (s.has("swipeDownForNotifications")) {
                            GestureAction(if (s.optBoolean("swipeDownForNotifications")) GestureKind.Notifications else GestureKind.None)
                        } else {
                            current.swipeDown
                        },
                    doubleTap = GestureAction.parse(s.optString("doubleTap"))
                        ?: if (s.has("doubleTapToLock")) {
                            GestureAction(if (s.optBoolean("doubleTapToLock")) GestureKind.Lock else GestureKind.None)
                        } else {
                            current.doubleTap
                        },
                    drawerSort = DrawerSort.entries.firstOrNull { it.name == s.optString("drawerSort") } ?: current.drawerSort,
                    drawerKeyboard = s.optBoolean("drawerKeyboard", current.drawerKeyboard),
                    hiddenApps = s.optJSONArray("hiddenApps").strings().toSet(),
                    iconPack = if (s.isNull("iconPack")) null else s.optString("iconPack").ifBlank { null },
                    themedIcons = s.optBoolean("themedIcons", current.themedIcons),
                    autoFitIcons = s.optBoolean("autoFitIcons", current.autoFitIcons),
                    iconArrangement = IconArrangement.entries.firstOrNull { it.name == s.optString("iconArrangement") } ?: current.iconArrangement,
                    contactSearch = s.optBoolean("contactSearch", current.contactSearch),
                    showTime = s.optBoolean("showTime", s.optBoolean("showClock", current.showTime)),
                    clockPosition = ClockPosition.entries.firstOrNull { it.name == s.optString("clockPosition") } ?: current.clockPosition,
                    showDate = s.optBoolean("showDate", current.showDate),
                    dateFormat = DateFormatOption.entries.firstOrNull { it.name == s.optString("dateFormat") } ?: current.dateFormat,
                    showBattery = s.optBoolean("showBattery", current.showBattery),
                    showStatusBar = s.optBoolean("showStatusBar", current.showStatusBar),
                    showThemeLabel = s.optBoolean("showThemeLabel", current.showThemeLabel),
                    appearance = AppearanceMode.entries.firstOrNull { it.name == s.optString("appearance") } ?: current.appearance,
                    suggestApps = s.optBoolean("suggestApps", current.suggestApps),
                    suggestSchedules = s.optBoolean("suggestSchedules", current.suggestSchedules),
                    suggestSwitching = s.optBoolean("suggestSwitching", current.suggestSwitching),
                    suggestionMinLaunches = s.optInt("suggestionMinLaunches", current.suggestionMinLaunches),
                )
            }
        }
        settings.setOnboardingComplete(true)
        Summary(themes.length(), favorites.size)
    }

    private suspend fun themeDaoFirstId(): Long? = database.themeDao().getThemes().firstOrNull()?.id

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }

    private companion object {
        const val FORMAT = "phases-backup"
        const val VERSION = 4 // 3: folders; 4: automation, focus, rules that are off
    }
}
