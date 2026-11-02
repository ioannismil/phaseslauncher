package com.phaseslauncher.ui.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.share.ShareOptions
import com.phaseslauncher.data.share.ThemeSharing.Companion.isPersonal
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.editor.ToggleRow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What the share sheet needs from the launcher. */
class ShareActions(
    val build: suspend (Theme, ShareOptions) -> JSONObject,
    val link: (JSONObject) -> String,
    val qrCode: (String, Int) -> Bitmap?,
    val writeFile: suspend (JSONObject, String) -> Uri,
)

/** Share one theme as a file, a link or a QR code, choosing what goes with it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareThemeSheet(theme: Theme, actions: ShareActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(ShareOptions()) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var qrTooBig by remember { mutableStateOf(false) }
    val qrSize = with(LocalDensity.current) { 280.dp.roundToPx() }
    val hasPersonal = theme.rules.any { it.rule.isPersonal() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.share_2, theme.name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.apps_travel_by_name_whoever_adds_it_gets_the_app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (theme.rules.isNotEmpty()) {
                ToggleRow(
                    title = stringResource(R.string.switching_conditions),
                    subtitle = if (options.includeRules) stringResource(R.string.when_it_switches_and_how) else stringResource(R.string.not_included),
                    checked = options.includeRules,
                    onCheckedChange = { options = options.copy(includeRules = it) },
                )
                if (hasPersonal && options.includeRules) {
                    ToggleRow(
                        title = stringResource(R.string.wi_fi_networks_bluetooth_devices_and_places),
                        subtitle = if (options.includePersonalRules) stringResource(R.string.included_they_can_reveal_where_you_live_and_work) else stringResource(R.string.left_out_for_privacy),
                        checked = options.includePersonalRules,
                        onCheckedChange = { options = options.copy(includePersonalRules = it) },
                    )
                }
            }
            if (!theme.focus.isDefault) {
                ToggleRow(
                    title = stringResource(R.string.focus_settings),
                    subtitle = if (options.includeFocus) stringResource(R.string.blocked_apps_notification_filter_and_limits) else stringResource(R.string.not_included),
                    checked = options.includeFocus,
                    onCheckedChange = { options = options.copy(includeFocus = it) },
                )
            }
            if (theme.wallpaperPath != null) {
                ToggleRow(
                    title = stringResource(R.string.wallpaper),
                    subtitle = if (options.includeWallpaper) stringResource(R.string.included_in_the_file_not_in_links_or_qr_codes) else stringResource(R.string.not_included),
                    checked = options.includeWallpaper,
                    onCheckedChange = { options = options.copy(includeWallpaper = it) },
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    scope.launch {
                        val uri = actions.writeFile(actions.build(theme, options), theme.name)
                        send(
                            context,
                            Intent(Intent.ACTION_SEND)
                                .setType("application/json")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.phase, theme.name))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }
                }) { ButtonContent(Icons.Outlined.Description, stringResource(R.string.file)) }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val link = actions.link(actions.build(theme, options.copy(includeWallpaper = false)))
                        send(
                            context,
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, res.getString(R.string.try_my_phase_in_phases, theme.name, link)),
                        )
                    }
                }) { ButtonContent(Icons.Outlined.Link, stringResource(R.string.link)) }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val link = actions.link(actions.build(theme, options.copy(includeWallpaper = false)))
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(res.getString(R.string.phase_link), link))
                        Toast.makeText(context, res.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                    }
                }) { ButtonContent(Icons.Outlined.ContentCopy, stringResource(R.string.copy_link)) }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val link = actions.link(actions.build(theme, options.copy(includeWallpaper = false)))
                        qr = actions.qrCode(link, qrSize)
                        qrTooBig = qr == null
                    }
                }) { ButtonContent(Icons.Outlined.QrCode2, stringResource(R.string.qr_code)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    qr?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text(theme.name) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                        Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.qr_code_for, theme.name), modifier = Modifier.size(260.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scan_it_with_the_phone_s_camera_on_a_phone_with),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qr = null }) { Text(stringResource(R.string.done)) } },
        )
    }
    if (qrTooBig) {
        AlertDialog(
            onDismissRequest = { qrTooBig = false },
            title = { Text(stringResource(R.string.too_big_for_a_qr_code)) },
            text = { Text(stringResource(R.string.this_phase_has_too_much_in_it_to_fit_in_a_qr_cod)) },
            confirmButton = { TextButton(onClick = { qrTooBig = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ButtonContent(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.size(8.dp))
    Text(label)
}

private fun send(context: Context, intent: Intent) {
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_phase)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_app_to_share_with), Toast.LENGTH_SHORT).show()
    }
}
