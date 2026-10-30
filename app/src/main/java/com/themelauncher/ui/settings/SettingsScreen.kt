package com.themelauncher.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info

import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.icons.IconPackInfo
import com.themelauncher.data.notifications.NotificationDotsRepository
import com.themelauncher.data.settings.ClockPosition
import com.themelauncher.data.settings.DateFormatOption
import com.themelauncher.data.settings.IconSize
import com.themelauncher.data.settings.LauncherPrefs
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.system.LockScreenService
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.home.formatDate
import com.themelauncher.ui.onboarding.rememberDefaultHomeState
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector
import java.time.LocalDate

/** Launcher settings: theme switcher, themes, home screen, apps, backup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themes: List<Theme>,
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
) {
    val context = LocalContext.current
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
    val iconPacks by produceState(emptyList<IconPackInfo>()) { value = loadIconPacks() }
    var lockServiceOn by remember { mutableStateOf(LockScreenService.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lockServiceOn = LockScreenService.isEnabled(context) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onUpdatePrefs { it.copy(contactSearch = granted) }
    }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launcher settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Theme switcher") }
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        "Swipe from the bar on the screen edge toward the middle, then release on a theme. Tap the bar to pick with a tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Bar position", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SwitcherSide.entries.forEachIndexed { index, side ->
                            SegmentedButton(
                                selected = side == prefs.switcherSide,
                                onClick = { onUpdatePrefs { it.copy(switcherSide = side) } },
                                shape = SegmentedButtonDefaults.itemShape(index, SwitcherSide.entries.size),
                            ) { Text(if (side == SwitcherSide.Left) "Left edge" else "Right edge") }
                        }
                    }
                }
            }

            item { SectionHeader("Themes") }
            itemsIndexed(themes, key = { _, theme -> theme.id }) { index, theme ->
                ThemeRow(
                    theme = theme,
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
            item { Option(Icons.Rounded.Add, "New theme", onClick = onNewTheme) }

            item { SectionHeader("Clock & status bar") }
            item {
                Text(
                    if (prefs.showTime || prefs.showDate || prefs.showBattery || prefs.showThemeLabel) {
                        "Show any mix at the top of the home screen."
                    } else {
                        "All off \u2014 apps use the whole screen."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            item {
                SwitchOption(
                    Icons.Outlined.Schedule,
                    "Time",
                    "Large, at the top",
                    prefs.showTime,
                ) { on -> onUpdatePrefs { it.copy(showTime = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.CalendarToday,
                    "Date",
                    if (prefs.showTime) "Under the time" else "At the top",
                    prefs.showDate,
                ) { on -> onUpdatePrefs { it.copy(showDate = on) } }
            }
            item {
                ListItem(
                    headlineContent = { Text("Date format") },
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
                    "Battery percentage",
                    if (prefs.showDate) "Next to the date" else if (prefs.showTime) "Under the time" else "At the top",
                    prefs.showBattery,
                ) { on -> onUpdatePrefs { it.copy(showBattery = on) } }
            }
            item {
                SwitchOption(
                    Icons.Outlined.Style,
                    "Theme name",
                    if (prefs.showThemeLabel) "Pill with the active theme; tap it to switch" else "Hidden — use the edge bar to switch themes",
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
                    Text("Position", style = MaterialTheme.typography.bodyLarge)
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
                    "Status bar",
                    if (prefs.showStatusBar) "Shown on the home screen" else "Hidden \u2014 swipe down from the top edge to see it",
                    prefs.showStatusBar,
                ) { on -> onUpdatePrefs { it.copy(showStatusBar = on) } }
            }

            item { SectionHeader("Home screen") }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Grid columns", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${prefs.gridColumns}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val range = LauncherPrefs.COLUMN_RANGE
                    Slider(
                        value = prefs.gridColumns.toFloat(),
                        onValueChange = { value -> onUpdatePrefs { it.copy(gridColumns = value.toInt()) } },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = range.last - range.first - 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Icon size", style = MaterialTheme.typography.bodyLarge)
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
                SwitchOption(Icons.AutoMirrored.Outlined.Label, "App labels", "Show names under icons on the home screen", prefs.showLabels) { on ->
                    onUpdatePrefs { it.copy(showLabels = on) }
                }
            }
            item {
                Option(
                    Icons.Outlined.Palette,
                    "Icon pack",
                    iconPacks.firstOrNull { it.packageName == prefs.iconPack }?.label
                        ?: if (iconPacks.isEmpty()) "None installed. Get one from the Play Store." else "App icons (none)",
                ) { showIconPacks = true }
            }
            item {
                SwitchOption(
                    icon = Icons.Outlined.TouchApp,
                    title = "Double-tap to lock",
                    subtitle = when {
                        prefs.doubleTapToLock && !lockServiceOn -> "Needs the accessibility service \u2014 tap to turn it on"
                        prefs.doubleTapToLock -> "Double-tap an empty part of the home screen"
                        else -> "Off"
                    },
                    checked = prefs.doubleTapToLock,
                    subtitleIsWarning = prefs.doubleTapToLock && !lockServiceOn,
                    onClick = if (prefs.doubleTapToLock && !lockServiceOn) ({ explainLock = true }) else null,
                ) { on ->
                    onUpdatePrefs { it.copy(doubleTapToLock = on) }
                    if (on && !lockServiceOn) explainLock = true
                }
            }
            item { Option(Icons.Outlined.Widgets, "Add widget", "To the theme you're on", onClick = onAddWidget) }
            item {
                val needsAccess = prefs.notificationDots && !hasDotAccess
                SwitchOption(
                    icon = Icons.Outlined.Badge,
                    title = "Notification dots",
                    subtitle = when {
                        needsAccess -> "Needs notification access — tap to allow"
                        prefs.notificationDots -> "A dot on apps with new notifications"
                        else -> "Off"
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
                SwitchOption(
                    Icons.Outlined.SwipeDown,
                    "Swipe down for notifications",
                    "Swipe down anywhere on the home screen",
                    prefs.swipeDownForNotifications,
                ) { on -> onUpdatePrefs { it.copy(swipeDownForNotifications = on) } }
            }
            item {
                Option(Icons.Outlined.Wallpaper, "Wallpaper") {
                    try {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }
            item {
                if (defaultHome.isDefault) {
                    Option(Icons.Rounded.CheckCircle, "Default home app", "ThemeLauncher is your home app", onClick = null)
                } else {
                    Option(Icons.Outlined.Home, "Set as default home app", "So the Home button opens ThemeLauncher", onClick = defaultHome.request)
                }
            }

            item { SectionHeader("Apps") }
            item {
                Option(
                    Icons.Outlined.VisibilityOff,
                    "Hidden apps",
                    when (prefs.hiddenApps.size) {
                        0 -> "None hidden. Long-press an app in the drawer to hide it."
                        1 -> "1 app hidden from the drawer"
                        else -> "${prefs.hiddenApps.size} apps hidden from the drawer"
                    },
                ) { showHiddenApps = true }
            }

            item {
                SwitchOption(
                    icon = Icons.Outlined.Contacts,
                    title = "Search contacts",
                    subtitle = when {
                        !prefs.contactSearch -> "Off"
                        hasContactsPermission(context) -> "Matching contacts appear in drawer search"
                        else -> "Needs permission to read contacts"
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

            item { SectionHeader("Backup") }
            item {
                Option(Icons.Outlined.Upload, "Back up themes", "Save themes, dock and settings to a file") {
                    exportLauncher.launch("themelauncher-${LocalDate.now()}.json")
                }
            }
            item {
                Option(Icons.Outlined.Download, "Restore from backup", "Replaces your current themes, dock and settings") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
            item { Option(Icons.Outlined.Restore, "Run setup again", "Pick favorites and add themes from presets", onClick = onRunSetup) }

            item { SectionHeader("About") }
            item { Option(Icons.Outlined.Info, "ThemeLauncher", versionName?.let { "Version $it" }, onClick = null) }
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
            title = { Text("Date format") },
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
            confirmButton = { TextButton(onClick = { showDateFormats = false }) { Text("Close") } },
        )
    }

    if (showIconPacks) {
        AlertDialog(
            onDismissRequest = { showIconPacks = false },
            title = { Text("Icon pack") },
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
                            Text(pack?.label ?: "App icons (none)")
                        }
                    }
                    if (iconPacks.isEmpty()) {
                        Text(
                            "Icon packs made for Nova, ADW or Apex launchers work here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIconPacks = false }) { Text("Close") } },
        )
    }

    if (explainLock) {
        AlertDialog(
            onDismissRequest = { explainLock = false },
            title = { Text("Turn on double-tap to lock") },
            text = {
                Text(
                    "Android only lets apps lock the screen through an accessibility service. " +
                        "On the next screen, open \u201cThemeLauncher: double-tap to lock\u201d and turn it on.\n\n" +
                        "It is used only to lock the screen. It can't read the screen or what you type.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainLock = false
                    LockScreenService.openSettings(context)
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { explainLock = false }) { Text("Not now") } },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore from backup?") },
            text = { Text("Your current themes, dock and launcher settings will be replaced by the ones in the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    onImport(uri)
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
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
            .clickable(onClick = onEdit, onClickLabel = "Edit ${theme.name}")
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
                if (theme.appKeys.size == 1) "1 app" else "${theme.appKeys.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${theme.name} up")
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${theme.name} down")
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit ${theme.name}") }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${theme.name}")
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
