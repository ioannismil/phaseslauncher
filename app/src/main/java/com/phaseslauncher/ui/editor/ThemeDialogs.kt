package com.phaseslauncher.ui.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.phaseslauncher.R
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.theme.imageVector

@Composable
fun DeleteThemeDialog(theme: Theme, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(theme.icon.imageVector, contentDescription = null, tint = Color(theme.accentColor)) },
        title = { Text(stringResource(R.string.delete_2, theme.name)) },
        text = { Text(stringResource(R.string.the_theme_is_removed_its_apps_stay_installed_and)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
