package com.phaseslauncher.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.phaseslauncher.R
import com.phaseslauncher.api.PhaseApi
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.icons.IconPackInfo
import com.phaseslauncher.data.notifications.NotificationDotsRepository
import com.phaseslauncher.data.settings.AppearanceMode
import com.phaseslauncher.data.settings.ClockPosition
import com.phaseslauncher.data.settings.DateFormatOption
import com.phaseslauncher.data.settings.DrawerSort
import com.phaseslauncher.data.settings.IconArrangement
import com.phaseslauncher.data.settings.IconSize
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.settings.SwitcherStyle
import com.phaseslauncher.data.system.LockScreenService
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.home.formatDate
import com.phaseslauncher.ui.onboarding.rememberDefaultHomeState
import com.phaseslauncher.ui.theme.contentColorOn
import com.phaseslauncher.ui.theme.imageVector
import java.time.LocalDate

/** Launcher settings: theme switcher, themes, home screen, apps, backup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themes: List<Theme>,
    /** Apps on each theme (by id), counting those in folders. */
    appCounts: Map<Long, Int>,
    prefs: LauncherPrefs,
    allApps: List<AppInfo>,
    notificationDots: NotificationDotsRepository,
    onUpdatePrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    onNewTheme: () -> Unit,
    onEditTheme: (Theme) -> Unit,
    onDeleteTheme: (Theme) -> Unit,
    onReorderThemes: (List<Long>) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onRunSetup: () -> Unit,
    onClose: () -> Unit,
    loadIconPacks: suspend () -> List<IconPackInfo>,
    onAddWidget: () -> Unit,
    onImportThemeFile: (Uri) -> Unit = {},
    onPasteTheme: () -> Unit = {},
    onImportLauncher: (Uri) -> Unit = {},
    onResetSuggestions: () -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val defaultHome = rememberDefaultHomeState()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    var hasDotAccess by remember { mutableStateOf(notificationDots.hasAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasDotAccess = notificationDots.hasAccess() }

    var showHiddenApps by remember { mutableStateOf(false) }
    var showIconPacks by remember { mutableStateOf(false) }
    var explainLock by remember { mutableStateOf(false) }
    var showDateFormats by remember { mutableStateOf(false) }
    var showArrangements by remember { mutableStateOf(false) }
    var showSwitcherStyles by remember { mutableStateOf(false) }
    val iconPacks by produceState(emptyList<IconPackInfo>()) { value = loadIconPacks() }
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    var lockServiceOn by remember { mutableStateOf(LockScreenService.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lockServiceOn = LockScreenService.isEnabled(context) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onUpdatePrefs { it.copy(contactSearch = granted) }
    }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var choosingMinLaunches by remember { mutableStateOf(false) }
    val themeFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportThemeFile)
    }
    val launcherBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportLauncher)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    BackHandler(onBack = onClose)

    // Large title that collapses as the list scrolls, like the system Settings app.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.launcher_settings)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.close_settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.appearance)) }
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.appearance_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AppearanceMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == prefs.appearance,
                                onClick = { onUpdatePrefs { it.copy(appearance = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index, AppearanceMode.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.theme_switcher)) }
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.swipe_from_the_bar_on_the_screen_edge_toward_the),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.bar_position), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SwitcherSide.entries.forEachIndexed { index, side ->
                            SegmentedButton(
                                selected = side == prefs.switcherSide,
                                onClick = { onUpdatePrefs { it.copy(switcherSide = side) } },
                                shape = SegmentedButtonDefaults.itemShape(index, SwitcherSide.entries.size),
                            ) { Text(if (side == SwitcherSide.Left) stringResource(R.string.left_edge) else stringResource(R.string.right_edge)) }
                        }
                    }
                }
            }
            item {
                Option(Icons.Outlined.Swipe, stringResource(R.string.switcher_style), prefs.switcherStyle.label) {
                    showSwitcherStyles = true
                }
            }

            item { SectionHeader(stringResource(R.string.themes)) }
            itemsIndexed(themes, key = { _, theme -> theme.id }) { index, theme ->
                ThemeRow(
                    theme = theme,
                    appCount = appCounts[theme.id] ?: theme.appKeys.size,
                    canMoveUp = index > 0,
                    canMoveDown = index < themes.lastIndex,
                    canDelete = themes.size > 1,
                    onMove = { delta ->
                        val ids = themes.map { it.id }.toMutableList()
                        ids.add(index + delta, ids.removeAt(index))
                        onReorderThemes(ids)
                    },
                    onEdit = { onEditTheme(theme) },
                    onDelete = { onDeleteTheme(theme) },
                    modifier = Modifier.animateItem(),
                )
            }
            item { Option(Icons.Rounded.Add, stringResource(R.string.new_theme), onClick = onNewTheme) }
            item {
                Option(Icons.Outlined.Download, stringResource(R.string.import_a_phase_file), stringResource(R.string.one_someone_shared_with_you)) {
                    themeFileLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
            item { Option(Icons.Outlined.Link, stringResource(R.string.paste_a_phase_link), stringResource(R.string.phases_from_a_message), onClick = onPasteTheme) }
            item {
                Option(
                    Icons.Outlined.SettingsBackupRestore,
                    stringResource(R.string.import_from_another_launcher),
                    stringResource(R.string.nova_novabackup_lawnchair_and_other_launcher3_ba),
                ) { launcherBackupLauncher.launch(arrayOf("*/*")) }
            }

            item { SectionHeader(stringResource(R.string.suggestions)) }
            item {
                Text(
                    stringResource(R.string.phases_learns_from_the_apps_you_open_and_the_pha),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            item {
                SwitchOption(
                    Icons.Outlined.AddToHomeScreen,
                    stringResource(R.string.apps_for_a_phase),
                    if (prefs.suggestApps) stringResource(R.string.suggest_adding_an_app_you_keep_opening_on_a_phas) else stringResource(R.string.off),
                    prefs.suggestApps,
                ) { on -> onUpdatePrefs { it.copy(suggestApps = on) } }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.suggest_after)) },
                    supportingContent = { Text(pluralStringResource(R.plurals.opens_in_two_weeks, prefs.suggestionMinLaunches, prefs.suggestionMinLaunches)) },
                    leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable(enabled = prefs.suggestApps) { choosingMinLaunches = true }
                        .alpha(if (prefs.suggestApps) 1f else 0.38f)
                        .padding(horizontal = 8.dp),
                )
            }
            item {
                SwitchOption(
                    Icons.Outlined.Schedule,
                    stringResource(R.string.schedules),
                    if (prefs.suggestSchedules) stringResource(R.string.suggest_a_schedule_for_a_phase_you_pick_around_t) else stringResource(R.string.off),
                    prefs.suggestSchedules,
                ) { on -> onUpdatePrefs { it.copy(suggestSchedules = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.SwapHoriz,
                    stringResource(R.string.switching),
                    if (prefs.suggestSwitching) stringResource(R.string.offer_to_switch_when_you_open_an_app_from_anothe) else stringResource(R.string.off),
                    prefs.suggestSwitching,
                ) { on -> onUpdatePrefs { it.copy(suggestSwitching = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.InstallMobile,
                    stringResource(R.string.new_apps),
                    if (prefs.askForNewApps) stringResource(R.string.ask_which_phases_a_newly_installed_app_goes_on) else stringResource(R.string.off),
                    prefs.askForNewApps,
                ) { on -> onUpdatePrefs { it.copy(askForNewApps = on) } }
            }
            item {
                Option(
                    Icons.Outlined.Restore,
                    stringResource(R.string.show_dismissed_suggestions_again),
                    if (prefs.dismissedSuggestions.isEmpty()) stringResource(R.string.none_dismissed) else pluralStringResource(R.plurals.n_dismissed, prefs.dismissedSuggestions.size, prefs.dismissedSuggestions.size),
                    onClick = if (prefs.dismissedSuggestions.isEmpty()) null else onResetSuggestions,
                )
            }
            item { Option(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_activity_history), stringResource(R.string.forget_the_launches_and_switches_suggestions_lea), onClick = onClearHistory) }

            item { SectionHeader(stringResource(R.string.automation_apps)) }
            item {
                SwitchOption(
                    Icons.Outlined.SettingsRemote,
                    stringResource(R.string.other_apps_can_switch_phases),
                    if (prefs.allowExternalSwitching) stringResource(R.string.tasker_and_similar_apps_can_switch_phases) else stringResource(R.string.off),
                    prefs.allowExternalSwitching,
                ) { on -> onUpdatePrefs { it.copy(allowExternalSwitching = on) } }
            }
            item {
                // Selectable, so the action and package can be copied into Tasker.
                SelectionContainer {
                    Text(
                        stringResource(R.string.automation_apps_help, PhaseApi.ACTION_SWITCH_PHASE, context.packageName, PhaseApi.EXTRA_PHASE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.clock_status_bar)) }
            item {
                Text(
                    if (prefs.showTime || prefs.showDate || prefs.showBattery || prefs.showThemeLabel) {
                        stringResource(R.string.show_any_mix_at_the_top_of_the_home_screen)
                    } else {
                        stringResource(R.string.all_off_apps_use_the_whole_screen)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            item {
                SwitchOption(
                    Icons.Outlined.Schedule,
                    stringResource(R.string.time),
                    stringResource(R.string.large_at_the_top),
                    prefs.showTime,
                ) { on -> onUpdatePrefs { it.copy(showTime = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.CalendarToday,
                    stringResource(R.string.date),
                    if (prefs.showTime) stringResource(R.string.under_the_time) else stringResource(R.string.at_the_top),
                    prefs.showDate,
                ) { on -> onUpdatePrefs { it.copy(showDate = on) } }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.date_format)) },
                    supportingContent = { Text(formatDate(prefs.dateFormat)) },
                    leadingContent = { Icon(Icons.Outlined.EditCalendar, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable(enabled = prefs.showDate) { showDateFormats = true }
                        .alpha(if (prefs.showDate) 1f else 0.38f)
                        .padding(horizontal = 8.dp),
                )
            }
            item {
                SwitchOption(
                    Icons.Outlined.BatteryStd,
                    stringResource(R.string.battery_percentage),
                    if (prefs.showDate) stringResource(R.string.next_to_the_date) else if (prefs.showTime) stringResource(R.string.under_the_time) else stringResource(R.string.at_the_top),
                    prefs.showBattery,
                ) { on -> onUpdatePrefs { it.copy(showBattery = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.Style,
                    stringResource(R.string.theme_name),
                    if (prefs.showThemeLabel) stringResource(R.string.pill_with_the_active_theme_tap_it_to_switch) else stringResource(R.string.hidden_use_the_edge_bar_to_switch_themes),
                    prefs.showThemeLabel,
                ) { on -> onUpdatePrefs { it.copy(showThemeLabel = on) } }
            }
            item {
                val anyShown = prefs.showTime || prefs.showDate || prefs.showBattery || prefs.showThemeLabel
                Column(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .alpha(if (anyShown) 1f else 0.38f),
                ) {
                    Text(stringResource(R.string.position), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ClockPosition.entries.forEachIndexed { index, position ->
                            SegmentedButton(
                                selected = position == prefs.clockPosition,
                                onClick = { onUpdatePrefs { it.copy(clockPosition = position) } },
                                enabled = anyShown,
                                shape = SegmentedButtonDefaults.itemShape(index, ClockPosition.entries.size),
                            ) { Text(position.label) }
                        }
                    }
                }
            }
            item {
                SwitchOption(
                    Icons.Outlined.ViewDay,
                    stringResource(R.string.status_bar),
                    if (prefs.showStatusBar) stringResource(R.string.shown_on_the_home_screen) else stringResource(R.string.hidden_swipe_down_from_the_top_edge_to_see_it),
                    prefs.showStatusBar,
                ) { on -> onUpdatePrefs { it.copy(showStatusBar = on) } }
            }

            item { SectionHeader(stringResource(R.string.home_screen)) }
            item {
                SwitchOption(
                    Icons.Outlined.Dock,
                    stringResource(R.string.dock),
                    if (prefs.showDock) stringResource(R.string.favorites_at_the_bottom_of_the_home_screen) else stringResource(R.string.hidden_apps_get_the_room),
                    prefs.showDock,
                ) { on -> onUpdatePrefs { it.copy(showDock = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.FitScreen,
                    stringResource(R.string.fit_icons_to_the_screen),
                    if (prefs.autoFitIcons) {
                        stringResource(R.string.icons_grow_or_shrink_so_each_theme_fits_without)
                    } else {
                        stringResource(R.string.off_fixed_columns_and_icon_size)
                    },
                    prefs.autoFitIcons,
                ) { on -> onUpdatePrefs { it.copy(autoFitIcons = on) } }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Row(Modifier.alpha(if (prefs.autoFitIcons) 0.38f else 1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.grid_columns), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            if (prefs.autoFitIcons) stringResource(R.string.auto) else "${prefs.gridColumns}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val range = LauncherPrefs.COLUMN_RANGE
                    Slider(
                        value = prefs.gridColumns.toFloat(),
                        onValueChange = { value -> onUpdatePrefs { it.copy(gridColumns = value.toInt()) } },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = range.last - range.first - 1,
                        enabled = !prefs.autoFitIcons,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Fitted home icons size themselves; the setting still sizes the dock.
                    Text(if (prefs.autoFitIcons) stringResource(R.string.dock_icon_size) else stringResource(R.string.icon_size), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        IconSize.entries.forEachIndexed { index, size ->
                            SegmentedButton(
                                selected = size == prefs.iconSize,
                                onClick = { onUpdatePrefs { it.copy(iconSize = size) } },
                                shape = SegmentedButtonDefaults.itemShape(index, IconSize.entries.size),
                            ) { Text(size.label) }
                        }
                    }
                }
            }
            item {
                Option(Icons.Outlined.GridView, stringResource(R.string.icon_arrangement), prefs.iconArrangement.label) {
                    showArrangements = true
                }
            }
            item {
                SwitchOption(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.app_labels), stringResource(R.string.show_names_under_icons_on_the_home_screen), prefs.showLabels) { on ->
                    onUpdatePrefs { it.copy(showLabels = on) }
                }
            }
            item {
                Option(
                    Icons.Outlined.Palette,
                    stringResource(R.string.icon_pack),
                    iconPacks.firstOrNull { it.packageName == prefs.iconPack }?.label
                        ?: if (iconPacks.isEmpty()) stringResource(R.string.none_installed_get_one_from_the_play_store) else stringResource(R.string.app_icons_none),
                ) { showIconPacks = true }
            }
            item {
                val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                SwitchOption(
                    icon = Icons.Outlined.InvertColors,
                    title = stringResource(R.string.themed_icons),
                    subtitle = when {
                        !supported -> stringResource(R.string.needs_android_13_or_newer)
                        prefs.themedIcons -> stringResource(R.string.icons_take_the_color_of_the_theme_you_re_on_apps)
                        else -> stringResource(R.string.off)
                    },
                    checked = prefs.themedIcons && supported,
                    enabled = supported,
                ) { on -> onUpdatePrefs { it.copy(themedIcons = on) } }
            }
            item { Option(Icons.Outlined.Widgets, stringResource(R.string.add_widget), stringResource(R.string.to_the_theme_you_re_on), onClick = onAddWidget) }
            item {
                val needsAccess = prefs.notificationDots && !hasDotAccess
                SwitchOption(
                    icon = Icons.Outlined.Badge,
                    title = stringResource(R.string.notification_dots),
                    subtitle = when {
                        needsAccess -> stringResource(R.string.needs_notification_access_tap_to_allow)
                        prefs.notificationDots -> stringResource(R.string.a_dot_on_apps_with_new_notifications)
                        else -> stringResource(R.string.off)
                    },
                    checked = prefs.notificationDots,
                    subtitleIsWarning = needsAccess,
                    onClick = if (needsAccess) ({ notificationDots.openAccessSettings(context) }) else null,
                ) { on ->
                    onUpdatePrefs { it.copy(notificationDots = on) }
                    if (on && !hasDotAccess) notificationDots.openAccessSettings(context)
                }
            }
            item {
                Option(Icons.Outlined.Wallpaper, stringResource(R.string.wallpaper)) {
                    try {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), res.getString(R.string.set_wallpaper))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }
            item {
                if (defaultHome.isDefault) {
                    Option(Icons.Rounded.CheckCircle, stringResource(R.string.default_home_app), stringResource(R.string.phases_is_your_home_app), onClick = null)
                } else {
                    Option(Icons.Outlined.Home, stringResource(R.string.set_as_default_home_app), stringResource(R.string.so_the_home_button_opens_phases), onClick = defaultHome.request)
                }
            }

            item { SectionHeader(stringResource(R.string.gestures)) }
            item {
                Text(
                    stringResource(R.string.on_an_empty_part_of_the_home_screen_long_press_i),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            item {
                GestureOption(Icons.Outlined.SwipeUp, stringResource(R.string.swipe_up), prefs.swipeUp, appsByKey, lockServiceOn, { a -> onUpdatePrefs { it.copy(swipeUp = a) } }) {
                    explainLock = true
                }
            }
            item {
                GestureOption(Icons.Outlined.SwipeDown, stringResource(R.string.swipe_down), prefs.swipeDown, appsByKey, lockServiceOn, { a -> onUpdatePrefs { it.copy(swipeDown = a) } }) {
                    explainLock = true
                }
            }
            item {
                GestureOption(Icons.Outlined.TouchApp, stringResource(R.string.double_tap), prefs.doubleTap, appsByKey, lockServiceOn, { a -> onUpdatePrefs { it.copy(doubleTap = a) } }) {
                    explainLock = true
                }
            }

            item { SectionHeader(stringResource(R.string.app_drawer)) }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.sort_apps_by), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DrawerSort.entries.forEachIndexed { index, sort ->
                            SegmentedButton(
                                selected = sort == prefs.drawerSort,
                                onClick = { onUpdatePrefs { it.copy(drawerSort = sort) } },
                                shape = SegmentedButtonDefaults.itemShape(index, DrawerSort.entries.size),
                            ) { Text(sort.shortLabel, maxLines = 1) }
                        }
                    }
                }
            }
            item {
                SwitchOption(
                    Icons.Outlined.Keyboard,
                    stringResource(R.string.open_the_keyboard),
                    if (prefs.drawerKeyboard) stringResource(R.string.the_drawer_opens_ready_to_search) else stringResource(R.string.tap_the_search_bar_to_search_the_search_gesture),
                    prefs.drawerKeyboard,
                ) { on -> onUpdatePrefs { it.copy(drawerKeyboard = on) } }
            }

            item { SectionHeader(stringResource(R.string.apps)) }
            item {
                Option(
                    Icons.Outlined.VisibilityOff,
                    stringResource(R.string.hidden_apps),
                    when (prefs.hiddenApps.size) {
                        0 -> stringResource(R.string.none_hidden_long_press_an_app_in_the_drawer_to_h)
                        else -> pluralStringResource(R.plurals.apps_hidden, prefs.hiddenApps.size, prefs.hiddenApps.size)
                    },
                ) { showHiddenApps = true }
            }

            item {
                SwitchOption(
                    icon = Icons.Outlined.Contacts,
                    title = stringResource(R.string.search_contacts),
                    subtitle = when {
                        !prefs.contactSearch -> stringResource(R.string.off)
                        hasContactsPermission(context) -> stringResource(R.string.matching_contacts_appear_in_drawer_search)
                        else -> stringResource(R.string.needs_permission_to_read_contacts)
                    },
                    checked = prefs.contactSearch && hasContactsPermission(context),
                ) { on ->
                    if (on && !hasContactsPermission(context)) {
                        contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    } else {
                        onUpdatePrefs { it.copy(contactSearch = on) }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.backup)) }
            item {
                Option(Icons.Outlined.Upload, stringResource(R.string.back_up_themes), stringResource(R.string.save_themes_dock_and_settings_to_a_file)) {
                    exportLauncher.launch("phases-${LocalDate.now()}.json")
                }
            }
            item {
                Option(Icons.Outlined.Download, stringResource(R.string.restore_from_backup), stringResource(R.string.replaces_your_current_themes_dock_and_settings)) {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
            item { Option(Icons.Outlined.Restore, stringResource(R.string.run_setup_again), stringResource(R.string.pick_favorites_and_add_themes_from_presets), onClick = onRunSetup) }

            item { SectionHeader(stringResource(R.string.about)) }
            item { Option(Icons.Outlined.Info, stringResource(R.string.phases), versionName?.let { stringResource(R.string.version, it) }, onClick = null) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    if (showHiddenApps) {
        HiddenAppsSheet(
            allApps = allApps,
            hidden = prefs.hiddenApps,
            onSave = { keys ->
                showHiddenApps = false
                onUpdatePrefs { it.copy(hiddenApps = keys) }
            },
            onDismiss = { showHiddenApps = false },
        )
    }

    if (showDateFormats) {
        AlertDialog(
            onDismissRequest = { showDateFormats = false },
            title = { Text(stringResource(R.string.date_format)) },
            text = {
                Column {
                    DateFormatOption.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(prefs.dateFormat == option, role = Role.RadioButton) {
                                    onUpdatePrefs { it.copy(dateFormat = option) }
                                    showDateFormats = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.dateFormat == option, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(formatDate(option))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDateFormats = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (showSwitcherStyles) {
        AlertDialog(
            onDismissRequest = { showSwitcherStyles = false },
            title = { Text(stringResource(R.string.switcher_style)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SwitcherStyle.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(prefs.switcherStyle == option, role = Role.RadioButton) {
                                    onUpdatePrefs { it.copy(switcherStyle = option) }
                                    showSwitcherStyles = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.switcherStyle == option, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(option.label)
                                Text(
                                    stringResource(option.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSwitcherStyles = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (showArrangements) {
        AlertDialog(
            onDismissRequest = { showArrangements = false },
            title = { Text(stringResource(R.string.icon_arrangement)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.icon_arrangement_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    IconArrangement.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(prefs.iconArrangement == option, role = Role.RadioButton) {
                                    onUpdatePrefs { it.copy(iconArrangement = option) }
                                    showArrangements = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.iconArrangement == option, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showArrangements = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (showIconPacks) {
        AlertDialog(
            onDismissRequest = { showIconPacks = false },
            title = { Text(stringResource(R.string.icon_pack)) },
            text = {
                Column {
                    val options = listOf<IconPackInfo?>(null) + iconPacks
                    options.forEach { pack ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(prefs.iconPack == pack?.packageName, role = Role.RadioButton) {
                                    onUpdatePrefs { it.copy(iconPack = pack?.packageName) }
                                    showIconPacks = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.iconPack == pack?.packageName, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(pack?.label ?: stringResource(R.string.app_icons_none))
                        }
                    }
                    if (iconPacks.isEmpty()) {
                        Text(
                            stringResource(R.string.icon_packs_made_for_nova_adw_or_apex_launchers_w),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIconPacks = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (explainLock) {
        AlertDialog(
            onDismissRequest = { explainLock = false },
            title = { Text(stringResource(R.string.turn_on_screen_locking)) },
            text = {
                Text(
                    stringResource(R.string.android_only_lets_apps_lock_the_screen_through_a) +
                        stringResource(R.string.on_the_next_screen_open_phases_double_tap) +
                        stringResource(R.string.it_is_used_only_to_lock_the_screen_it_can_t_read),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainLock = false
                    LockScreenService.openSettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton(onClick = { explainLock = false }) { Text(stringResource(R.string.not_now)) } },
        )
    }

    if (choosingMinLaunches) {
        AlertDialog(
            onDismissRequest = { choosingMinLaunches = false },
            title = { Text(stringResource(R.string.suggest_an_app_after)) },
            text = {
                Column {
                    LauncherPrefs.SUGGESTION_LAUNCH_CHOICES.forEach { count ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(prefs.suggestionMinLaunches == count, role = Role.RadioButton) {
                                    onUpdatePrefs { it.copy(suggestionMinLaunches = count) }
                                    choosingMinLaunches = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.suggestionMinLaunches == count, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(pluralStringResource(R.plurals.opens_in_two_weeks, count, count))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingMinLaunches = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.restore_from_backup_2)) },
            text = { Text(stringResource(R.string.your_current_themes_dock_and_launcher_settings_w)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    onImport(uri)
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun ThemeRow(
    theme: Theme,
    appCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(theme.accentColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit, onClickLabel = stringResource(R.string.edit, theme.name))
            .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(theme.icon.imageVector, contentDescription = null, tint = contentColorOn(accent))
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.apps_count, appCount, appCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up_2, theme.name))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down_2, theme.name))
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit, theme.name)) }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_3, theme.name))
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
    )
}

/** A row with a switch. Tapping the row toggles, unless [onClick] overrides it (e.g. to grant access). */
@Composable
private fun SwitchOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    subtitleIsWarning: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** Greyed out and not toggleable, e.g. a sub-option of a switch that's off. */
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle, color = if (subtitleIsWarning) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(enabled = enabled) { if (onClick != null) onClick() else onCheckedChange(!checked) }
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 8.dp),
    )
}

private fun hasContactsPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private val DrawerSort.shortLabel: String
    @Composable get() = when (this) {
        DrawerSort.Name -> "A\u2013Z"
        DrawerSort.MostUsed -> stringResource(R.string.most_used)
        DrawerSort.Newest -> stringResource(R.string.newest)
    }
