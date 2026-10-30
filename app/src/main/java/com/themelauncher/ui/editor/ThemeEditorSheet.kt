package com.themelauncher.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.filterApps
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.data.themes.ThemeIconKey
import com.themelauncher.data.themes.ThemePreset
import com.themelauncher.data.themes.SoundProfile
import com.themelauncher.ui.components.AppIcon
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

/**
 * Create or edit a theme: name, icon, accent color, and which apps it contains.
 * With [initial] == null it starts blank and offers the presets as one-tap starting points.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorSheet(
    initial: ThemeDraft?,
    allApps: List<AppInfo>,
    env: ThemeEditorEnvironment,
    onSave: (ThemeDraft) -> Unit,
    onDismiss: () -> Unit,
    /** Accents already taken by other themes; a new theme starts on the first free one. */
    usedColors: Set<Int> = emptySet(),
) {
    val isNew = initial == null
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var icon by rememberSaveable { mutableStateOf(initial?.icon ?: ThemeIconKey.Star) }
    var color by rememberSaveable {
        mutableIntStateOf(
            initial?.accentColor
                ?: ThemeDefaults.AccentColors.firstOrNull { it !in usedColors }
                ?: ThemeDefaults.AccentColors[usedColors.size % ThemeDefaults.AccentColors.size],
        )
    }
    val selected = rememberSaveable(saver = stringListSaver) { initial?.appKeys.orEmpty().toMutableStateList() }
    var query by rememberSaveable { mutableStateOf("") }
    var wallpaperPath by rememberSaveable { mutableStateOf(initial?.wallpaperPath) }
    var ownDock by rememberSaveable { mutableStateOf(initial?.ownDock ?: false) }
    var soundProfile by rememberSaveable { mutableStateOf(initial?.soundProfile ?: SoundProfile.Unchanged) }
    var rules by remember { mutableStateOf(initial?.rules.orEmpty()) }

    val visibleApps = remember(allApps, query) { filterApps(allApps, query) }

    fun save() {
        // Keep the existing order and append newly checked apps alphabetically.
        val chosen = selected.toSet()
        val kept = initial?.appKeys.orEmpty().filter { it in chosen }
        val added = allApps.map { it.key }.filter { it in chosen && it !in kept }
        onSave(ThemeDraft(name.trim(), icon, color, kept + added, wallpaperPath, soundProfile, ownDock, rules))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            LazyColumn(Modifier.weight(1f)) {
                item(key = "details") {
                    ThemeDetails(
                        isNew = isNew,
                        name = name,
                        onNameChange = { name = it.take(MAX_NAME_LENGTH) },
                        icon = icon,
                        onIconChange = { icon = it },
                        color = color,
                        onColorChange = { color = it },
                        onPreset = { preset ->
                            name = preset.name
                            icon = preset.icon
                            color = preset.accentColor
                        },
                    )
                }
                item(key = "extras") {
                    ThemeExtras(
                        env = env,
                        wallpaperPath = wallpaperPath,
                        onWallpaperChange = { wallpaperPath = it },
                        ownDock = ownDock,
                        onOwnDockChange = { ownDock = it },
                        soundProfile = soundProfile,
                        onSoundProfileChange = { soundProfile = it },
                        rules = rules,
                        onRulesChange = { rules = it },
                    )
                }
                item(key = "appsHeader") {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        SectionTitle(
                            when (selected.size) {
                                0 -> "Apps"
                                1 -> "Apps · 1 selected"
                                else -> "Apps · ${selected.size} selected"
                            },
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search apps") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(visibleApps, key = { it.key }) { app ->
                    AppCheckRow(
                        app = app,
                        checked = app.key in selected,
                        onCheckedChange = { if (it) selected += app.key else selected -= app.key },
                    )
                }
            }

            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = ::save, enabled = name.isNotBlank()) { Text(if (isNew) "Create" else "Save") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeDetails(
    isNew: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    icon: ThemeIconKey,
    onIconChange: (ThemeIconKey) -> Unit,
    color: Int,
    onColorChange: (Int) -> Unit,
    onPreset: (ThemePreset) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(if (isNew) "New theme" else "Edit theme", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (isNew) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeDefaults.Presets.forEach { preset ->
                    SuggestionChip(
                        onClick = { onPreset(preset) },
                        label = { Text(preset.name) },
                        icon = { Icon(preset.icon.imageVector, contentDescription = null, Modifier.size(18.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Icon")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeIconKey.entries.forEach { key ->
                val isSelected = key == icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(color) else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(role = Role.RadioButton) { onIconChange(key) }
                        .semantics {
                            selected = isSelected
                            contentDescription = key.name
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        key.imageVector,
                        contentDescription = null,
                        tint = if (isSelected) contentColorOn(Color(color)) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTitle("Color")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeDefaults.AccentColors.forEach { argb ->
                val isSelected = argb == color
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable(role = Role.RadioButton) { onColorChange(argb) }
                        .semantics { selected = isSelected },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = contentColorOn(Color(argb)))
                }
            }
        }
    }
}

@Composable
private fun AppCheckRow(app: AppInfo, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, size = 36.dp)
        Spacer(Modifier.size(16.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

private val stringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

private const val MAX_NAME_LENGTH = 24
