package com.themelauncher.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.theme.imageVector

@Composable
fun DeleteThemeDialog(theme: Theme, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(theme.icon.imageVector, contentDescription = null, tint = Color(theme.accentColor)) },
        title = { Text("Delete “${theme.name}”?") },
        text = { Text("The theme is removed. Its apps stay installed, and stay in your other themes and the dock.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Move themes up and down; the switcher shows them in this order. */
@Composable
fun ReorderThemesDialog(themes: List<Theme>, onSave: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val order = remember(themes) { themes.toMutableStateList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder themes") },
        text = {
            LazyColumn {
                itemsIndexed(order, key = { _, theme -> theme.id }) { index, theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(theme.icon.imageVector, contentDescription = null, tint = Color(theme.accentColor))
                        Spacer(Modifier.size(12.dp))
                        Text(
                            theme.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { order.add(index - 1, order.removeAt(index)) },
                            enabled = index > 0,
                        ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${theme.name} up") }
                        IconButton(
                            onClick = { order.add(index + 1, order.removeAt(index)) },
                            enabled = index < order.lastIndex,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${theme.name} down") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(order.map { it.id }) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
