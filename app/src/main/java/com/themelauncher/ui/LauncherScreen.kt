package com.themelauncher.ui

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themelauncher.LauncherViewModel
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.AppShortcut
import com.themelauncher.data.icons.IconPackInfo
import com.themelauncher.data.system.LockScreenService
import com.themelauncher.data.system.SystemActions
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.toDraft
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.components.LocalNotificationDots
import com.themelauncher.ui.components.LocalShortcutHost
import com.themelauncher.ui.components.ShortcutHost
import com.themelauncher.ui.drawer.AppDrawer
import com.themelauncher.ui.drawer.DrawerSearchActions
import com.themelauncher.ui.editor.AddToThemesDialog
import com.themelauncher.ui.editor.CustomizeAppDialog
import com.themelauncher.ui.editor.DeleteThemeDialog
import com.themelauncher.ui.editor.ThemeEditorEnvironment
import com.themelauncher.ui.editor.ThemeEditorSheet
import com.themelauncher.ui.home.ArcExtra
import com.themelauncher.ui.home.HomeLayout
import com.themelauncher.ui.home.HomeOptionsSheet
import com.themelauncher.ui.home.HomeScreen
import com.themelauncher.ui.home.LocalHomeLayout
import com.themelauncher.ui.home.ThemeAction
import com.themelauncher.ui.home.ThemeArcOverlay
import com.themelauncher.ui.home.ThemeArcState
import com.themelauncher.ui.onboarding.OnboardingScreen
import com.themelauncher.ui.settings.SettingsScreen
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.rememberWallpaperContentColors
import com.themelauncher.ui.widgets.LocalWidgetHost
import com.themelauncher.ui.widgets.WidgetHostUi
import com.themelauncher.ui.widgets.WidgetPickerSheet

/** Which theme the editor is open for; [themeId] == null creates a new one. */
private data class EditorRequest(val themeId: Long?)

@Composable
fun LauncherScreen(viewModel: LauncherViewModel, startWidgetAdd: (AppWidgetProviderInfo) -> Unit) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val drawerState by viewModel.drawerState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var addToThemesApp by remember { mutableStateOf<AppInfo?>(null) }
    var deletingTheme by remember { mutableStateOf<Theme?>(null) }
    var showHomeOptions by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var customizingApp by remember { mutableStateOf<AppInfo?>(null) }
    val customizations by viewModel.customizations.collectAsStateWithLifecycle()
    val arcState = remember { ThemeArcState() }
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val switcherSide = prefs.switcherSide
    val dotKeys by viewModel.notificationDots.dotKeys.collectAsStateWithLifecycle()
    val shortcutHost = remember(viewModel) {
        object : ShortcutHost {
            override suspend fun load(app: AppInfo) = viewModel.shortcutsFor(app)
            override fun launch(shortcut: AppShortcut, sourceBounds: Rect?) = viewModel.launchShortcut(shortcut, sourceBounds)
        }
    }
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

    // Back closes the arc or the drawer; on the home screen it does nothing (we are home).
    BackHandler(enabled = true) {
        when {
            arcState.isOpen -> arcState.close()
            drawerOpen -> viewModel.closeDrawer()
        }
    }

    val wallpaperColors = rememberWallpaperContentColors()

    // System-bar icons follow the wallpaper on home, and the surface color in the drawer.
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val overSurface = drawerOpen || showOnboarding == true || showSettings
        val lightBars = if (overSurface) !darkTheme else wallpaperColors.isDark
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
            // Hidden status bar: a swipe from the top edge still shows it briefly.
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (prefs.showStatusBar) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    // The drawer is slightly translucent to tint the wallpaper; hide home so it doesn't ghost through.
    val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")
    // Blur home behind the theme arc so its labels stand out (no-op before Android 12).
    val homeBlur by animateDpAsState(if (arcState.isOpen) 14.dp else 0.dp, label = "homeBlur")

    val favoriteKeys = homeState.favorites.mapTo(HashSet()) { it.key }

    val drawerActions: (AppInfo) -> List<AppAction> = { app ->
        buildList {
            if (app.key in favoriteKeys) {
                add(AppAction("Remove from favorites", Icons.Rounded.Star) { viewModel.removeFavorite(app) })
            } else {
                add(AppAction("Add to favorites", Icons.Rounded.StarOutline) { viewModel.addFavorite(app) })
            }
            add(AppAction("Add to theme…", Icons.Rounded.AddCircleOutline) { addToThemesApp = app })
            add(AppAction("Customize\u2026", Icons.Outlined.Edit) { customizingApp = app })
            add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) })
            add(AppAction("Hide app", Icons.Outlined.VisibilityOff) { viewModel.hideApp(app) })
            if (app.canUninstall) add(AppAction("Uninstall", Icons.Outlined.DeleteForever) { viewModel.uninstall(app) })
        }
    }
    val gridActions: (AppInfo) -> List<AppAction> = { app ->
        buildList {
            add(AppAction("Remove from this theme", Icons.Rounded.RemoveCircleOutline) { viewModel.removeAppFromActiveTheme(app) })
            if (app.key !in favoriteKeys) add(AppAction("Move to favorites", Icons.Rounded.Star) { viewModel.moveToFavorites(app) })
            add(AppAction("Customize\u2026", Icons.Outlined.Edit) { customizingApp = app })
            add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) })
        }
    }
    val dockActions: (AppInfo) -> List<AppAction> = { app ->
        listOf(
            AppAction("Remove from favorites", Icons.Rounded.RemoveCircleOutline) { viewModel.removeFavorite(app) },
            AppAction("Customize\u2026", Icons.Outlined.Edit) { customizingApp = app },
            AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) },
        )
    }
    val themeActions: (Theme) -> List<ThemeAction> = { theme ->
        val canDelete = homeState.themes.size > 1
        listOf(
            ThemeAction("Edit theme", Icons.Outlined.Edit) { editor = EditorRequest(theme.id) },
            ThemeAction(if (canDelete) "Delete theme" else "Delete theme (last one)", Icons.Outlined.Delete, enabled = canDelete) {
                deletingTheme = theme
            },
            ThemeAction("Manage themes", Icons.Outlined.Settings) { showSettings = true },
        )
    }
    // Arc items are the themes in order, then the ArcExtra items ("New theme", "Settings").
    val onArcItem: (Int?) -> Unit = { index ->
        arcState.close()
        val theme = index?.let { homeState.themes.getOrNull(it) }
        val extra = index?.let { ArcExtra.entries.getOrNull(it - homeState.themes.size) }
        when {
            theme != null -> {
                if (theme.id != homeState.activeTheme?.id) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.selectTheme(theme.id)
            }
            extra == ArcExtra.NewTheme -> editor = EditorRequest(themeId = null)
            extra == ArcExtra.Settings -> showSettings = true
        }
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

    val swipeDown: () -> Unit = {
        if (prefs.swipeDownForNotifications) SystemActions.expandNotifications(context)
    }
    val doubleTap: () -> Unit = {
        if (prefs.doubleTapToLock && !LockScreenService.lock()) {
            viewModel.message("Turn on \u201cThemeLauncher: double-tap to lock\u201d in Accessibility settings")
            LockScreenService.openSettings(context)
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Re-run the search so contacts show up right away, or stop asking if declined.
        if (granted) viewModel.refreshSearch() else viewModel.updatePrefs { it.copy(contactSearch = false) }
    }
    val searchActions = remember(viewModel) {
        DrawerSearchActions(
            onOpenContact = viewModel::openContact,
            onOpenSettings = viewModel::openSettingsResult,
            onSearchWeb = viewModel::searchWeb,
            onAllowContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
            onDeclineContacts = { viewModel.updatePrefs { it.copy(contactSearch = false) } },
        )
    }
    val widgetHost = remember(viewModel) {
        WidgetHostUi(
            repository = viewModel.widgets,
            onRemove = { viewModel.removeWidget(it.appWidgetId) },
            onResize = { widget, height -> viewModel.resizeWidget(widget.appWidgetId, height) },
            onMove = { widget, delta ->
                homeState.themes.firstOrNull { it.id == widget.themeId }?.let { viewModel.moveWidget(it, widget.appWidgetId, delta) }
            },
        )
    }

    CompositionLocalProvider(
        LocalWallpaperContent provides wallpaperColors,
        LocalHomeLayout provides HomeLayout(
            columns = prefs.gridColumns,
            iconSize = prefs.iconSize.dp.dp,
            showLabels = prefs.showLabels,
            showTime = prefs.showTime,
            showDate = prefs.showDate,
            dateFormat = prefs.dateFormat,
            showBattery = prefs.showBattery,
            clockPosition = prefs.clockPosition,
            showThemeLabel = prefs.showThemeLabel,
        ),
        LocalNotificationDots provides if (prefs.notificationDots) dotKeys else emptySet(),
        LocalShortcutHost provides shortcutHost,
        LocalWidgetHost provides widgetHost,
    ) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                state = homeState,
                arcState = arcState,
                switcherSide = switcherSide,
                onArcRelease = onArcItem,
                onLaunch = viewModel::launch,
                onCreateTheme = { editor = EditorRequest(themeId = null) },
                onAddApps = { editor = EditorRequest(it.id) },
                onSwipeUp = viewModel::openDrawer,
                onSwipeDown = swipeDown,
                gridAppActions = gridActions,
                dockAppActions = dockActions,
                onReorderThemeApps = { theme, keys -> viewModel.reorderThemeApps(theme.id, keys) },
                onReorderFavorites = viewModel::reorderFavorites,
                onEmptyLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showHomeOptions = true
                },
                modifier = Modifier
                    .graphicsLayer { alpha = homeAlpha }
                    .blur(homeBlur)
                    // Taps on apps are consumed by the apps, so this only sees empty space.
                    .pointerInput(prefs.doubleTapToLock) {
                        if (prefs.doubleTapToLock) detectTapGestures(onDoubleTap = { doubleTap() })
                    }
                    .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { _, delta -> dragged += delta },
                        onDragEnd = {
                            when {
                                dragged < -swipeThresholdPx -> viewModel.openDrawer()
                                dragged > swipeThresholdPx -> swipeDown()
                            }
                        },
                    )
                },
            )

            ThemeArcOverlay(
                state = arcState,
                themes = homeState.themes,
                activeThemeId = homeState.activeTheme?.id,
                side = switcherSide,
                onSelect = onArcItem,
                themeActions = themeActions,
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
                    onOpenSettings = {
                        viewModel.closeDrawer()
                        showSettings = true
                    },
                    onSelectProfile = viewModel::selectProfileTab,
                    onSetProfileQuiet = viewModel::setProfileQuiet,
                    searchActions = searchActions,
                )
            }

            if (showSettings) {
                SettingsScreen(
                    themes = homeState.themes,
                    prefs = prefs,
                    allApps = homeState.apps.values.toList(),
                    notificationDots = viewModel.notificationDots,
                    onUpdatePrefs = viewModel::updatePrefs,
                    onExport = viewModel::exportBackup,
                    onImport = viewModel::importBackup,
                    onNewTheme = { editor = EditorRequest(themeId = null) },
                    onEditTheme = { editor = EditorRequest(it.id) },
                    onDeleteTheme = { deletingTheme = it },
                    onReorderThemes = viewModel::reorderThemes,
                    onRunSetup = {
                        showSettings = false
                        showOnboarding = true
                    },
                    onClose = { showSettings = false },
                    loadIconPacks = viewModel::installedIconPacks,
                    onAddWidget = { showWidgetPicker = true },
                )
            }
        }
    }

    val editorEnvironment = remember(viewModel) {
        ThemeEditorEnvironment(
            importWallpaper = viewModel::importWallpaper,
            hasPolicyAccess = viewModel.themeEffects::hasPolicyAccess,
            requestPolicyAccess = { viewModel.themeEffects.openPolicyAccessSettings(context) },
            hasBluetoothPermission = viewModel.autoSwitcher::hasBluetoothPermission,
            pairedDevices = viewModel.autoSwitcher::pairedDevices,
        )
    }

    editor?.let { request ->
        val theme = request.themeId?.let { id -> homeState.themes.firstOrNull { it.id == id } }
        ThemeEditorSheet(
            initial = theme?.toDraft(),
            allApps = homeState.apps.values.toList(),
            env = editorEnvironment,
            usedColors = homeState.themes.mapTo(HashSet()) { it.accentColor },
            onSave = { draft ->
                editor = null
                if (theme == null) viewModel.createTheme(draft) else viewModel.updateTheme(theme.id, draft)
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
            onAddWidget = { showWidgetPicker = true },
            onOpenSettings = { showSettings = true },
            onDismiss = { showHomeOptions = false },
        )
    }

    if (showWidgetPicker) {
        WidgetPickerSheet(
            themeName = homeState.activeTheme?.name ?: "this theme",
            loadProviders = viewModel.widgets::availableProviders,
            onPick = { info ->
                showWidgetPicker = false
                startWidgetAdd(info)
            },
            onDismiss = { showWidgetPicker = false },
        )
    }

    customizingApp?.let { app ->
        val iconPacks by produceState(emptyList<IconPackInfo>()) { value = viewModel.installedIconPacks() }
        val current = customizations[app.key]
        CustomizeAppDialog(
            app = app,
            initialIcon = current?.let { c -> c.iconPack?.let { pack -> c.iconName?.let { pack to it } } },
            iconPacks = iconPacks,
            iconPackManager = viewModel.iconPackManager(),
            loadIconNames = viewModel::iconPackDrawables,
            onSave = { label, pack, name -> viewModel.customizeApp(app, label, pack, name) },
            onReset = { viewModel.resetCustomization(app) },
            onDismiss = { customizingApp = null },
        )
    }

}
