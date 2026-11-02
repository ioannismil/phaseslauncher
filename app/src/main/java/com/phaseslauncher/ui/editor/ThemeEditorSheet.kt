package com.phaseslauncher.ui.editor

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.outlined.Share
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
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.apps.filterApps
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.themes.ThemeDraft
import com.phaseslauncher.data.themes.ThemeIconKey
import com.phaseslauncher.data.themes.ThemePreset
import com.phaseslauncher.data.themes.SoundProfile
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeAutomation
import com.phaseslauncher.ui.components.AppIcon
import com.phaseslauncher.ui.theme.contentColorOn
import com.phaseslauncher.ui.theme.imageVector

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
    /** Apps in this theme's folders, with the folder's name. They show as selected. */
    folderMembers: Map<String, String> = emptyMap(),
    /** Folder apps that were unchecked, to take out of their folders. */
    onFolderAppsRemoved: (Set<String>) -> Unit = {},
    /** Shares the saved theme; null for a new one. */
    onShare: (() -> Unit)? = null,
    /** Title override, e.g. while customizing a suggested theme during setup. */
    title: String? = null,
    saveLabel: String? = null,
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
    val selected = rememberSaveable(saver = stringListSaver) { (initial?.appKeys.orEmpty() + folderMembers.keys).toMutableStateList() }
    var query by rememberSaveable { mutableStateOf("") }
    var wallpaperPath by rememberSaveable { mutableStateOf(initial?.wallpaperPath) }
    var ownDock by rememberSaveable { mutableStateOf(initial?.ownDock ?: false) }
    var soundProfile by rememberSaveable { mutableStateOf(initial?.soundProfile ?: SoundProfile.Unchanged) }
    var rules by remember { mutableStateOf(initial?.rules.orEmpty()) }
    var automation by remember { mutableStateOf(initial?.automation ?: ThemeAutomation()) }
    var focus by remember { mutableStateOf(initial?.focus ?: ThemeFocus()) }
    var iconPack by rememberSaveable { mutableStateOf(initial?.iconPack) }
    var gridLayout by rememberSaveable { mutableStateOf(initial?.gridLayout) }

    val visibleApps = remember(allApps, query) { filterApps(allApps, query) }

    fun save() {
        // Keep the existing order and append newly checked apps alphabetically.
        val chosen = selected.toSet()
        val kept = initial?.appKeys.orEmpty().filter { it in chosen }
        val added = allApps.map { it.key }.filter { it in chosen && it !in kept && it !in folderMembers }
        val removedFromFolders = folderMembers.keys - chosen
        if (removedFromFolders.isNotEmpty()) onFolderAppsRemoved(removedFromFolders)
        onSave(ThemeDraft(name.trim(), icon, color, kept + added, wallpaperPath, soundProfile, ownDock, rules, iconPack, gridLayout, automation, focus))
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
                        title = title,
                        onShare = onShare,
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
                            // The preset's starting rules, sound and matching apps; all can be changed below.
                            rules = preset.rules.map { RuleSetting(it) }
                            automation = preset.automation
                            soundProfile = preset.soundProfile
                            if (selected.isEmpty()) selected.addAll(preset.suggest(allApps).map { it.key })
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
                        automation = automation,
                        onAutomationChange = { automation = it },
                        focus = focus,
                        onFocusChange = { focus = it },
                        allApps = allApps,
                        iconPack = iconPack,
                        onIconPackChange = { iconPack = it },
                        gridLayout = gridLayout,
                        onGridLayoutChange = { gridLayout = it },
                    )
                }
                item(key = "appsHeader") {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        // Folders and pinned shortcuts are kept but not counted; only apps are listed here.
                        val appCount = selected.count(ItemKeys::isApp)
                        SectionTitle(
                            when (appCount) {
                                0 -> stringResource(R.string.apps)
                                1 -> stringResource(R.string.apps_1_selected)
                                else -> "Apps · $appCount selected"
                            },
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.search_apps)) },
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
                        folder = folderMembers[app.key],
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.size(8.dp))
                Button(onClick = ::save, enabled = name.isNotBlank()) {
                    Text(saveLabel ?: if (isNew) stringResource(R.string.create) else stringResource(R.string.save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeDetails(
    isNew: Boolean,
    title: String?,
    onShare: (() -> Unit)?,
    name: String,
    onNameChange: (String) -> Unit,
    icon: ThemeIconKey,
    onIconChange: (ThemeIconKey) -> Unit,
    color: Int,
    onColorChange: (Int) -> Unit,
    onPreset: (ThemePreset) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title ?: if (isNew) stringResource(R.string.new_theme) else stringResource(R.string.edit_theme),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (onShare != null) {
                TextButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Share")
                }
            }
        }
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
            label = { Text(stringResource(R.string.name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.icon))
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

        SectionTitle(stringResource(R.string.color))
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
private fun AppCheckRow(app: AppInfo, folder: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, size = 36.dp)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder != null) {
                Text(
                    "In folder \u201c$folder\u201d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
