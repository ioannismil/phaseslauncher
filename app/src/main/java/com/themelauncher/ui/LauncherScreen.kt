package com.themelauncher.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themelauncher.LauncherViewModel
import com.themelauncher.ui.drawer.AppDrawer
import com.themelauncher.ui.home.HomeScreen

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val drawerState by viewModel.drawerState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    // Back closes the drawer; on the home screen it does nothing (we are home).
    BackHandler(enabled = true) { if (drawerOpen) viewModel.closeDrawer() }

    // Light system-bar icons over the wallpaper; dark ones over the light-mode drawer.
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = drawerOpen && !darkTheme
            isAppearanceLightNavigationBars = drawerOpen && !darkTheme
        }
    }

    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            modifier = Modifier.pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, delta -> dragged += delta },
                    onDragEnd = { if (dragged < -swipeThresholdPx) viewModel.openDrawer() },
                )
            },
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                state = drawerState,
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onLaunch = viewModel::launch,
                onClose = viewModel::closeDrawer,
            )
        }
    }
}
