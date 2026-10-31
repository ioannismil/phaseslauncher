package com.themelauncher.ui

import com.themelauncher.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Eject
import androidx.compose.material.icons.outlined.FolderOff
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
import com.themelauncher.data.settings.GestureAction
import com.themelauncher.data.settings.GestureKind
import com.themelauncher.data.items.AppItem
import com.themelauncher.data.items.FolderItem
import com.themelauncher.data.items.HomeItem
import com.themelauncher.data.items.PinnedShortcut
import com.themelauncher.data.items.ShortcutItem
import com.themelauncher.data.system.LockScreenService
import com.themelauncher.data.system.SystemActions
import com.themelauncher.data.themes.Theme
import com.themelauncher.data.themes.ThemeLook
import com.themelauncher.data.themes.toDraft
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.data.importer.LauncherLayout
import com.themelauncher.data.share.Resolution
import com.themelauncher.data.share.SharedTheme
import com.themelauncher.ui.home.FocusPauseDialog
import com.themelauncher.ui.importer.LauncherImportDialog
import com.themelauncher.ui.share.ImportThemeDialog
import com.themelauncher.ui.share.PasteThemeDialog
import com.themelauncher.ui.share.ShareActions
import com.themelauncher.ui.share.ShareThemeSheet
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.components.AppIcon
import com.themelauncher.ui.components.ExternalDragState
import com.themelauncher.ui.components.LocalExternalDrag
import com.themelauncher.ui.components.externalDragTracker
import com.themelauncher.ui.components.LaunchAnimation
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
import com.themelauncher.ui.home.AddToFolderDialog
import com.themelauncher.ui.home.FolderDialog
import com.themelauncher.ui.home.RenameFolderDialog
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
import com.themelauncher.ui.theme.LocalDarkTheme
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.rememberWallpaperContentColors
import com.themelauncher.ui.widgets.LocalWidgetHost
import com.themelauncher.ui.widgets.WidgetHostUi
import com.themelauncher.ui.widgets.WidgetPickerSheet

/** Which theme the editor is open for; [themeId] == null creates a new one. [draft] opens it with changes already made. */
private data class EditorRequest(val themeId: Long?, val draft: ThemeDraft? = null)

/** A shared theme waiting for the user to look it over. */
private data class IncomingTheme(val shared: SharedTheme, val resolution: Resolution)

@Composable
fun LauncherScreen(viewModel: LauncherViewModel, startWidgetAdd: (AppWidgetProviderInfo) -> Unit) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val drawerKeyboard by viewModel.drawerKeyboard.collectAsStateWithLifecycle()
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
    var openFolderKey by remember { mutableStateOf<String?>(null) }
    var addToFolderItem by remember { mutableStateOf<HomeItem?>(null) }
    var renamingFolder by remember { mutableStateOf<FolderItem?>(null) }
    var sharingTheme by remember { mutableStateOf<Theme?>(null) }
    var incomingTheme by remember { mutableStateOf<IncomingTheme?>(null) }
    var pastingTheme by remember { mutableStateOf(false) }
    var incomingLayout by remember { mutableStateOf<LauncherLayout?>(null) }
    val banner by viewModel.homeBanner.collectAsStateWithLifecycle()
    val pendingLaunch by viewModel.pendingLaunch.collectAsStateWithLifecycle()
    val uiScope = rememberCoroutineScope()
    val customizations by viewModel.customizations.collectAsStateWithLifecycle()
    val arcState = remember { ThemeArcState() }
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val switcherSide = prefs.switcherSide
    val dotKeys by viewModel.notificationDots.dotKeys.collectAsStateWithLifecycle()
    // Launches grow out of the tapped icon.
    val rootView = LocalView.current
    val launchApp: (AppInfo, Rect?) -> Unit = { app, bounds -> viewModel.launch(app, bounds, LaunchAnimation.options(rootView, bounds)) }
    val openItem: (HomeItem, Rect?) -> Unit = { item, bounds ->
        if (item is FolderItem) openFolderKey = item.key else viewModel.open(item, bounds, LaunchAnimation.options(rootView, bounds))
    }
    val shortcutHost = remember(viewModel, rootView) {
        object : ShortcutHost {
            override suspend fun load(app: AppInfo) = viewModel.shortcutsFor(app)
            override fun launch(shortcut: AppShortcut, sourceBounds: Rect?) =
                viewModel.launchShortcut(shortcut, sourceBounds, LaunchAnimation.options(rootView, sourceBounds))
            override suspend fun pinnedIcon(shortcut: PinnedShortcut) = viewModel.pinnedShortcutIcon(shortcut)
        }
    }
    // Carrying an app from the drawer: the drawer closes under the finger and home takes the drop.
    val externalDrag = remember { ExternalDragState() }
    val dragHaptics = LocalHapticFeedback.current
    externalDrag.onStart = {
        dragHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.closeDrawer()
    }
    externalDrag.onDrop = { app, window ->
        val dock = externalDrag.dockBounds
        if (dock != null && dock.contains(window)) viewModel.addFavorite(app) else viewModel.dropOnHome(app, externalDrag.gridDrop?.invoke(window))
    }

    // Coming back from an app, home zooms in slightly, as if the app shrank back into it.
    val homeZoom = remember { Animatable(1f) }
    LaunchedEffect(viewModel) {
        viewModel.returnedFromApp.collect {
            homeZoom.snapTo(RETURN_ZOOM_FROM)
            homeZoom.animateTo(1f, tween(RETURN_ZOOM_MS, easing = FastOutSlowInEasing))
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
    LaunchedEffect(viewModel) {
        viewModel.editDraft.collect { (themeId, draft) -> editor = EditorRequest(themeId, draft) }
    }

    val editorEnvironment = remember(viewModel) {
        val switcher = viewModel.autoSwitcher
        ThemeEditorEnvironment(
            importWallpaper = viewModel::importWallpaper,
            hasPolicyAccess = viewModel.themeEffects::hasPolicyAccess,
            requestPolicyAccess = { viewModel.themeEffects.openPolicyAccessSettings(context) },
            hasBluetoothPermission = switcher::hasBluetoothPermission,
            pairedDevices = switcher::pairedDevices,
            hasLocationPermission = switcher::hasLocationPermission,
            hasBackgroundLocation = switcher.location::hasBackgroundPermission,
            currentWifiSsid = switcher::currentWifiSsid,
            currentLocation = switcher.location::currentLocation,
            onLocationGranted = switcher::onLocationGranted,
            hasCalendarPermission = switcher.calendar::hasPermission,
            calendars = switcher.calendar::calendars,
            onCalendarGranted = switcher.calendar::refresh,
            hasNotificationAccess = viewModel.notificationDots::hasAccess,
            requestNotificationAccess = { viewModel.notificationDots.openAccessSettings(context) },
            hasUsageAccess = viewModel.focusManager::hasUsageAccess,
            requestUsageAccess = { viewModel.focusManager.openUsageAccessSettings(context) },
            loadIconPacks = viewModel::installedIconPacks,
        )
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
    val darkTheme = LocalDarkTheme.current
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
    val gridActions: (HomeItem) -> List<AppAction> = { item ->
        buildList {
            when (item) {
                is AppItem -> {
                    val app = item.app
                    add(AppAction("Remove from this theme", Icons.Rounded.RemoveCircleOutline) { viewModel.removeFromActiveTheme(item) })
                    if (app.key !in favoriteKeys) add(AppAction("Move to favorites", Icons.Rounded.Star) { viewModel.moveToFavorites(app) })
                    add(AppAction("Add to folder\u2026", Icons.Outlined.CreateNewFolder) { addToFolderItem = item })
                    add(AppAction("Customize\u2026", Icons.Outlined.Edit) { customizingApp = app })
                    add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(app, it) })
                }
                is ShortcutItem -> {
                    add(AppAction("Remove from this theme", Icons.Rounded.RemoveCircleOutline) { viewModel.removeFromActiveTheme(item) })
                    add(AppAction("Add to folder\u2026", Icons.Outlined.CreateNewFolder) { addToFolderItem = item })
                    add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(item.app, it) })
                }
                is FolderItem -> {
                    add(AppAction("Rename", Icons.Outlined.Edit) { renamingFolder = item })
                    add(AppAction("Ungroup", Icons.Outlined.FolderOff) { viewModel.ungroupFolder(item) })
                }
            }
        }
    }
    val folderItemActions: (FolderItem, HomeItem) -> List<AppAction> = { folder, item ->
        buildList {
            add(AppAction("Move out of folder", Icons.Outlined.Eject) { viewModel.moveOutOfFolder(folder, item) })
            add(AppAction("Remove", Icons.Rounded.RemoveCircleOutline) { viewModel.removeFromFolder(folder, item) })
            when (item) {
                is AppItem -> {
                    add(AppAction("Customize\u2026", Icons.Outlined.Edit) { customizingApp = item.app })
                    add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(item.app, it) })
                }
                is ShortcutItem -> add(AppAction("App info", Icons.Outlined.Info) { viewModel.openAppInfo(item.app, it) })
                is FolderItem -> Unit
            }
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
            ThemeAction("Share theme", Icons.Outlined.Share) { sharingTheme = theme },
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
                editorEnv = editorEnvironment,
                readLauncherBackup = viewModel::readLauncherBackup,
                onImportLayout = viewModel::importLauncherLayout,
            )
            return
        }
        false -> Unit
    }

    val performGesture: (GestureAction) -> Unit = { action ->
        when (action.kind) {
            GestureKind.None -> Unit
            GestureKind.Drawer -> viewModel.openDrawer()
            GestureKind.Search -> viewModel.openDrawer(withKeyboard = true)
            GestureKind.Notifications -> SystemActions.expandNotifications(context)
            GestureKind.QuickSettings -> SystemActions.expandQuickSettings(context)
            GestureKind.Lock -> if (!LockScreenService.lock()) {
                viewModel.message("Turn on \u201cThemeLauncher: double-tap to lock\u201d in Accessibility settings")
                LockScreenService.openSettings(context)
            }
            GestureKind.ThemeSwitcher -> arcState.openForTap()
            GestureKind.NextTheme -> viewModel.stepTheme(1)
            GestureKind.PreviousTheme -> viewModel.stepTheme(-1)
            GestureKind.OpenApp -> action.appKey?.let(homeState.apps::get)?.let { launchApp(it, null) }
                ?: viewModel.message("The app for this gesture isn't installed")
        }
    }
    val swipeDown: () -> Unit = { performGesture(prefs.swipeDown) }
    val swipeUp: () -> Unit = { performGesture(prefs.swipeUp) }
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
            columns = homeState.activeTheme?.gridLayout?.toIntOrNull() ?: prefs.gridColumns,
            iconSize = prefs.iconSize.dp.dp,
            showLabels = prefs.showLabels,
            showTime = prefs.showTime,
            showDate = prefs.showDate,
            dateFormat = prefs.dateFormat,
            showBattery = prefs.showBattery,
            clockPosition = prefs.clockPosition,
            showThemeLabel = prefs.showThemeLabel,
            autoFit = when (homeState.activeTheme?.gridLayout) {
                null -> prefs.autoFitIcons
                ThemeLook.GRID_FIT -> true
                else -> false
            },
            arrangement = prefs.iconArrangement,
        ),
        LocalNotificationDots provides if (prefs.notificationDots) dotKeys else emptySet(),
        LocalShortcutHost provides shortcutHost,
        LocalWidgetHost provides widgetHost,
        LocalExternalDrag provides externalDrag,
    ) {
        Box(Modifier.fillMaxSize().externalDragTracker(externalDrag)) {
            HomeScreen(
                state = homeState,
                arcState = arcState,
                switcherSide = switcherSide,
                onArcRelease = onArcItem,
                onLaunch = launchApp,
                onOpenItem = openItem,
                onCreateTheme = { editor = EditorRequest(themeId = null) },
                onAddApps = { editor = EditorRequest(it.id) },
                onSwipeUp = swipeUp,
                onSwipeDown = swipeDown,
                gridItemActions = gridActions,
                dockAppActions = dockActions,
                onReorderThemeApps = { theme, keys -> viewModel.reorderThemeApps(theme.id, keys) },
                onMergeItems = { theme, order, dragged, target -> viewModel.mergeItems(theme.id, order, dragged, target) },
                onReorderFavorites = viewModel::reorderFavorites,
                onEmptyLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showHomeOptions = true
                },
                banner = banner,
                onBannerAccept = viewModel::acceptBanner,
                onBannerCustomize = viewModel::customizeBanner,
                onBannerDismiss = viewModel::dismissBanner,
                modifier = Modifier
                    .graphicsLayer {
                        val zoom = homeZoom.value
                        alpha = homeAlpha * (1f - (1f - zoom) * 5f).coerceIn(0f, 1f)
                        scaleX = zoom
                        scaleY = zoom
                    }
                    .blur(homeBlur)
                    // Taps on apps are consumed by the apps, so this only sees empty space.
                    .pointerInput(prefs.doubleTap) {
                        if (prefs.doubleTap.kind != GestureKind.None) detectTapGestures(onDoubleTap = { performGesture(prefs.doubleTap) })
                    }
                    .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { _, delta -> dragged += delta },
                        onDragEnd = {
                            when {
                                dragged < -swipeThresholdPx -> swipeUp()
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
                    onLaunch = launchApp,
                    appActions = drawerActions,
                    onClose = viewModel::closeDrawer,
                    onOpenSettings = {
                        viewModel.closeDrawer()
                        showSettings = true
                    },
                    onSelectProfile = viewModel::selectProfileTab,
                    onSetProfileQuiet = viewModel::setProfileQuiet,
                    searchActions = searchActions,
                    openKeyboard = drawerKeyboard,
                    onSortChange = { sort -> viewModel.updatePrefs { it.copy(drawerSort = sort) } },
                )
            }

            // The app being carried from the drawer, under the finger.
            externalDrag.app?.let { app ->
                val iconSize = LocalHomeLayout.current.iconSize
                val half = with(LocalDensity.current) { iconSize.toPx() / 2 }
                AppIcon(
                    app = app,
                    size = iconSize,
                    modifier = Modifier.graphicsLayer {
                        translationX = externalDrag.position.x - half
                        translationY = externalDrag.position.y - half * 1.6f // a little above the finger, so it stays visible
                        scaleX = 1.15f
                        scaleY = 1.15f
                        shadowElevation = 12f
                    },
                )
            }

            if (showSettings) {
                SettingsScreen(
                    themes = homeState.themes,
                    appCounts = homeState.themes.associate { it.id to homeState.itemCount(it) },
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
                    onImportThemeFile = { uri ->
                        uiScope.launch {
                            viewModel.readSharedTheme(uri)
                                .onSuccess { incomingTheme = IncomingTheme(it, viewModel.resolveSharedTheme(it)) }
                                .onFailure { viewModel.message(it.message ?: "Couldn't open the theme") }
                        }
                    },
                    onPasteTheme = { pastingTheme = true },
                    onImportLauncher = { uri ->
                        uiScope.launch {
                            viewModel.readLauncherBackup(uri)
                                .onSuccess { incomingLayout = it }
                                .onFailure { viewModel.message(it.message ?: "Couldn't read the backup") }
                        }
                    },
                    onResetSuggestions = viewModel::resetSuggestions,
                    onClearHistory = viewModel::clearHistory,
                )
            }
        }
    }

    editor?.let { request ->
        val theme = request.themeId?.let { id -> homeState.themes.firstOrNull { it.id == id } }
        ThemeEditorSheet(
            initial = request.draft ?: theme?.toDraft(),
            allApps = homeState.apps.values.toList(),
            env = editorEnvironment,
            usedColors = homeState.themes.mapTo(HashSet()) { it.accentColor },
            folderMembers = theme?.let(homeState::folderMembers).orEmpty(),
            onFolderAppsRemoved = { keys -> theme?.let { viewModel.removeFromThemeFolders(it.id, keys) } },
            onSave = { draft ->
                editor = null
                if (theme == null) viewModel.createTheme(draft) else viewModel.updateTheme(theme.id, draft)
            },
            onDismiss = { editor = null },
            onShare = theme?.let { { sharingTheme = it } },
        )
    }

    sharingTheme?.let { theme ->
        val iconPacks by produceState(emptyList<IconPackInfo>()) { value = viewModel.installedIconPacks() }
        ShareThemeSheet(
            theme = theme,
            actions = remember(viewModel, iconPacks) {
                ShareActions(
                    build = { t, options -> viewModel.shareJson(t, options, iconPacks.firstOrNull { it.packageName == t.iconPack }?.label) },
                    link = viewModel::shareLink,
                    qrCode = viewModel::qrCode,
                    writeFile = viewModel::shareFile,
                )
            },
            onDismiss = { sharingTheme = null },
        )
    }

    incomingTheme?.let { incoming ->
        ImportThemeDialog(
            shared = incoming.shared,
            resolution = incoming.resolution,
            onImport = { options ->
                incomingTheme = null
                viewModel.installSharedTheme(incoming.shared, incoming.resolution, options)
            },
            onDismiss = { incomingTheme = null },
        )
    }

    if (pastingTheme) {
        PasteThemeDialog(
            onParse = { text ->
                viewModel.parseSharedTheme(text).fold(
                    onSuccess = {
                        pastingTheme = false
                        incomingTheme = IncomingTheme(it, viewModel.resolveSharedTheme(it))
                        null
                    },
                    onFailure = { it.message ?: "That isn't a theme link" },
                )
            },
            onDismiss = { pastingTheme = false },
        )
    }

    incomingLayout?.let { layout ->
        LauncherImportDialog(
            layout = layout,
            onImport = { options ->
                incomingLayout = null
                viewModel.importLauncherLayout(layout, options)
            },
            onDismiss = { incomingLayout = null },
        )
    }

    pendingLaunch?.let { pending ->
        FocusPauseDialog(
            block = pending.block,
            onOpenAnyway = viewModel::proceedPendingLaunch,
            onCancel = viewModel::cancelPendingLaunch,
            onEditTheme = {
                viewModel.cancelPendingLaunch()
                editor = EditorRequest(pending.block.theme.id)
            },
        )
    }

    addToThemesApp?.let { app ->
        AddToThemesDialog(
            app = app,
            themes = homeState.themes,
            alreadyIn = homeState.themesContaining(app.key),
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
            onOpenDrawer = { viewModel.openDrawer() },
            onNewTheme = { editor = EditorRequest(themeId = null) },
            onAddWidget = { showWidgetPicker = true },
            onOpenSettings = { showSettings = true },
            onDismiss = { showHomeOptions = false },
        )
    }

    if (showWidgetPicker) {
        WidgetPickerSheet(
            themeName = homeState.activeTheme?.name ?: stringResource(R.string.this_theme),
            loadProviders = viewModel.widgets::availableProviders,
            onPick = { info ->
                showWidgetPicker = false
                startWidgetAdd(info)
            },
            onDismiss = { showWidgetPicker = false },
        )
    }

    openFolderKey?.let { key ->
        val folder = homeState.activeTheme?.let { theme -> homeState.foldersFor(theme).firstOrNull { it.key == key } }
        if (folder == null) {
            // Emptied, ungrouped or on another theme now.
            LaunchedEffect(key) { openFolderKey = null }
        } else {
            FolderDialog(
                folder = folder,
                onOpenItem = { item, bounds ->
                    openFolderKey = null
                    openItem(item, bounds)
                },
                itemActions = { folderItemActions(folder, it) },
                onRename = { renamingFolder = folder },
                onReorder = { viewModel.reorderFolder(folder, it) },
                onDismiss = { openFolderKey = null },
            )
        }
    }

    addToFolderItem?.let { item ->
        AddToFolderDialog(
            itemLabel = item.label,
            folders = homeState.activeTheme?.let(homeState::foldersFor).orEmpty(),
            onPick = { folder ->
                addToFolderItem = null
                viewModel.moveIntoFolder(item, folder)
            },
            onCreate = { name ->
                addToFolderItem = null
                viewModel.createFolder(item, name)
            },
            onDismiss = { addToFolderItem = null },
        )
    }

    renamingFolder?.let { folder ->
        RenameFolderDialog(
            current = folder.label,
            onConfirm = { name ->
                renamingFolder = null
                viewModel.renameFolder(folder, name)
            },
            onDismiss = { renamingFolder = null },
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

private const val RETURN_ZOOM_FROM = 0.92f
private const val RETURN_ZOOM_MS = 360
