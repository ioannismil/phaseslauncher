package com.themelauncher.ui.editor

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.themelauncher.data.rules.BluetoothDeviceInfo
import com.themelauncher.data.rules.ThemeRule
import com.themelauncher.data.themes.SoundProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** System-dependent bits the editor needs, supplied by the screen. */
class ThemeEditorEnvironment(
    val importWallpaper: suspend (Uri) -> String?,
    val hasPolicyAccess: () -> Boolean,
    val requestPolicyAccess: () -> Unit,
    val hasBluetoothPermission: () -> Boolean,
    val pairedDevices: () -> List<BluetoothDeviceInfo>,
)

/** Wallpaper, dock, sound and automatic-switching options of a theme. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeExtras(
    env: ThemeEditorEnvironment,
    wallpaperPath: String?,
    onWallpaperChange: (String?) -> Unit,
    ownDock: Boolean,
    onOwnDockChange: (Boolean) -> Unit,
    soundProfile: SoundProfile,
    onSoundProfileChange: (SoundProfile) -> Unit,
    rules: List<ThemeRule>,
    onRulesChange: (List<ThemeRule>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { env.importWallpaper(uri)?.let(onWallpaperChange) }
    }
    var policyAccess by remember { mutableStateOf(env.hasPolicyAccess()) }
    var showSoundPicker by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 24.dp)) {
        SectionTitle("Wallpaper")
        Row(verticalAlignment = Alignment.CenterVertically) {
            WallpaperThumb(wallpaperPath)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (wallpaperPath == null) "Keeps the current wallpaper" else "Applied when you switch to this theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (wallpaperPath == null) "Choose image" else "Change")
                    }
                    if (wallpaperPath != null) TextButton(onClick = { onWallpaperChange(null) }) { Text("Remove") }
                }
            }
        }

        SectionTitle("Dock")
        ToggleRow(
            title = "Own dock",
            subtitle = if (ownDock) "This theme has its own favorites" else "Uses the dock shared by all themes",
            checked = ownDock,
            onCheckedChange = onOwnDockChange,
        )

        SectionTitle("Sound")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showSoundPicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("When this theme is on", style = MaterialTheme.typography.bodyLarge)
                Text(soundProfile.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (soundProfile.needsPolicyAccess && !policyAccess) {
            Text(
                "Needs Do Not Disturb access to work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = {
                env.requestPolicyAccess()
                policyAccess = env.hasPolicyAccess()
            }) { Text("Allow access") }
        }

        SectionTitle("Switch automatically")
        AutoSwitchRules(env, rules, onRulesChange)
        Spacer(Modifier.height(8.dp))
    }

    if (showSoundPicker) {
        AlertDialog(
            onDismissRequest = { showSoundPicker = false },
            title = { Text("Sound when this theme is on") },
            text = {
                Column {
                    SoundProfile.entries.forEach { profile ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(profile == soundProfile, role = Role.RadioButton) {
                                    onSoundProfileChange(profile)
                                    showSoundPicker = false
                                    policyAccess = env.hasPolicyAccess()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = profile == soundProfile, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(profile.label)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Switching to a theme that doesn't change sound puts back your own settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSoundPicker = false }) { Text("Close") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AutoSwitchRules(env: ThemeEditorEnvironment, rules: List<ThemeRule>, onRulesChange: (List<ThemeRule>) -> Unit) {
    val schedule = rules.filterIsInstance<ThemeRule.Schedule>().firstOrNull()
    val bluetooth = rules.filterIsInstance<ThemeRule.Bluetooth>().firstOrNull()
    val controller = ThemeRule.Controller in rules
    fun replace(type: Class<out ThemeRule>, rule: ThemeRule?) =
        onRulesChange(rules.filterNot { type.isInstance(it) } + listOfNotNull(rule))

    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var pickingDevice by remember { mutableStateOf(false) }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickingDevice = true
    }

    Text(
        "Switches to this theme while a condition applies, then back to the theme you were on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Schedule
    ToggleRow(
        title = "On a schedule",
        subtitle = schedule?.let { describe(it) } ?: "e.g. work hours",
        checked = schedule != null,
        onCheckedChange = { on -> replace(ThemeRule.Schedule::class.java, if (on) ThemeRule.WORK_HOURS else null) },
    )
    if (schedule != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in schedule.days,
                    onClick = {
                        val days = if (day in schedule.days) schedule.days - day else schedule.days + day
                        if (days.isNotEmpty()) replace(ThemeRule.Schedule::class.java, schedule.copy(days = days))
                    },
                    label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editingTime = true }) { Text("From ${formatMinute(schedule.startMinute)}") }
            OutlinedButton(onClick = { editingTime = false }) { Text("To ${formatMinute(schedule.endMinute)}") }
        }
    }

    // Bluetooth
    ToggleRow(
        title = "When a Bluetooth device connects",
        subtitle = bluetooth?.name ?: "e.g. your car or headphones",
        checked = bluetooth != null,
        onCheckedChange = { on ->
            when {
                !on -> replace(ThemeRule.Bluetooth::class.java, null)
                env.hasBluetoothPermission() -> pickingDevice = true
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
    )

    // Controller
    ToggleRow(
        title = "When a game controller connects",
        subtitle = "Any Bluetooth or USB gamepad",
        checked = controller,
        onCheckedChange = { on -> replace(ThemeRule.Controller::class.java, if (on) ThemeRule.Controller else null) },
    )

    editingTime?.let { isStart ->
        val minute = if (isStart) schedule!!.startMinute else schedule!!.endMinute
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text(if (isStart) "Start time" else "End time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.hour * 60 + state.minute
                    replace(
                        ThemeRule.Schedule::class.java,
                        if (isStart) schedule.copy(startMinute = picked) else schedule.copy(endMinute = picked),
                    )
                    editingTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingTime = null }) { Text("Cancel") } },
        )
    }

    if (pickingDevice) {
        val devices = remember { env.pairedDevices() }
        AlertDialog(
            onDismissRequest = { pickingDevice = false },
            title = { Text("Choose a device") },
            text = {
                Column {
                    if (devices.isEmpty()) Text("No paired Bluetooth devices. Pair one in Settings first.")
                    devices.forEach { device ->
                        Text(
                            device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    replace(ThemeRule.Bluetooth::class.java, ThemeRule.Bluetooth(device.address, device.name))
                                    pickingDevice = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingDevice = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WallpaperThumb(path: String?) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull()
            }
        }
    }
    Box(
        Modifier
            .size(width = 56.dp, height = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = "Theme wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

private fun formatMinute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun describe(schedule: ThemeRule.Schedule): String {
    val days = DayOfWeek.entries.filter { it in schedule.days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    return "$days, ${formatMinute(schedule.startMinute)}–${formatMinute(schedule.endMinute)}"
}
