package com.phaseslauncher.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.phaseslauncher.appContainer
import com.phaseslauncher.data.settings.AppearanceMode
import com.phaseslauncher.data.share.Resolution
import com.phaseslauncher.data.share.SharedTheme
import com.phaseslauncher.ui.theme.PhasesTheme
import com.phaseslauncher.ui.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a shared theme: a phases:// link (from a QR code or a message), a shared theme
 * file, or text containing a link. Shows what's in it and adds it on request.
 */
class ImportThemeActivity : ComponentActivity() {

    private var received by mutableStateOf<Pair<SharedTheme, Resolution>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val shared = readIntent(intent) ?: error("Nothing to import")
                    val apps = container.appsRepository.apps.filterNotNull().first()
                    shared to container.themeSharing.resolve(shared, apps)
                }
            }
            result.onSuccess { received = it }.onFailure { done(it.message ?: "Couldn't open the phase") }
        }

        setContent {
            val prefs by container.settingsRepository.prefs.collectAsStateWithLifecycle(initialValue = null)
            PhasesTheme(darkTheme = (prefs?.appearance ?: AppearanceMode.System).isDark()) {
                received?.let { (shared, resolution) ->
                    ImportThemeDialog(
                        shared = shared,
                        resolution = resolution,
                        onImport = { options ->
                            container.appScope.launch {
                                val message = runCatching { container.themeSharing.install(shared, resolution, options) }
                                    .onSuccess { container.settingsRepository.setActiveThemeId(it) }
                                    .fold({ "Added ${options.name.ifBlank { shared.name }}" }, { "Couldn't add the phase: ${it.message}" })
                                withContext(Dispatchers.Main) { done(message) }
                            }
                        },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    private fun readIntent(intent: Intent): SharedTheme? {
        val sharing = appContainer.themeSharing
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (uri.scheme == "phases") sharing.parse(uri.toString()) else readUri(uri)
            }
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                stream?.let(::readUri) ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let(sharing::parse)
            }
            else -> null
        }
    }

    private fun readUri(uri: Uri): SharedTheme = appContainer.themeSharing.let { it.parse(it.readText(uri)) }

    private fun done(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
