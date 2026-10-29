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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.ThemeIconKey
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

/** What the editor produces; app assignments are handled separately. */
data class ThemeDraft(val name: String, val icon: ThemeIconKey, val accentColor: Int)

/**
 * Create or edit a theme's name, icon and accent color. With [initial] == null it starts blank
 * and offers the presets as one-tap starting points.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorSheet(
    initial: ThemeDraft?,
    onSave: (ThemeDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = initial == null
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var icon by rememberSaveable { mutableStateOf(initial?.icon ?: ThemeIconKey.Star) }
    var color by rememberSaveable { mutableIntStateOf(initial?.accentColor ?: ThemeDefaults.AccentColors.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(if (isNew) "New theme" else "Edit theme", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (isNew) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeDefaults.Presets.forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                name = preset.name
                                icon = preset.icon
                                color = preset.accentColor
                            },
                            label = { Text(preset.name) },
                            icon = { Icon(preset.icon.imageVector, contentDescription = null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
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
                    val selected = key == icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(color) else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(role = Role.RadioButton) { icon = key }
                            .semantics {
                                this.selected = selected
                                contentDescription = key.name
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            key.imageVector,
                            contentDescription = null,
                            tint = if (selected) contentColorOn(Color(color)) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val selected = argb == color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable(role = Role.RadioButton) { color = argb }
                            .semantics { this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = contentColorOn(Color(argb)))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { onSave(ThemeDraft(name.trim(), icon, color)) },
                    enabled = name.isNotBlank(),
                ) { Text(if (isNew) "Create" else "Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
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

private const val MAX_NAME_LENGTH = 24
