package com.phaseslauncher.ui.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.phaseslauncher.data.importer.ImportMode
import com.phaseslauncher.data.importer.LauncherLayout
import com.phaseslauncher.data.importer.LayoutImportOptions
import com.phaseslauncher.ui.editor.ToggleRow

/** Shows what was found in another launcher's backup and how to turn it into themes. */
@Composable
fun LauncherImportDialog(layout: LauncherLayout, onImport: (LayoutImportOptions) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(if (layout.pages.size > 1) ImportMode.PagePerTheme else ImportMode.OneTheme) }
    val names = remember { mutableStateMapOf<Int, String>().apply { layout.pages.forEach { put(it.index, defaultName(it.index)) } } }
    val included = remember { mutableStateMapOf<Int, Boolean>().apply { layout.pages.forEach { put(it.index, true) } } }
    var singleName by rememberSaveable { mutableStateOf("Imported") }
    var dock by rememberSaveable { mutableStateOf(layout.dock.isNotEmpty()) }
    var folders by rememberSaveable { mutableStateOf(true) }
    val chosen = layout.pages.filter { included[it.index] == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import home screen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    buildString {
                        append("Found ${layout.pages.size} page${if (layout.pages.size == 1) "" else "s"}")
                        if (layout.dock.isNotEmpty()) append(" and a dock of ${layout.dock.size}")
                        append(".")
                        if (layout.unmatched > 0) append(" ${layout.unmatched} app${if (layout.unmatched == 1) " isn't" else "s aren't"} installed here and will be skipped.")
                        append(" Widgets aren't carried over.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ImportMode.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(mode == option, role = Role.RadioButton) { mode = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == option, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(option.label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (mode == ImportMode.OneTheme) {
                    OutlinedTextField(
                        value = singleName,
                        onValueChange = { singleName = it.take(24) },
                        label = { Text("Phase name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text("Pages", style = MaterialTheme.typography.titleSmall)
                layout.pages.forEach { page ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = included[page.index] == true, onCheckedChange = { included[page.index] = it })
                        if (mode == ImportMode.PagePerTheme) {
                            OutlinedTextField(
                                value = names[page.index].orEmpty(),
                                onValueChange = { names[page.index] = it.take(24) },
                                singleLine = true,
                                enabled = included[page.index] == true,
                                supportingText = { Text("${page.appCount} apps") },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Text("Page ${page.index + 1} · ${page.appCount} apps", modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (layout.dock.isNotEmpty()) {
                    ToggleRow(
                        title = "Replace the dock",
                        subtitle = if (dock) "With the ${layout.dock.size} apps from the backup" else "Keep your current dock",
                        checked = dock,
                        onCheckedChange = { dock = it },
                    )
                }
                ToggleRow(
                    title = "Keep folders",
                    subtitle = if (folders) "Folders come across as folders" else "Their apps go straight onto the phase",
                    checked = folders,
                    onCheckedChange = { folders = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onImport(
                        LayoutImportOptions(
                            mode = mode,
                            pageNames = chosen.associate { it.index to (names[it.index]?.ifBlank { null } ?: defaultName(it.index)) },
                            singleName = singleName.ifBlank { "Imported" },
                            importDock = dock,
                            keepFolders = folders,
                        ),
                    )
                },
                enabled = chosen.isNotEmpty() || dock,
            ) {
                Text(
                    when {
                        chosen.isEmpty() -> "Import dock"
                        mode == ImportMode.OneTheme || chosen.size == 1 -> "Import 1 phase"
                        else -> "Import ${chosen.size} phases"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun defaultName(index: Int) = if (index == 0) "Home" else "Page ${index + 1}"
