package com.themelauncher.ui.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.themelauncher.data.rules.AutoEndAction
import com.themelauncher.data.rules.AutoPriority
import com.themelauncher.data.rules.RuleMatch
import com.themelauncher.data.rules.RuleSetting
import com.themelauncher.data.rules.RuleText
import com.themelauncher.data.rules.RuleType
import com.themelauncher.data.rules.ThemeAutomation
import com.themelauncher.data.rules.ThemeRule
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Which rule is being edited: [index] in the list, or null for a new one. */
private data class RuleEdit(val index: Int?, val rule: ThemeRule)

/**
 * The theme's conditions (any number of each kind, each switchable on and off) and how it
 * switches: priority, any/all, a delay, what happens when they end, and asking first.
 */
@Composable
fun AutomationSection(
    env: ThemeEditorEnvironment,
    rules: List<RuleSetting>,
    onRulesChange: (List<RuleSetting>) -> Unit,
    automation: ThemeAutomation,
    onAutomationChange: (ThemeAutomation) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RuleEdit?>(null) }
    var choosing by remember { mutableStateOf<String?>(null) }

    fun save(edit: RuleEdit, rule: ThemeRule) {
        onRulesChange(
            if (edit.index == null) rules + RuleSetting(rule) else rules.mapIndexed { i, s -> if (i == edit.index) s.copy(rule = rule) else s },
        )
    }

    Text(
        "Switches to this theme while its conditions apply, and back when they end. Add as many as you like.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rules.forEachIndexed { index, setting ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { editing = RuleEdit(index, setting.rule) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(RuleText.title(setting.rule.type), style = MaterialTheme.typography.bodyLarge)
                Text(
                    RuleText.describe(setting.rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (setting.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRulesChange(rules.filterIndexed { i, _ -> i != index }) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove condition")
            }
            Switch(
                checked = setting.enabled,
                onCheckedChange = { on -> onRulesChange(rules.mapIndexed { i, s -> if (i == index) s.copy(enabled = on) else s }) },
            )
        }
    }
    TextButton(onClick = { picking = true }) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add a condition")
    }

    if (rules.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("How it switches", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ValueRow("Switch when", if (rules.count { it.enabled } < 2) "${automation.match.label} (only one is on)" else automation.match.label) {
            choosing = "match"
        }
        ValueRow("Priority", "${automation.priority.label} – wins over themes with a lower one when both apply") { choosing = "priority" }
        ValueRow("Wait before switching", delayLabel(automation.delayMinutes)) { choosing = "delay" }
        ValueRow("When the conditions end", automation.onEnd.label) { choosing = "end" }
        ToggleRow(
            title = "Ask first",
            subtitle = if (automation.askFirst) "A prompt on the home screen asks before switching" else "Switches without asking",
            checked = automation.askFirst,
            onCheckedChange = { onAutomationChange(automation.copy(askFirst = it)) },
        )
    }

    if (picking) {
        RuleTypeDialog(
            existing = rules.map { it.rule.type }.toSet(),
            onPick = { type ->
                picking = false
                when (type) {
                    RuleType.Controller -> onRulesChange(rules + RuleSetting(ThemeRule.Controller))
                    RuleType.Headphones -> onRulesChange(rules + RuleSetting(ThemeRule.Headphones))
                    RuleType.Charging -> onRulesChange(rules + RuleSetting(ThemeRule.Charging))
                    RuleType.Schedule -> editing = RuleEdit(null, ThemeRule.WORK_HOURS)
                    RuleType.Bluetooth -> editing = RuleEdit(null, ThemeRule.Bluetooth("", ""))
                    RuleType.Wifi -> editing = RuleEdit(null, ThemeRule.Wifi(""))
                    RuleType.Location -> editing = RuleEdit(null, ThemeRule.Location("", 0.0, 0.0, ThemeRule.DEFAULT_RADIUS_M))
                    RuleType.Calendar -> editing = RuleEdit(null, ThemeRule.Calendar())
                }
            },
            onDismiss = { picking = false },
        )
    }

    editing?.let { edit ->
        val done: (ThemeRule) -> Unit = { rule ->
            editing = null
            save(edit, rule)
        }
        val cancel = { editing = null }
        when (val rule = edit.rule) {
            is ThemeRule.Schedule -> ScheduleDialog(rule, done, cancel)
            is ThemeRule.Bluetooth -> BluetoothDialog(env, done, cancel)
            is ThemeRule.Wifi -> WifiDialog(env, rule.ssid.ifBlank { null }, done, cancel)
            is ThemeRule.Location -> LocationDialog(env, rule, isNew = edit.index == null, done, cancel)
            is ThemeRule.Calendar -> CalendarDialog(env, rule, done, cancel)
            // Nothing to set up for these.
            ThemeRule.Controller, ThemeRule.Headphones, ThemeRule.Charging -> cancel()
        }
    }

    when (choosing) {
        "match" -> ChoiceDialog(
            title = "Switch when",
            options = RuleMatch.entries,
            selected = automation.match,
            label = { it.label },
            onSelect = {
                choosing = null
                onAutomationChange(automation.copy(match = it))
            },
            onDismiss = { choosing = null },
        )
        "priority" -> ChoiceDialog(
            title = "Priority",
            options = AutoPriority.entries.reversed(),
            selected = automation.priority,
            label = { it.label },
            onSelect = {
                choosing = null
                onAutomationChange(automation.copy(priority = it))
            },
            onDismiss = { choosing = null },
        )
        "delay" -> ChoiceDialog(
            title = "Wait before switching",
            options = ThemeAutomation.DELAY_CHOICES,
            selected = automation.delayMinutes,
            label = ::delayLabel,
            onSelect = {
                choosing = null
                onAutomationChange(automation.copy(delayMinutes = it))
            },
            onDismiss = { choosing = null },
        )
        "end" -> ChoiceDialog(
            title = "When the conditions end",
            options = AutoEndAction.entries,
            selected = automation.onEnd,
            label = { it.label },
            onSelect = {
                choosing = null
                onAutomationChange(automation.copy(onEnd = it))
            },
            onDismiss = { choosing = null },
        )
    }
}

private fun delayLabel(minutes: Int) = when (minutes) {
    0 -> "Switch right away"
    1 -> "1 minute (the conditions must hold that long)"
    else -> "$minutes minutes (the conditions must hold that long)"
}

/** Kinds without settings can only be added once; the rest as often as you like. */
@Composable
private fun RuleTypeDialog(existing: Set<RuleType>, onPick: (RuleType) -> Unit, onDismiss: () -> Unit) {
    val once = setOf(RuleType.Controller, RuleType.Headphones, RuleType.Charging)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a condition") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    RuleType.Schedule, RuleType.Location, RuleType.Wifi, RuleType.Calendar,
                    RuleType.Bluetooth, RuleType.Headphones, RuleType.Controller, RuleType.Charging,
                ).forEach { type ->
                    val enabled = type !in once || type !in existing
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = enabled) { onPick(type) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            RuleText.title(type),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        Text(
                            if (enabled) RuleText.hint(type) else "Already added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(initial: ThemeRule.Schedule, onDone: (ThemeRule) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(initial.days) }
    var start by remember { mutableStateOf(initial.startMinute) }
    var end by remember { mutableStateOf(initial.endMinute) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule") },
        text = {
            Column {
                Text("Days", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { days = DayOfWeek.entries.take(5).toSet() }) { Text("Weekdays") }
                    TextButton(onClick = { days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }) { Text("Weekends") }
                    TextButton(onClick = { days = DayOfWeek.entries.toSet() }) { Text("Every day") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingTime = true }) { Text("From ${RuleText.minute(start)}") }
                    OutlinedButton(onClick = { editingTime = false }) { Text("To ${RuleText.minute(end)}") }
                }
                if (end <= start) {
                    Text(
                        "Ends the next morning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(ThemeRule.Schedule(days, start, end)) }, enabled = days.isNotEmpty() && start != end) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    editingTime?.let { isStart ->
        val minute = if (isStart) start else end
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text(if (isStart) "Start time" else "End time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.hour * 60 + state.minute
                    if (isStart) start = picked else end = picked
                    editingTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingTime = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BluetoothDialog(env: ThemeEditorEnvironment, onDone: (ThemeRule) -> Unit, onDismiss: () -> Unit) {
    var granted by remember { mutableStateOf(env.hasBluetoothPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val devices = remember(granted) { if (granted) env.pairedDevices() else emptyList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a device") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    !granted -> {
                        Text("ThemeLauncher needs permission to see your Bluetooth devices.")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            TextButton(onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Allow") }
                        }
                    }
                    devices.isEmpty() -> Text("No paired Bluetooth devices. Pair one in Settings first.")
                }
                devices.forEach { device ->
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDone(ThemeRule.Bluetooth(device.address, device.name)) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick the Wi-Fi network: the connected one with a tap, or any name typed in. */
@Composable
private fun WifiDialog(env: ThemeEditorEnvironment, initial: String?, onDone: (ThemeRule) -> Unit, onDismiss: () -> Unit) {
    var canDetect by remember { mutableStateOf(env.hasLocationPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            env.onLocationGranted()
            canDetect = true
        }
    }
    val current = remember(canDetect) { env.currentWifiSsid() }
    var name by rememberSaveable { mutableStateOf(initial ?: current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi network") },
        text = {
            Column {
                when {
                    current != null -> {
                        Text("Connected to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { name = current }) { Text(current) }
                    }
                    canDetect -> Text(
                        "Not connected to Wi-Fi right now. Type the network's name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Text(
                            "Without location access, Android hides network names, so this rule can't tell which network you're on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }) { Text("Allow location access") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Network name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(ThemeRule.Wifi(name.trim())) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationDialog(
    env: ThemeEditorEnvironment,
    initial: ThemeRule.Location,
    isNew: Boolean,
    onDone: (ThemeRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var latitude by rememberSaveable { mutableStateOf(if (isNew) "" else initial.latitude.toString()) }
    var longitude by rememberSaveable { mutableStateOf(if (isNew) "" else initial.longitude.toString()) }
    var radius by rememberSaveable { mutableStateOf(initial.radiusMeters) }
    var locating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(env.hasLocationPermission()) }
    var background by remember { mutableStateOf(env.hasBackgroundLocation()) }

    fun locate() {
        locating = true
        status = null
        scope.launch {
            val fix = env.currentLocation()
            locating = false
            if (fix == null) {
                status = "Couldn't get your location. Is location turned on?"
            } else {
                latitude = "%.6f".format(Locale.US, fix.latitude)
                longitude = "%.6f".format(Locale.US, fix.longitude)
                status = "Accurate to about ${fix.accuracyMeters.toInt()} m"
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
        if (granted) {
            env.onLocationGranted()
            locate()
        }
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        background = env.hasBackgroundLocation()
        if (background) env.onLocationGranted()
    }

    val lat = latitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon = longitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name (e.g. Gym)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (granted) {
                            locate()
                        } else {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    enabled = !locating,
                ) { Text(if (locating) "Finding you…" else "Use my current location") }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                Text("Or enter coordinates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        isError = latitude.isNotBlank() && lat == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        isError = longitude.isNotBlank() && lon == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Within", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RADII.forEach { r ->
                        FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text(RuleText.distance(r)) })
                    }
                }
                if (granted && !background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Without “Allow all the time”, Android only tells ThemeLauncher where you are while it's on screen, " +
                            "so the switch happens when you next go home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                        Text("Allow all the time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(ThemeRule.Location(name.trim(), lat!!, lon!!, radius)) },
                enabled = lat != null && lon != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarDialog(env: ThemeEditorEnvironment, initial: ThemeRule.Calendar, onDone: (ThemeRule) -> Unit, onDismiss: () -> Unit) {
    var granted by remember { mutableStateOf(env.hasCalendarPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) env.onCalendarGranted()
    }
    val calendars = remember(granted) { if (granted) env.calendars() else emptyList() }
    var chosen by remember { mutableStateOf(initial.calendarIds) }
    var keywords by rememberSaveable { mutableStateOf(initial.keywords.joinToString(", ")) }
    var allDay by remember { mutableStateOf(initial.includeAllDay) }
    var busyOnly by remember { mutableStateOf(initial.busyOnly) }
    var before by remember { mutableStateOf(initial.minutesBefore) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendar events") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!granted) {
                    Text("ThemeLauncher needs permission to read your calendar. Events never leave your phone.")
                    TextButton(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Title contains (optional)") },
                    supportingText = { Text("Separate words with commas; any of them matches. Empty matches every event.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (calendars.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Calendars", style = MaterialTheme.typography.titleSmall)
                    CheckRow("All calendars", chosen.isEmpty()) { chosen = emptySet() }
                    calendars.forEach { calendar ->
                        CheckRow("${calendar.name} · ${calendar.account}", calendar.id in chosen) {
                            chosen = if (calendar.id in chosen) chosen - calendar.id else chosen + calendar.id
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Switch early", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15, 30).forEach { m ->
                        FilterChip(selected = before == m, onClick = { before = m }, label = { Text(if (m == 0) "At the start" else "$m min before") })
                    }
                }
                CheckRow("Include all-day events", allDay) { allDay = !allDay }
                CheckRow("Only events marked busy", busyOnly) { busyOnly = !busyOnly }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone(
                    ThemeRule.Calendar(
                        calendarIds = chosen,
                        keywords = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        includeAllDay = allDay,
                        busyOnly = busyOnly,
                        minutesBefore = before,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private val RADII = listOf(50, 100, 150, 250, 500, 1000, 2000)
