package com.themelauncher.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.themelauncher.data.db.LauncherDatabase
import com.themelauncher.data.db.ThemeEntity
import com.themelauncher.data.db.ThemeRuleEntity
import com.themelauncher.data.settings.DateFormatOption
import com.themelauncher.data.settings.IconSize
import com.themelauncher.data.settings.SettingsRepository
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.SoundProfile
import com.themelauncher.data.themes.ThemeIconKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports themes, the dock and launcher settings to a JSON file, and restores them.
 * Apps are referenced by component name, so a backup restores onto another phone as long as
 * the same apps are installed; missing ones are simply not shown.
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
                    .put("apps", JSONArray(themeDao.getThemeAppKeys(theme.id)))
                    .put("soundProfile", theme.soundProfile)
                    .put("ownDock", theme.ownDock)
                    .put("dock", JSONArray(database.favoritesDao().getFavoriteKeys(theme.id)))
                    .put("rules", JSONArray(database.ruleDao().getForTheme(theme.id).map {
                        JSONObject().put("type", it.type).put("config", it.config)
                    }))
            }))
            .put("favorites", JSONArray(favorites))
            .put("settings", JSONObject()
                .put("switcherSide", prefs.switcherSide.name)
                .put("gridColumns", prefs.gridColumns)
                .put("iconSize", prefs.iconSize.name)
                .put("showLabels", prefs.showLabels)
                .put("notificationDots", prefs.notificationDots)
                .put("swipeDownForNotifications", prefs.swipeDownForNotifications)
                .put("hiddenApps", JSONArray(prefs.hiddenApps.toList()))
                .put("iconPack", prefs.iconPack ?: JSONObject.NULL)
                .put("doubleTapToLock", prefs.doubleTapToLock)
                .put("contactSearch", prefs.contactSearch)
                .put("showClock", prefs.showClock)
                .put("showDate", prefs.showDate)
                .put("dateFormat", prefs.dateFormat.name)
                .put("showBattery", prefs.showBattery)
                .put("showStatusBar", prefs.showStatusBar))

        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
            ?: error("Could not open the file for writing")
        Summary(themes.size, favorites.size)
    }

    /** Replaces all themes, the dock and settings with the backup's. Throws if the file isn't a backup. */
    suspend fun importFrom(uri: Uri): Summary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the file")
        val json = JSONObject(text)
        require(json.optString("format") == FORMAT) { "This isn't a ThemeLauncher backup" }
        require(json.optInt("version") <= VERSION) { "This backup is from a newer version of ThemeLauncher" }

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
                    ),
                    theme.optJSONArray("apps").strings(),
                )
                database.favoritesDao().setFavorites(theme.optJSONArray("dock").strings().take(ThemeDefaults.MAX_FAVORITES), id)
                val rules = theme.optJSONArray("rules")
                if (rules != null) {
                    database.ruleDao().setRules(id, List(rules.length()) { j ->
                        val rule = rules.getJSONObject(j)
                        ThemeRuleEntity(themeId = id, type = rule.getString("type"), config = rule.getString("config"))
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
                    swipeDownForNotifications = s.optBoolean("swipeDownForNotifications", current.swipeDownForNotifications),
                    hiddenApps = s.optJSONArray("hiddenApps").strings().toSet(),
                    iconPack = if (s.isNull("iconPack")) null else s.optString("iconPack").ifBlank { null },
                    doubleTapToLock = s.optBoolean("doubleTapToLock", current.doubleTapToLock),
                    contactSearch = s.optBoolean("contactSearch", current.contactSearch),
                    showClock = s.optBoolean("showClock", current.showClock),
                    showDate = s.optBoolean("showDate", current.showDate),
                    dateFormat = DateFormatOption.entries.firstOrNull { it.name == s.optString("dateFormat") } ?: current.dateFormat,
                    showBattery = s.optBoolean("showBattery", current.showBattery),
                    showStatusBar = s.optBoolean("showStatusBar", current.showStatusBar),
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
        const val FORMAT = "themelauncher-backup"
        const val VERSION = 2
    }
}
