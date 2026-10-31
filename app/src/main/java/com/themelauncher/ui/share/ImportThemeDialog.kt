package com.themelauncher.ui.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.themelauncher.data.rules.RuleText
import com.themelauncher.data.share.ImportOptions
import com.themelauncher.data.share.Resolution
import com.themelauncher.data.share.SharedTheme
import com.themelauncher.data.themes.SoundProfile
import com.themelauncher.ui.editor.ToggleRow
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

/** Preview a received theme and choose what to take from it. */
@Composable
fun ImportThemeDialog(
    shared: SharedTheme,
    resolution: Resolution,
    onImport: (ImportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(shared.name) }
    var rules by remember { mutableStateOf(true) }
    var focus by remember { mutableStateOf(true) }
    var wallpaper by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf(true) }
    val total = shared.allApps.size
    val found = total - resolution.missing.size
    val accent = Color(shared.accentColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(48.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(shared.icon.imageVector, contentDescription = null, tint = contentColorOn(accent))
            }
        },
        title = { Text("Add this theme?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        total == 0 -> "No apps"
                        resolution.missing.isEmpty() -> "All $total apps are installed"
                        else -> "$found of $total apps are installed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (resolution.missing.isNotEmpty()) {
                    Text(
                        "Not installed (you can add them later):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    resolution.missing.take(MAX_MISSING_SHOWN).forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { openStore(context, app.packageName) }) { Text("Get") }
                        }
                    }
                    if (resolution.missing.size > MAX_MISSING_SHOWN) {
                        Text("and ${resolution.missing.size - MAX_MISSING_SHOWN} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
                shared.iconPack?.let { pack ->
                    Text(
                        "Uses the icon pack ${shared.iconPackLabel ?: pack}; if it isn't installed, apps keep their own icons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (shared.rules.isNotEmpty()) {
                    ToggleRow(
                        title = "Switching conditions",
                        subtitle = shared.rules.joinToString("; ") { RuleText.describe(it.rule) },
                        checked = rules,
                        onCheckedChange = { rules = it },
                    )
                }
                if (!shared.focus.isDefault) {
                    ToggleRow("Focus settings", "Blocked apps, notification filter and limits", focus) { focus = it }
                }
                if (shared.soundProfile != SoundProfile.Unchanged) {
                    ToggleRow("Sound", shared.soundProfile.label, sound) { sound = it }
                }
                if (shared.wallpaper != null) {
                    ToggleRow("Wallpaper", "Applied when you switch to it", wallpaper) { wallpaper = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(ImportOptions(name, rules, focus, wallpaper, sound)) },
                enabled = name.isNotBlank(),
            ) { Text("Add theme") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Paste a shared link (or the file's text). */
@Composable
fun PasteThemeDialog(onParse: (String) -> String?, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste a theme link") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text("themelauncher://theme…") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().width(320.dp),
            )
        },
        confirmButton = { TextButton(onClick = { error = onParse(text) }, enabled = text.isNotBlank()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun openStore(context: Context, packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private const val MAX_MISSING_SHOWN = 6
