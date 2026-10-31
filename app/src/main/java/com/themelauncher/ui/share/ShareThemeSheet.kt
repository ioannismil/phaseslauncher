package com.themelauncher.ui.share

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.themelauncher.data.share.ShareOptions
import com.themelauncher.data.share.ThemeSharing.Companion.isPersonal
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.editor.ToggleRow
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
            Text("Share “${theme.name}”", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Apps travel by name: whoever adds it gets the apps they have installed, and a list of the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (theme.rules.isNotEmpty()) {
                ToggleRow(
                    title = "Switching conditions",
                    subtitle = if (options.includeRules) "When it switches, and how" else "Not included",
                    checked = options.includeRules,
                    onCheckedChange = { options = options.copy(includeRules = it) },
                )
                if (hasPersonal && options.includeRules) {
                    ToggleRow(
                        title = "Wi-Fi networks, Bluetooth devices and places",
                        subtitle = if (options.includePersonalRules) "Included: they can reveal where you live and work" else "Left out for privacy",
                        checked = options.includePersonalRules,
                        onCheckedChange = { options = options.copy(includePersonalRules = it) },
                    )
                }
            }
            if (!theme.focus.isDefault) {
                ToggleRow(
                    title = "Focus settings",
                    subtitle = if (options.includeFocus) "Blocked apps, notification filter and limits" else "Not included",
                    checked = options.includeFocus,
                    onCheckedChange = { options = options.copy(includeFocus = it) },
                )
            }
            if (theme.wallpaperPath != null) {
                ToggleRow(
                    title = "Wallpaper",
                    subtitle = if (options.includeWallpaper) "Included in the file (not in links or QR codes)" else "Not included",
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
                                .putExtra(Intent.EXTRA_SUBJECT, "ThemeLauncher theme: ${theme.name}")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }
                }) { ButtonContent(Icons.Outlined.Description, "File") }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val link = actions.link(actions.build(theme, options.copy(includeWallpaper = false)))
                        send(
                            context,
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "My “${theme.name}” theme for ThemeLauncher: $link"),
                        )
                    }
                }) { ButtonContent(Icons.Outlined.Link, "Link") }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val link = actions.link(actions.build(theme, options.copy(includeWallpaper = false)))
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("ThemeLauncher theme", link))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    }
                }) { ButtonContent(Icons.Outlined.ContentCopy, "Copy link") }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val link = actions.link(actions.build(theme, options.copy(includeWallpaper = false)))
                        qr = actions.qrCode(link, qrSize)
                        qrTooBig = qr == null
                    }
                }) { ButtonContent(Icons.Outlined.QrCode2, "QR code") }
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
                        Image(bitmap.asImageBitmap(), contentDescription = "QR code for ${theme.name}", modifier = Modifier.size(260.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan it with the phone's camera on a phone with ThemeLauncher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qr = null }) { Text("Done") } },
        )
    }
    if (qrTooBig) {
        AlertDialog(
            onDismissRequest = { qrTooBig = false },
            title = { Text("Too big for a QR code") },
            text = { Text("This theme has too much in it to fit in a QR code. Share it as a file or a link instead.") },
            confirmButton = { TextButton(onClick = { qrTooBig = false }) { Text("OK") } },
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
        context.startActivity(Intent.createChooser(intent, "Share theme").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show()
    }
}
