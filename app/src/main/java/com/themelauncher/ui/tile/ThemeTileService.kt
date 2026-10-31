package com.themelauncher.ui.tile

import android.app.AlertDialog
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.themelauncher.R
import com.themelauncher.appContainer
import com.themelauncher.data.themes.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A Quick Settings tile showing the active theme. Tapping it lists the themes over the shade, so
 * themes can be switched from anywhere, without going home.
 */
class ThemeTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val container = appContainer
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { container.activeTheme.collect(::show) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun show(theme: Theme?) {
        val tile = qsTile ?: return
        tile.label = theme?.name ?: getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(R.string.tile_label)
        tile.state = if (theme != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Picking a theme needs no unlock, but don't show the list over a secure lock screen.
        if (isSecure) unlockAndRun(::pickTheme) else pickTheme()
    }

    private fun pickTheme() {
        val container = appContainer
        val scope = scope ?: return
        scope.launch {
            val themes = container.themeRepository.themes.first()
            if (themes.isEmpty()) return@launch
            val active = container.activeTheme.first()
            val dialog = AlertDialog.Builder(this@ThemeTileService, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.tile_dialog_title)
                .setSingleChoiceItems(themes.map { it.name }.toTypedArray(), themes.indexOfFirst { it.id == active?.id }) { dialog, which ->
                    dialog.dismiss()
                    container.appScope.launch {
                        container.settingsRepository.setActiveThemeId(themes[which].id)
                        container.history.recordManualSwitch(themes[which].id)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            showDialog(dialog)
        }
    }
}
