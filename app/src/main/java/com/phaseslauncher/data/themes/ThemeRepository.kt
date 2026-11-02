package com.phaseslauncher.data.themes

import androidx.room.withTransaction
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.db.ThemeEntity
import com.phaseslauncher.data.db.ThemeRuleEntity
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.data.items.GridCell
import com.phaseslauncher.data.items.GridPlacement
import com.phaseslauncher.data.items.HomeGrid
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeAutomation
import com.phaseslauncher.data.rules.ThemeRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ThemeRepository(private val database: LauncherDatabase) {

    private val dao = database.themeDao()
    private val ruleDao = database.ruleDao()
    private val widgetDao = database.widgetDao()

    /** All themes in switcher order, each with its ordered app keys and switching rules. */
    val themes: Flow<List<Theme>> =
        combine(dao.observeThemes(), dao.observeAssignments(), ruleDao.observeAll()) { themes, assignments, rules ->
            val assignmentsByTheme = assignments.groupBy { it.themeId }
            val rulesByTheme = rules.groupBy { it.themeId }
            themes.map { entity ->
                val rows = assignmentsByTheme[entity.id].orEmpty()
                entity.toTheme(
                    appKeys = rows.map { it.appKey },
                    rules = rulesByTheme[entity.id].orEmpty().mapNotNull { it.toSetting() },
                    cells = rows.mapNotNull { row -> row.cellX?.let { x -> row.cellY?.let { y -> row.appKey to GridCell(x, y) } } }.toMap(),
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
                iconPack = draft.iconPack,
                gridLayout = draft.gridLayout,
                automation = draft.automation.toJsonOrEmpty(),
                focus = draft.focus.toJsonOrEmpty(),
            ),
            draft.appKeys,
        )
        ruleDao.setRules(id, draft.rules.toEntities(id))
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
                iconPack = draft.iconPack,
                gridLayout = draft.gridLayout,
                automation = draft.automation.toJsonOrEmpty(),
                focus = draft.focus.toJsonOrEmpty(),
            ),
        )
        dao.setApps(id, draft.appKeys)
        ruleDao.setRules(id, draft.rules.toEntities(id))
    }

    /** Adds a rule to a theme (e.g. an accepted schedule suggestion). */
    suspend fun addRule(themeId: Long, rule: ThemeRule) = database.withTransaction {
        val current = ruleDao.getForThemeOrdered(themeId)
        ruleDao.setRules(themeId, current + ThemeRule.toEntity(themeId, rule))
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

    /**
     * Saves where everything on the theme's home grid is: its items' and widgets' cells, and the
     * items' order (row by row) for the editor and sharing. Items not in [layout] (apps that aren't
     * available right now) keep their cells and go after the rest.
     */
    suspend fun saveLayout(themeId: Long, layout: List<GridPlacement>) = database.withTransaction {
        val keys = dao.getThemeAppKeys(themeId)
        val ordered = HomeGrid.readingOrder(layout)
        val onGrid = ordered.filter { HomeGrid.widgetId(it.key) == null && it.key in keys }
        val placedKeys = onGrid.mapTo(HashSet()) { it.key }
        onGrid.forEachIndexed { i, p ->
            dao.setPosition(themeId, p.key, i)
            dao.setCell(themeId, p.key, p.x, p.y)
        }
        keys.filter { it !in placedKeys }.forEachIndexed { i, key -> dao.setPosition(themeId, key, onGrid.size + i) }
        ordered.forEach { p -> HomeGrid.widgetId(p.key)?.let { widgetDao.setPlacement(it, p.x, p.y, p.width, p.height) } }
    }

    private fun List<RuleSetting>.toEntities(themeId: Long) = map { ThemeRule.toEntity(themeId, it.rule, it.enabled) }

    private fun ThemeRuleEntity.toSetting(): RuleSetting? = ThemeRule.fromEntity(this)?.let { RuleSetting(it, enabled) }

    private fun ThemeAutomation.toJsonOrEmpty() = if (this == ThemeAutomation()) "" else toJson()

    private fun ThemeFocus.toJsonOrEmpty() = if (isDefault) "" else toJson()

    private fun ThemeEntity.toTheme(appKeys: List<String>, rules: List<RuleSetting>, cells: Map<String, GridCell>) = Theme(
        id = id,
        name = name,
        icon = ThemeIconKey.fromName(iconKey),
        accentColor = accentColor,
        appKeys = appKeys,
        wallpaperPath = wallpaperPath,
        soundProfile = SoundProfile.fromName(soundProfile),
        ownDock = ownDock,
        rules = rules,
        iconPack = iconPack,
        gridLayout = gridLayout,
        automation = ThemeAutomation.fromJson(automation),
        focus = ThemeFocus.fromJson(focus),
        cells = cells,
    )
}
