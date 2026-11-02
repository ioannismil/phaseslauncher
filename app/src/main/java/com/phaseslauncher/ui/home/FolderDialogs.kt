package com.phaseslauncher.ui.home

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phaseslauncher.data.items.FolderItem
import com.phaseslauncher.data.items.FolderRepository
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.ui.components.AppAction
import com.phaseslauncher.ui.components.AppMenuState
import com.phaseslauncher.ui.components.drawerLabelStyle
import com.phaseslauncher.ui.components.rememberReorderState
import com.phaseslauncher.ui.components.reorderContainer
import com.phaseslauncher.ui.components.reorderItem

/** An open folder: its apps in a grid. Long-press an app for its menu, or long-press and drag to reorder. */
@Composable
fun FolderDialog(
    folder: FolderItem,
    onOpenItem: (HomeItem, Rect?) -> Unit,
    itemActions: (HomeItem) -> List<AppAction>,
    onRename: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        folder.label,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.rename_folder)) }
                }
                val menuState = remember { AppMenuState() }
                val reorder = rememberReorderState(folder.items.map { it.key }, onReorder)
                val labelStyle = drawerLabelStyle()
                val folderBackground = MaterialTheme.colorScheme.surfaceVariant
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (folder.items.size <= 6) 3 else 4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .reorderContainer(reorder, onDragStart = menuState::close),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(reorder.arrange(folder.items) { it.key }, key = { it.key }) { item ->
                        HomeItemTile(
                            item = item,
                            labelStyle = labelStyle,
                            onClick = { onOpenItem(item, it) },
                            actions = { itemActions(item) },
                            menuState = menuState,
                            iconSize = 56.dp,
                            showLabel = true,
                            folderBackground = folderBackground,
                            modifier = Modifier.animateItem().reorderItem(reorder, item.key),
                        )
                    }
                }
            }
        }
    }
}

/** Pick a folder on the current theme for [itemLabel], or name a new one. */
@Composable
fun AddToFolderDialog(
    itemLabel: String,
    folders: List<FolderItem>,
    onPick: (FolderItem) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $itemLabel to a folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                folders.forEach { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(folder) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.size(16.dp))
                        Text(folder.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (folders.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_FOLDER_NAME) },
                    label = { Text(stringResource(R.string.new_folder)) },
                    placeholder = { Text(FolderRepository.DEFAULT_NAME) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text(stringResource(R.string.create_folder)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun RenameFolderDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_folder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_FOLDER_NAME) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_FOLDER_NAME = 30
