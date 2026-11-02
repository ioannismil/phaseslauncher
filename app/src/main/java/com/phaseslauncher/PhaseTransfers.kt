package com.phaseslauncher

import android.net.Uri
import com.phaseslauncher.data.importer.LauncherLayout
import com.phaseslauncher.data.importer.LayoutImportOptions
import com.phaseslauncher.data.runCatchingCancellable
import com.phaseslauncher.data.share.ImportOptions
import com.phaseslauncher.data.share.Resolution
import com.phaseslauncher.data.share.ShareOptions
import com.phaseslauncher.data.share.SharedTheme
import com.phaseslauncher.data.themes.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Backups to a file, phases shared as files, links and QR codes, and other launchers' layouts. */
class PhaseTransfers(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val homeState: StateFlow<HomeState>,
    private val onActivate: (themeId: Long) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val res = container.appContext.resources

    fun exportBackup(uri: Uri) {
        scope.launch {
            runCatchingCancellable { container.backupRepository.exportTo(uri) }
                .onSuccess { onMessage(res.getString(R.string.backed_up, phasesCount(it.themes), favoritesCount(it.favorites))) }
                .onFailure { onMessage(res.getString(R.string.backup_failed, it.message)) }
        }
    }

    fun importBackup(uri: Uri) {
        scope.launch {
            // Widget ids belong to this phone's widget host; the restored themes start without widgets.
            container.widgetRepository.removeAll()
            runCatchingCancellable { container.backupRepository.importFrom(uri) }
                .onSuccess { onMessage(res.getString(R.string.restored, phasesCount(it.themes), favoritesCount(it.favorites))) }
                .onFailure { onMessage(res.getString(R.string.couldn_t_restore, it.message)) }
        }
    }

    suspend fun shareJson(theme: Theme, options: ShareOptions, iconPackLabel: String?): JSONObject {
        val state = homeState.value
        val dock = if (theme.ownDock) container.favoritesRepository.getKeys(theme.id) else emptyList()
        return container.themeSharing.export(
            theme = theme,
            folders = state.folders,
            dock = dock,
            labelOf = { key -> state.apps[key]?.label },
            iconPackLabel = iconPackLabel,
            options = options,
        )
    }

    fun shareLink(json: JSONObject): String = container.themeSharing.toLink(json)

    fun qrCode(text: String, sizePx: Int) = container.themeSharing.qrCode(text, sizePx)

    suspend fun shareFile(json: JSONObject, name: String): Uri = container.themeSharing.writeFile(json, name)

    suspend fun readSharedTheme(uri: Uri): Result<SharedTheme> = runCatchingCancellable { container.themeSharing.read(uri) }

    fun parseSharedTheme(text: String): Result<SharedTheme> = runCatching { container.themeSharing.parse(text) }

    fun resolveSharedTheme(shared: SharedTheme): Resolution =
        container.themeSharing.resolve(shared, homeState.value.apps.values.toList())

    fun installSharedTheme(shared: SharedTheme, resolution: Resolution, options: ImportOptions) {
        scope.launch {
            runCatchingCancellable { container.themeSharing.install(shared, resolution, options) }
                .onSuccess { id ->
                    onActivate(id)
                    onMessage(res.getString(R.string.added, options.name.ifBlank { shared.name }))
                }
                .onFailure { onMessage(res.getString(R.string.couldn_t_add_the_phase, it.message)) }
        }
    }

    suspend fun readLauncherBackup(uri: Uri): Result<LauncherLayout> =
        runCatchingCancellable { container.launcherImporter.read(uri, homeState.value.apps.values.toList()) }

    fun importLauncherLayout(layout: LauncherLayout, options: LayoutImportOptions) {
        scope.launch {
            runCatchingCancellable { container.launcherImporter.apply(layout, options) }
                .onSuccess { count -> onMessage(res.getQuantityString(R.plurals.imported_phases, count, count)) }
                .onFailure { onMessage(res.getString(R.string.import_failed, it.message)) }
        }
    }

    private fun phasesCount(n: Int) = res.getQuantityString(R.plurals.phases_count, n, n)

    private fun favoritesCount(n: Int) = res.getQuantityString(R.plurals.favorites_count, n, n)
}
