package com.themelauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import com.themelauncher.ui.LauncherScreen
import com.themelauncher.ui.components.LocalIconCache
import com.themelauncher.ui.theme.ThemeLauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent bars; the wallpaper shows through. Icon colors are adjusted per screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            ThemeLauncherTheme {
                CompositionLocalProvider(LocalIconCache provides appContainer.iconCache) {
                    LauncherScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }

    override fun onStop() {
        super.onStop()
        // Coming back to the launcher should land on the home screen, not a stale drawer.
        viewModel.closeDrawer()
    }
}
