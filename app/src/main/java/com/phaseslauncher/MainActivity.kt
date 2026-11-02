package com.phaseslauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phaseslauncher.ui.LauncherScreen
import com.phaseslauncher.ui.components.LocalIconCache
import com.phaseslauncher.ui.theme.PhasesTheme
import com.phaseslauncher.ui.theme.isDark
import com.phaseslauncher.ui.widgets.WidgetAddFlow

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.Factory }

    private val widgetAddFlow by lazy {
        WidgetAddFlow(
            activity = this,
            repository = appContainer.widgetRepository,
            onPlaced = { id, info -> viewModel.placeWidget(id, info, resources.displayMetrics.density) },
            onFailed = viewModel::message,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent bars; the wallpaper shows through. Icon colors are adjusted per screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            val darkTheme = prefs.appearance.isDark()
            // Themed icons are light on dark in the dark theme, so they follow the chosen appearance.
            SideEffect { appContainer.iconCache.setDarkTheme(darkTheme) }
            PhasesTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalIconCache provides appContainer.iconCache) {
                    LauncherScreen(viewModel, startWidgetAdd = widgetAddFlow::start)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Widgets only update while their host is listening.
        runCatching { appContainer.widgetRepository.host.startListening() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }

    @Deprecated("AppWidgetHost's configuration screen only reports back through onActivityResult.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetAddFlow.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        super.onStop()
        runCatching { appContainer.widgetRepository.host.stopListening() }
        // Coming back to the launcher should land on the home screen, not a stale drawer.
        viewModel.closeDrawer()
    }
}
