package com.themelauncher.ui.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
