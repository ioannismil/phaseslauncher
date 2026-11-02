package com.phaseslauncher.ui.pin

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phaseslauncher.appContainer
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.widgets.WidgetRepository
import com.phaseslauncher.data.settings.AppearanceMode
import com.phaseslauncher.ui.theme.PhasesTheme
import com.phaseslauncher.ui.theme.isDark
import com.phaseslauncher.ui.widgets.WidgetAddFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is being added, for the dialog. */
data class PinPreview(val label: String, val icon: ImageBitmap?, val isWidget: Boolean)

/**
 * Handles "Add to home screen" from other apps (Chrome, Maps, Contacts, widget pickers): the system
 * opens this over the requesting app with a pin request, and the user picks the theme it goes on.
 */
class PinItemActivity : ComponentActivity() {

    private lateinit var request: LauncherApps.PinItemRequest

    /** The theme a widget goes on, while its bind/configure screens are showing. */
    private var widgetTheme: Theme? = null

    private val widgetAddFlow by lazy {
        WidgetAddFlow(
            activity = this,
            repository = appContainer.widgetRepository,
            onPlaced = ::placeWidget,
            onFailed = { done(it) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pinRequest = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }.getOrNull()
        val preview = pinRequest?.takeIf { it.isValid }?.let(::previewOf)
        if (pinRequest == null || preview == null) {
            finish()
            return
        }
        request = pinRequest
        val container = appContainer

        setContent {
            val prefs by container.settingsRepository.prefs.collectAsStateWithLifecycle(initialValue = null)
            PhasesTheme(darkTheme = (prefs?.appearance ?: AppearanceMode.System).isDark()) {
                val themes by container.themeRepository.themes.collectAsStateWithLifecycle(initialValue = null)
                val active by container.activeTheme.collectAsStateWithLifecycle(initialValue = null)
                themes?.let { list ->
                    PinItemDialog(
                        preview = preview,
                        themes = list,
                        initialThemeId = active?.id,
                        onAdd = ::add,
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    private fun previewOf(request: LauncherApps.PinItemRequest): PinPreview? = when (request.requestType) {
        LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> request.shortcutInfo?.let { info ->
            val shortcuts = appContainer.pinnedShortcuts
            PinPreview(shortcuts.labelOf(info), shortcuts.render(info), isWidget = false)
        }
        LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> request.getAppWidgetProviderInfo(this)?.let { info ->
            val icon = runCatching { info.loadIcon(this, resources.displayMetrics.densityDpi)?.toBitmap()?.asImageBitmap() }.getOrNull()
            PinPreview(WidgetRepository.labelOf(this, info), icon, isWidget = true)
        }
        else -> null
    }

    private fun add(theme: Theme) {
        when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val container = appContainer
                container.appScope.launch {
                    val key = container.pinnedShortcuts.accept(request)
                    if (key != null) container.themeRepository.addApp(theme.id, key)
                    withContext(Dispatchers.Main) {
                        done(if (key != null) "Added to ${theme.name}" else "Couldn’t add the shortcut")
                    }
                }
            }
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                val info = request.getAppWidgetProviderInfo(this) ?: return done("Couldn’t add the widget")
                widgetTheme = theme
                widgetAddFlow.start(info)
            }
        }
    }

    /** The widget is bound (and configured); hand its id to the requesting app and place it. */
    private fun placeWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        val theme = widgetTheme ?: return done(null)
        val container = appContainer
        val extras = Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
        if (!runCatching { request.isValid && request.accept(extras) }.getOrDefault(false)) {
            container.widgetRepository.discardId(appWidgetId)
            return done("Couldn’t add the widget")
        }
        val density = resources.displayMetrics.density
        container.appScope.launch {
            container.widgetRepository.add(appWidgetId, theme.id, info, density)
            withContext(Dispatchers.Main) { done("Added to ${theme.name}") }
        }
    }

    private fun done(message: String?) {
        message?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
        finish()
    }

    @Deprecated("AppWidgetHost's bind and configuration screens only report back through onActivityResult.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetAddFlow.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }
}
