package com.themelauncher.data.themes

import com.themelauncher.data.db.ThemeDao
import com.themelauncher.data.db.ThemeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ThemeRepository(private val dao: ThemeDao) {

    /** All themes in switcher order, each with its ordered app keys. */
    val themes: Flow<List<Theme>> =
        combine(dao.observeThemes(), dao.observeAssignments()) { themes, assignments ->
            val appsByTheme = assignments.groupBy({ it.themeId }, { it.appKey })
            themes.map { it.toTheme(appsByTheme[it.id].orEmpty()) }
        }

    suspend fun createTheme(name: String, icon: ThemeIconKey, accentColor: Int, appKeys: List<String> = emptyList()): Long =
        dao.insertThemeWithApps(
            ThemeEntity(name = name.trim(), iconKey = icon.name, accentColor = accentColor, position = 0),
            appKeys,
        )

    suspend fun updateTheme(id: Long, name: String, icon: ThemeIconKey, accentColor: Int) {
        val current = dao.getThemes().firstOrNull { it.id == id } ?: return
        dao.updateTheme(current.copy(name = name.trim(), iconKey = icon.name, accentColor = accentColor))
    }

    /** Refuses to delete the last remaining theme. Returns whether the theme was deleted. */
    suspend fun deleteTheme(id: Long): Boolean {
        if (dao.countThemes() <= 1) return false
        dao.deleteTheme(id)
        return true
    }

    suspend fun reorderThemes(orderedIds: List<Long>) = dao.reorderThemes(orderedIds)

    /** Replaces (and reorders) the theme's apps. */
    suspend fun setThemeApps(themeId: Long, orderedKeys: List<String>) = dao.setApps(themeId, orderedKeys)

    suspend fun addApp(themeId: Long, appKey: String) = dao.addApp(themeId, appKey)

    suspend fun removeApp(themeId: Long, appKey: String) = dao.removeApp(themeId, appKey)

    private fun ThemeEntity.toTheme(appKeys: List<String>) = Theme(
        id = id,
        name = name,
        icon = ThemeIconKey.fromName(iconKey),
        accentColor = accentColor,
        appKeys = appKeys,
    )
}
