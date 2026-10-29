package com.themelauncher.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.theme.imageVector

/** Pick one or more themes for [app]. Themes that already contain it are shown checked and locked. */
@Composable
fun AddToThemesDialog(
    app: AppInfo,
    themes: List<Theme>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${app.label} to…") },
        text = {
            if (themes.isEmpty()) {
                Text("Create a theme first with the + button on the home screen.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    themes.forEach { theme ->
                        val alreadyIn = app.key in theme.appKeys
                        val checked = alreadyIn || theme.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    enabled = !alreadyIn,
                                    role = Role.Checkbox,
                                    onValueChange = { if (it) selected += theme.id else selected -= theme.id },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !alreadyIn)
                            Spacer(Modifier.size(8.dp))
                            Icon(theme.icon.imageVector, contentDescription = null, tint = Color(theme.accentColor))
                            Spacer(Modifier.size(12.dp))
                            Text(theme.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toSet()) }, enabled = selected.isNotEmpty()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
