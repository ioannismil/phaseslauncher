package com.phaseslauncher.ui.share

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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.rules.RuleText
import com.phaseslauncher.data.share.ImportOptions
import com.phaseslauncher.data.share.Resolution
import com.phaseslauncher.data.share.SharedTheme
import com.phaseslauncher.data.themes.SoundProfile
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.editor.ToggleRow
import com.phaseslauncher.ui.theme.contentColorOn
import com.phaseslauncher.ui.theme.imageVector

/** Preview a received theme and choose what to take from it. */
@Composable
fun ImportThemeDialog(
    shared: SharedTheme,
    resolution: Resolution,
    onImport: (ImportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
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
        title = { Text(stringResource(R.string.add_this_phase)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        total == 0 -> stringResource(R.string.no_apps)
                        resolution.missing.isEmpty() -> pluralStringResource(R.plurals.all_apps_installed, total, total)
                        else -> pluralStringResource(R.plurals.apps_installed_of, total, found, total)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (resolution.missing.isNotEmpty()) {
                    Text(
                        stringResource(R.string.not_installed_you_can_add_them_later),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    resolution.missing.take(MAX_MISSING_SHOWN).forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { openStore(context, app.packageName) }) { Text(stringResource(R.string.get)) }
                        }
                    }
                    if (resolution.missing.size > MAX_MISSING_SHOWN) {
                        Text(pluralStringResource(R.plurals.and_more, resolution.missing.size - MAX_MISSING_SHOWN, resolution.missing.size - MAX_MISSING_SHOWN), style = MaterialTheme.typography.bodySmall)
                    }
                }
                shared.iconPack?.let { pack ->
                    Text(
                        stringResource(R.string.uses_the_icon_pack_if_it_isn_t_installed_apps_ke, shared.iconPackLabel ?: pack),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (shared.rules.isNotEmpty()) {
                    ToggleRow(
                        title = stringResource(R.string.switching_conditions),
                        subtitle = shared.rules.joinToString("; ") { RuleText.describe(res, it.rule) },
                        checked = rules,
                        onCheckedChange = { rules = it },
                    )
                }
                if (!shared.focus.isDefault) {
                    ToggleRow(stringResource(R.string.focus_settings), stringResource(R.string.blocked_apps_notification_filter_and_limits), focus) { focus = it }
                }
                if (shared.soundProfile != SoundProfile.Unchanged) {
                    ToggleRow(stringResource(R.string.sound), shared.soundProfile.label, sound) { sound = it }
                }
                if (shared.wallpaper != null) {
                    ToggleRow(stringResource(R.string.wallpaper), stringResource(R.string.applied_when_you_switch_to_it), wallpaper) { wallpaper = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(ImportOptions(name, rules, focus, wallpaper, sound)) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.add_phase)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Paste a shared link (or the file's text). */
@Composable
fun PasteThemeDialog(onParse: (String) -> String?, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_a_phase_link)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text("phases://phase…") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().width(320.dp),
            )
        },
        confirmButton = { TextButton(onClick = { error = onParse(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.open_2)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
