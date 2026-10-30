package com.themelauncher.data.themes

import androidx.room.withTransaction
import com.themelauncher.data.db.LauncherDatabase
import com.themelauncher.data.db.ThemeEntity
import com.themelauncher.data.rules.ThemeRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ThemeRepository(private val database: LauncherDatabase) {

    private val dao = database.themeDao()
    private val ruleDao = database.ruleDao()

    /** All themes in switcher order, each with its ordered app keys and switching rules. */
    val themes: Flow<List<Theme>> =
        combine(dao.observeThemes(), dao.observeAssignments(), ruleDao.observeAll()) { themes, assignments, rules ->
            val appsByTheme = assignments.groupBy({ it.themeId }, { it.appKey })
            val rulesByTheme = rules.filter { it.enabled }.groupBy { it.themeId }
            themes.map { entity ->
                entity.toTheme(
                    appKeys = appsByTheme[entity.id].orEmpty(),
                    rules = rulesByTheme[entity.id].orEmpty().mapNotNull(ThemeRule::fromEntity),
                )
            }
        }

    suspend fun createTheme(draft: ThemeDraft): Long = database.withTransaction {
        val id = dao.insertThemeWithApps(
            ThemeEntity(
                name = draft.name.trim(),
                iconKey = draft.icon.name,
                accentColor = draft.accentColor,
                position = 0,
                wallpaperPath = draft.wallpaperPath,
                soundProfile = draft.soundProfile.name,
                ownDock = draft.ownDock,
            ),
            draft.appKeys,
        )
        ruleDao.setRules(id, draft.rules.map { ThemeRule.toEntity(id, it) })
        id
    }

    suspend fun createTheme(name: String, icon: ThemeIconKey, accentColor: Int, appKeys: List<String> = emptyList()): Long =
        createTheme(ThemeDraft(name, icon, accentColor, appKeys))

    /** Saves everything in [draft], including the theme's apps and rules. */
    suspend fun updateTheme(id: Long, draft: ThemeDraft) = database.withTransaction {
        val current = dao.getThemes().firstOrNull { it.id == id } ?: return@withTransaction
        dao.updateTheme(
            current.copy(
                name = draft.name.trim(),
                iconKey = draft.icon.name,
                accentColor = draft.accentColor,
                wallpaperPath = draft.wallpaperPath,
                soundProfile = draft.soundProfile.name,
                ownDock = draft.ownDock,
            ),
        )
        dao.setApps(id, draft.appKeys)
        ruleDao.setRules(id, draft.rules.map { ThemeRule.toEntity(id, it) })
    }

    /** Refuses to delete the last remaining theme. Returns whether the theme was deleted. */
    suspend fun deleteTheme(id: Long): Boolean {
        if (dao.countThemes() <= 1) return false
        database.withTransaction {
            dao.deleteTheme(id)
            database.favoritesDao().clear(id) // the theme's own dock, if any
        }
        return true
    }

    suspend fun reorderThemes(orderedIds: List<Long>) = dao.reorderThemes(orderedIds)

    /** Replaces (and reorders) the theme's apps. */
    suspend fun setThemeApps(themeId: Long, orderedKeys: List<String>) = dao.setApps(themeId, orderedKeys)

    suspend fun addApp(themeId: Long, appKey: String) = dao.addApp(themeId, appKey)

    suspend fun removeApp(themeId: Long, appKey: String) = dao.removeApp(themeId, appKey)

    private fun ThemeEntity.toTheme(appKeys: List<String>, rules: List<ThemeRule>) = Theme(
        id = id,
        name = name,
        icon = ThemeIconKey.fromName(iconKey),
        accentColor = accentColor,
        appKeys = appKeys,
        wallpaperPath = wallpaperPath,
        soundProfile = SoundProfile.fromName(soundProfile),
        ownDock = ownDock,
        rules = rules,
    )
}
