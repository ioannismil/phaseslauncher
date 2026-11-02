package com.phaseslauncher.ui.editor

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.icons.IconPackInfo
import com.phaseslauncher.data.icons.IconPackManager
import com.phaseslauncher.ui.components.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rename an app and/or give it an icon from an installed icon pack. */
@Composable
fun CustomizeAppDialog(
    app: AppInfo,
    /** The icon picked earlier (pack, drawable), kept unless changed or reset. */
    initialIcon: Pair<String, String>?,
    iconPacks: List<IconPackInfo>,
    iconPackManager: IconPackManager,
    loadIconNames: suspend (String) -> List<String>,
    onSave: (label: String?, iconPack: String?, iconName: String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(app.label) }
    var picked by remember { mutableStateOf(initialIcon) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize ${app.defaultLabel}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val chosen = picked
                    if (chosen != null) {
                        PackIcon(iconPackManager, chosen.first, chosen.second, Modifier.size(56.dp))
                    } else {
                        AppIcon(app, size = 56.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    if (iconPacks.isEmpty()) {
                        Text(
                            stringResource(R.string.install_an_icon_pack_to_pick_a_different_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.change_icon)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(app.defaultLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    onReset()
                    onDismiss()
                }) { Text(stringResource(R.string.reset_to_default)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(label, picked?.first, picked?.second)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (picking) {
        IconPickerSheet(
            iconPacks = iconPacks,
            iconPackManager = iconPackManager,
            loadIconNames = loadIconNames,
            onPick = { pack, name ->
                picked = pack to name
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPickerSheet(
    iconPacks: List<IconPackInfo>,
    iconPackManager: IconPackManager,
    loadIconNames: suspend (String) -> List<String>,
    onPick: (pack: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pack by remember { mutableStateOf(iconPacks.first().packageName) }
    var query by remember { mutableStateOf("") }
    val names by produceState(emptyList<String>(), pack) { value = loadIconNames(pack) }
    val visible = remember(names, query) {
        val q = query.trim().lowercase().replace(' ', '_')
        if (q.isEmpty()) names else names.filter { it.contains(q) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.choose_an_icon), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            if (iconPacks.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    iconPacks.forEach { p ->
                        FilterChip(selected = p.packageName == pack, onClick = { pack = p.packageName }, label = { Text(p.label) })
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_icons)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), modifier = Modifier.weight(1f)) {
                items(visible, key = { it }) { name ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(pack, name) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PackIcon(iconPackManager, pack, name, Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIcon(manager: IconPackManager, pack: String, name: String, modifier: Modifier) {
    val density = LocalContext.current.resources.displayMetrics.densityDpi
    val bitmap by produceState<ImageBitmap?>(null, pack, name) {
        value = withContext(Dispatchers.IO) {
            runCatching { manager.drawable(pack, name, density)?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier) { bitmap?.let { Image(it, contentDescription = name, modifier = Modifier.matchParentSize()) } }
}
