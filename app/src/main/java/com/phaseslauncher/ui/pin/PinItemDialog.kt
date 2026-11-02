package com.phaseslauncher.ui.pin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.theme.imageVector

/** Asks which theme a shortcut or widget from another app goes on. Starts on the active theme. */
@Composable
fun PinItemDialog(
    preview: PinPreview,
    themes: List<Theme>,
    initialThemeId: Long?,
    onAdd: (Theme) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosenId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = themes.firstOrNull { it.id == chosenId }
        ?: themes.firstOrNull { it.id == initialThemeId }
        ?: themes.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = preview.icon?.let { { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) } },
        title = { Text(if (preview.isWidget) stringResource(R.string.add_widget_to_home_screen) else stringResource(R.string.add_to_home_screen)) },
        text = {
            Column {
                Text(
                    preview.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                if (themes.isEmpty()) {
                    Text(stringResource(R.string.create_a_theme_in_phases_first_then_try_a))
                } else {
                    Text(stringResource(R.string.which_theme_should_it_go_on), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                        themes.forEach { theme ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = theme == selected, role = Role.RadioButton, onClick = { chosenId = theme.id })
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = theme == selected, onClick = null)
                                Spacer(Modifier.size(8.dp))
                                Icon(theme.icon.imageVector, contentDescription = null, tint = Color(theme.accentColor))
                                Spacer(Modifier.size(12.dp))
                                Text(theme.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selected != null) TextButton(onClick = { onAdd(selected) }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
