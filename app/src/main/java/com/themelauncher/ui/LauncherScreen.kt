package com.themelauncher.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themelauncher.LauncherViewModel
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.drawer.AppDrawer
import com.themelauncher.ui.editor.AddToThemesDialog
import com.themelauncher.ui.editor.ThemeEditorSheet
import com.themelauncher.ui.home.HomeScreen
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.rememberWallpaperContentColors

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val drawerState by viewModel.drawerState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var creatingTheme by remember { mutableStateOf(false) }
    var addToThemesApp by remember { mutableStateOf<AppInfo?>(null) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Back closes the drawer; on the home screen it does nothing (we are home).
    BackHandler(enabled = true) { if (drawerOpen) viewModel.closeDrawer() }

    val wallpaperColors = rememberWallpaperContentColors()

    // System-bar icons follow the wallpaper on home, and the surface color in the drawer.
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val lightBars = if (drawerOpen) !darkTheme else wallpaperColors.isDark
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    // The drawer is slightly translucent to tint the wallpaper; hide home so it doesn't ghost through.
    val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")

    val drawerActions: (AppInfo) -> List<AppAction> = { app ->
        listOf(
            AppAction("Add to favorites", Icons.Rounded.StarOutline) { viewModel.addFavorite(app) },
            AppAction("Add to theme…", Icons.Rounded.AddCircleOutline) { addToThemesApp = app },
            AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) },
        )
    }
    val gridActions: (AppInfo) -> List<AppAction> = { app ->
        listOf(
            AppAction("Remove from this theme", Icons.Rounded.RemoveCircleOutline) { viewModel.removeAppFromActiveTheme(app) },
            AppAction("Move to favorites", Icons.Rounded.Star) { viewModel.moveToFavorites(app) },
            AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) },
        )
    }
    val dockActions: (AppInfo) -> List<AppAction> = { app ->
        listOf(
            AppAction("Remove from favorites", Icons.Rounded.RemoveCircleOutline) { viewModel.removeFavorite(app) },
            AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) },
        )
    }

    CompositionLocalProvider(LocalWallpaperContent provides wallpaperColors) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                state = homeState,
                onLaunch = viewModel::launch,
                onSelectTheme = { viewModel.selectTheme(it.id) },
                onThemeLongPress = { /* Theme menu arrives in phase 4. */ },
                onCreateTheme = { creatingTheme = true },
                onAddApps = { viewModel.openDrawer() },
                onSwipeUp = viewModel::openDrawer,
                gridAppActions = gridActions,
                dockAppActions = dockActions,
                modifier = Modifier.graphicsLayer { alpha = homeAlpha }.pointerInput(Unit) {
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
                    appActions = drawerActions,
                    onClose = viewModel::closeDrawer,
                )
            }
        }
    }

    if (creatingTheme) {
        ThemeEditorSheet(
            initial = null,
            onSave = { draft ->
                creatingTheme = false
                viewModel.createTheme(draft.name, draft.icon, draft.accentColor)
            },
            onDismiss = { creatingTheme = false },
        )
    }

    addToThemesApp?.let { app ->
        AddToThemesDialog(
            app = app,
            themes = homeState.themes,
            onConfirm = { ids ->
                addToThemesApp = null
                viewModel.addAppToThemes(app, ids)
            },
            onDismiss = { addToThemesApp = null },
        )
    }
}
