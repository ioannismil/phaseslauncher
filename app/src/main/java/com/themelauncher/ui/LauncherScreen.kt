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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themelauncher.LauncherViewModel
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.drawer.AppDrawer
import com.themelauncher.ui.editor.AddToThemesDialog
import com.themelauncher.ui.editor.DeleteThemeDialog
import com.themelauncher.ui.editor.ReorderThemesDialog
import com.themelauncher.ui.editor.ThemeEditorSheet
import com.themelauncher.ui.home.HomeOptionsSheet
import com.themelauncher.ui.home.HomeScreen
import com.themelauncher.ui.home.ThemeAction
import com.themelauncher.ui.onboarding.OnboardingScreen
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.rememberWallpaperContentColors

/** Which theme the editor is open for; [themeId] == null creates a new one. */
private data class EditorRequest(val themeId: Long?)

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val drawerState by viewModel.drawerState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var addToThemesApp by remember { mutableStateOf<AppInfo?>(null) }
    var deletingTheme by remember { mutableStateOf<Theme?>(null) }
    var reordering by remember { mutableStateOf(false) }
    var showHomeOptions by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Decided once the setting has loaded; stays up after completion until the user taps Finish.
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    var showOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(onboardingComplete) {
        if (showOnboarding == null) onboardingComplete?.let { showOnboarding = !it }
    }

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
        val overSurface = drawerOpen || showOnboarding == true
        val lightBars = if (overSurface) !darkTheme else wallpaperColors.isDark
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    // The drawer is slightly translucent to tint the wallpaper; hide home so it doesn't ghost through.
    val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")

    val favoriteKeys = homeState.favorites.mapTo(HashSet()) { it.key }

    val drawerActions: (AppInfo) -> List<AppAction> = { app ->
        buildList {
            if (app.key in favoriteKeys) {
                add(AppAction("Remove from favorites", Icons.Rounded.Star) { viewModel.removeFavorite(app) })
            } else {
                add(AppAction("Add to favorites", Icons.Rounded.StarOutline) { viewModel.addFavorite(app) })
            }
            add(AppAction("Add to theme…", Icons.Rounded.AddCircleOutline) { addToThemesApp = app })
            add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) })
            if (app.canUninstall) add(AppAction("Uninstall", Icons.Outlined.DeleteForever) { viewModel.uninstall(app) })
        }
    }
    val gridActions: (AppInfo) -> List<AppAction> = { app ->
        buildList {
            add(AppAction("Remove from this theme", Icons.Rounded.RemoveCircleOutline) { viewModel.removeAppFromActiveTheme(app) })
            if (app.key !in favoriteKeys) add(AppAction("Move to favorites", Icons.Rounded.Star) { viewModel.moveToFavorites(app) })
            add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) })
        }
    }
    val dockActions: (AppInfo) -> List<AppAction> = { app ->
        listOf(
            AppAction("Remove from favorites", Icons.Rounded.RemoveCircleOutline) { viewModel.removeFavorite(app) },
            AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) },
        )
    }
    val themeActions: (Theme) -> List<ThemeAction> = { theme ->
        val canDelete = homeState.themes.size > 1
        listOf(
            ThemeAction("Edit theme", Icons.Outlined.Edit) { editor = EditorRequest(theme.id) },
            ThemeAction("Reorder themes", Icons.Rounded.SwapVert, enabled = homeState.themes.size > 1) { reordering = true },
            ThemeAction(if (canDelete) "Delete theme" else "Delete theme (last one)", Icons.Outlined.Delete, enabled = canDelete) {
                deletingTheme = theme
            },
        )
    }

    when (showOnboarding) {
        null -> return // Still loading; show just the wallpaper for a moment.
        true -> {
            OnboardingScreen(
                allApps = homeState.apps.values.toList(),
                initialFavorites = homeState.favorites.map { it.key },
                onComplete = viewModel::completeOnboarding,
                onSkip = {
                    viewModel.skipOnboarding()
                    showOnboarding = false
                },
                onFinished = { showOnboarding = false },
            )
            return
        }
        false -> Unit
    }

    CompositionLocalProvider(LocalWallpaperContent provides wallpaperColors) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                state = homeState,
                onLaunch = viewModel::launch,
                onSelectTheme = { viewModel.selectTheme(it.id) },
                themeActions = themeActions,
                onCreateTheme = { editor = EditorRequest(themeId = null) },
                onAddApps = { editor = EditorRequest(it.id) },
                onSwipeUp = viewModel::openDrawer,
                gridAppActions = gridActions,
                dockAppActions = dockActions,
                onReorderThemeApps = { theme, keys -> viewModel.reorderThemeApps(theme.id, keys) },
                onReorderFavorites = viewModel::reorderFavorites,
                onEmptyLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showHomeOptions = true
                },
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

    editor?.let { request ->
        val theme = request.themeId?.let { id -> homeState.themes.firstOrNull { it.id == id } }
        ThemeEditorSheet(
            initial = theme?.let { ThemeDraft(it.name, it.icon, it.accentColor, it.appKeys) },
            allApps = homeState.apps.values.toList(),
            usedColors = homeState.themes.mapTo(HashSet()) { it.accentColor },
            onSave = { draft ->
                editor = null
                if (theme == null) {
                    viewModel.createTheme(draft.name, draft.icon, draft.accentColor, draft.appKeys)
                } else {
                    viewModel.updateTheme(theme.id, draft.name, draft.icon, draft.accentColor, draft.appKeys)
                }
            },
            onDismiss = { editor = null },
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

    deletingTheme?.let { theme ->
        DeleteThemeDialog(
            theme = theme,
            onConfirm = {
                deletingTheme = null
                viewModel.deleteTheme(theme.id)
            },
            onDismiss = { deletingTheme = null },
        )
    }

    if (showHomeOptions) {
        HomeOptionsSheet(
            onNewTheme = { editor = EditorRequest(themeId = null) },
            onRunSetup = { showOnboarding = true },
            onDismiss = { showHomeOptions = false },
        )
    }

    if (reordering) {
        ReorderThemesDialog(
            themes = homeState.themes,
            onSave = { ids ->
                reordering = false
                viewModel.reorderThemes(ids)
            },
            onDismiss = { reordering = false },
        )
    }
}
