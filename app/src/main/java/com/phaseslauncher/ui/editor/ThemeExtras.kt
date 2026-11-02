package com.phaseslauncher.ui.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.data.icons.IconPackInfo
import com.phaseslauncher.data.rules.BluetoothDeviceInfo
import com.phaseslauncher.data.rules.CalendarInfo
import com.phaseslauncher.data.rules.GeoFix
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeAutomation
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.themes.SoundProfile
import com.phaseslauncher.data.themes.ThemeLook
import com.phaseslauncher.ui.components.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** System-dependent bits the editor needs, supplied by the screen. */
class ThemeEditorEnvironment(
    val importWallpaper: suspend (Uri) -> String?,
    val hasPolicyAccess: () -> Boolean,
    val requestPolicyAccess: () -> Unit,
    val hasBluetoothPermission: () -> Boolean,
    val pairedDevices: () -> List<BluetoothDeviceInfo>,
    val hasLocationPermission: () -> Boolean,
    /** "Allow all the time", so place rules work while another app is open. */
    val hasBackgroundLocation: () -> Boolean,
    /** The connected Wi-Fi network's name, when Android reveals it. */
    val currentWifiSsid: () -> String?,
    val currentLocation: suspend () -> GeoFix?,
    val onLocationGranted: () -> Unit,
    val hasCalendarPermission: () -> Boolean,
    val calendars: () -> List<CalendarInfo>,
    val onCalendarGranted: () -> Unit,
    val hasNotificationAccess: () -> Boolean,
    val requestNotificationAccess: () -> Unit,
    val hasUsageAccess: () -> Boolean,
    val requestUsageAccess: () -> Unit,
    val loadIconPacks: suspend () -> List<IconPackInfo>,
)

/** Wallpaper, look, dock, sound, automatic-switching and focus options of a theme. */
@Composable
fun ThemeExtras(
    env: ThemeEditorEnvironment,
    wallpaperPath: String?,
    onWallpaperChange: (String?) -> Unit,
    ownDock: Boolean,
    onOwnDockChange: (Boolean) -> Unit,
    soundProfile: SoundProfile,
    onSoundProfileChange: (SoundProfile) -> Unit,
    rules: List<RuleSetting>,
    onRulesChange: (List<RuleSetting>) -> Unit,
    automation: ThemeAutomation,
    onAutomationChange: (ThemeAutomation) -> Unit,
    focus: ThemeFocus,
    onFocusChange: (ThemeFocus) -> Unit,
    allApps: List<AppInfo>,
    iconPack: String?,
    onIconPackChange: (String?) -> Unit,
    gridLayout: String?,
    onGridLayoutChange: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { env.importWallpaper(uri)?.let(onWallpaperChange) }
    }
    var policyAccess by remember { mutableStateOf(env.hasPolicyAccess()) }
    var showSoundPicker by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 24.dp)) {
        SectionTitle(stringResource(R.string.wallpaper))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WallpaperThumb(wallpaperPath)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (wallpaperPath == null) stringResource(R.string.keeps_the_current_wallpaper) else stringResource(R.string.applied_when_you_switch_to_this_theme),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (wallpaperPath == null) stringResource(R.string.choose_image) else stringResource(R.string.change))
                    }
                    if (wallpaperPath != null) TextButton(onClick = { onWallpaperChange(null) }) { Text(stringResource(R.string.remove)) }
                }
            }
        }

        SectionTitle(stringResource(R.string.look))
        ThemeLookOptions(env, iconPack, onIconPackChange, gridLayout, onGridLayoutChange)

        SectionTitle(stringResource(R.string.dock))
        ToggleRow(
            title = stringResource(R.string.own_dock),
            subtitle = if (ownDock) stringResource(R.string.this_theme_has_its_own_favorites) else stringResource(R.string.uses_the_dock_shared_by_all_themes),
            checked = ownDock,
            onCheckedChange = onOwnDockChange,
        )

        SectionTitle(stringResource(R.string.sound))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showSoundPicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.when_this_theme_is_on), style = MaterialTheme.typography.bodyLarge)
                Text(soundProfile.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (soundProfile.needsPolicyAccess && !policyAccess) {
            Text(
                stringResource(R.string.needs_do_not_disturb_access_to_work),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = {
                env.requestPolicyAccess()
                policyAccess = env.hasPolicyAccess()
            }) { Text(stringResource(R.string.allow_access)) }
        }

        SectionTitle(stringResource(R.string.switch_automatically))
        AutomationSection(env, rules, onRulesChange, automation, onAutomationChange)

        SectionTitle(stringResource(R.string.focus))
        FocusSection(env, focus, onFocusChange, allApps)
        Spacer(Modifier.height(8.dp))
    }

    if (showSoundPicker) {
        AlertDialog(
            onDismissRequest = { showSoundPicker = false },
            title = { Text(stringResource(R.string.sound_when_this_theme_is_on)) },
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
                        stringResource(R.string.switching_to_a_theme_that_doesn_t_change_sound_p),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSoundPicker = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** The theme's own icon pack and home grid, or the launcher settings. */
@Composable
private fun ThemeLookOptions(
    env: ThemeEditorEnvironment,
    iconPack: String?,
    onIconPackChange: (String?) -> Unit,
    gridLayout: String?,
    onGridLayoutChange: (String?) -> Unit,
) {
    val res = LocalResources.current
    val packs by produceState(emptyList<IconPackInfo>()) { value = env.loadIconPacks() }
    var pickingPack by remember { mutableStateOf(false) }
    var pickingGrid by remember { mutableStateOf(false) }

    fun packLabel(pack: String?) = when (pack) {
        null -> res.getString(R.string.same_as_launcher_settings)
        ThemeLook.NO_ICON_PACK -> res.getString(R.string.app_icons)
        else -> packs.firstOrNull { it.packageName == pack }?.label ?: res.getString(R.string.icon_pack_not_installed)
    }

    fun gridLabel(layout: String?) = when (layout) {
        null -> res.getString(R.string.same_as_launcher_settings)
        ThemeLook.GRID_FIT -> res.getString(R.string.fit_icons_to_the_screen)
        else -> res.getString(R.string.n_columns, layout)
    }

    ValueRow(stringResource(R.string.icon_pack), packLabel(iconPack)) { pickingPack = true }
    ValueRow(stringResource(R.string.home_grid), gridLabel(gridLayout)) { pickingGrid = true }

    if (pickingPack) {
        ChoiceDialog(
            title = stringResource(R.string.icon_pack_for_this_theme),
            options = listOf(null, ThemeLook.NO_ICON_PACK) + packs.map { it.packageName },
            selected = iconPack,
            label = { packLabel(it) },
            onSelect = {
                pickingPack = false
                onIconPackChange(it)
            },
            onDismiss = { pickingPack = false },
        )
    }
    if (pickingGrid) {
        ChoiceDialog(
            title = stringResource(R.string.home_grid_for_this_theme),
            options = listOf(null, ThemeLook.GRID_FIT) + LauncherPrefs.COLUMN_RANGE.map { it.toString() },
            selected = gridLayout,
            label = { gridLabel(it) },
            onSelect = {
                pickingGrid = false
                onGridLayoutChange(it)
            },
            onDismiss = { pickingGrid = false },
        )
    }
}

@Composable
internal fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(option == selected, role = Role.RadioButton) { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            Image(b.asImageBitmap(), contentDescription = stringResource(R.string.theme_wallpaper), contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
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
