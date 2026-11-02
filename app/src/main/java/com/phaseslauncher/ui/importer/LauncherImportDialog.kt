package com.phaseslauncher.ui.importer

import android.content.res.Resources
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.importer.ImportMode
import com.phaseslauncher.data.importer.LauncherLayout
import com.phaseslauncher.data.importer.LayoutImportOptions
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.editor.ToggleRow

/** Shows what was found in another launcher's backup and how to turn it into themes. */
@Composable
fun LauncherImportDialog(layout: LauncherLayout, onImport: (LayoutImportOptions) -> Unit, onDismiss: () -> Unit) {
    val res = LocalResources.current
    var mode by rememberSaveable { mutableStateOf(if (layout.pages.size > 1) ImportMode.PagePerTheme else ImportMode.OneTheme) }
    val names = remember { mutableStateMapOf<Int, String>().apply { layout.pages.forEach { put(it.index, defaultName(res, it.index)) } } }
    val included = remember { mutableStateMapOf<Int, Boolean>().apply { layout.pages.forEach { put(it.index, true) } } }
    val importedName = stringResource(R.string.imported)
    var singleName by rememberSaveable { mutableStateOf(importedName) }
    var dock by rememberSaveable { mutableStateOf(layout.dock.isNotEmpty()) }
    var folders by rememberSaveable { mutableStateOf(true) }
    val chosen = layout.pages.filter { included[it.index] == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_home_screen)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    buildString {
                        val pages = layout.pages.size
                        append(
                            if (layout.dock.isNotEmpty()) {
                                pluralStringResource(R.plurals.found_pages_and_dock, pages, pages, layout.dock.size)
                            } else {
                                pluralStringResource(R.plurals.found_pages, pages, pages)
                            },
                        )
                        if (layout.unmatched > 0) append(" ").append(pluralStringResource(R.plurals.apps_skipped, layout.unmatched, layout.unmatched))
                        append(" ").append(stringResource(R.string.widgets_aren_t_carried_over))
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
                        label = { Text(stringResource(R.string.theme_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(R.string.pages), style = MaterialTheme.typography.titleSmall)
                layout.pages.forEach { page ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = included[page.index] == true, onCheckedChange = { included[page.index] = it })
                        if (mode == ImportMode.PagePerTheme) {
                            OutlinedTextField(
                                value = names[page.index].orEmpty(),
                                onValueChange = { names[page.index] = it.take(24) },
                                singleLine = true,
                                enabled = included[page.index] == true,
                                supportingText = { Text(pluralStringResource(R.plurals.apps_count, page.appCount, page.appCount)) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Text(stringResource(R.string.page_apps, page.index + 1, pluralStringResource(R.plurals.apps_count, page.appCount, page.appCount)), modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (layout.dock.isNotEmpty()) {
                    ToggleRow(
                        title = stringResource(R.string.replace_the_dock),
                        subtitle = if (dock) pluralStringResource(R.plurals.with_the_apps_from_the_backup, layout.dock.size, layout.dock.size) else stringResource(R.string.keep_your_current_dock),
                        checked = dock,
                        onCheckedChange = { dock = it },
                    )
                }
                ToggleRow(
                    title = stringResource(R.string.keep_folders),
                    subtitle = if (folders) stringResource(R.string.folders_come_across_as_folders) else stringResource(R.string.their_apps_go_straight_onto_the_phase),
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
                            pageNames = chosen.associate { it.index to (names[it.index]?.ifBlank { null } ?: defaultName(res, it.index)) },
                            singleName = singleName.ifBlank { importedName },
                            importDock = dock,
                            keepFolders = folders,
                        ),
                    )
                },
                enabled = chosen.isNotEmpty() || dock,
            ) {
                Text(
                    when {
                        chosen.isEmpty() -> stringResource(R.string.import_dock)
                        else -> (if (mode == ImportMode.OneTheme) 1 else chosen.size).let { pluralStringResource(R.plurals.import_phases, it, it) }
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun defaultName(res: Resources, index: Int) = if (index == 0) res.getString(R.string.home) else res.getString(R.string.page, index + 1)
