package com.phaseslauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.settings.GestureAction
import com.phaseslauncher.data.settings.GestureKind
import com.phaseslauncher.ui.components.AppIcon
import com.phaseslauncher.ui.components.label

/**
 * One home-screen gesture and what it does. Choosing "Lock the screen" without the accessibility
 * service on calls [onNeedsLockService]; "Open an app" asks which app.
 */
@Composable
fun GestureOption(
    icon: ImageVector,
    title: String,
    action: GestureAction,
    apps: Map<String, AppInfo>,
    lockServiceOn: Boolean,
    onChange: (GestureAction) -> Unit,
    onNeedsLockService: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }
    val needsService = action.kind == GestureKind.Lock && !lockServiceOn
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                when {
                    needsService -> stringResource(R.string.lock_the_screen_needs_the_accessibility_service)
                    action.kind == GestureKind.OpenApp -> stringResource(R.string.open, action.appKey?.let(apps::get)?.label ?: stringResource(R.string.an_app_not_installed))
                    else -> action.kind.label
                },
                color = if (needsService) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.change)) { if (needsService) onNeedsLockService() else picking = true }
            .padding(horizontal = 8.dp),
    )

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup()) {
                    GestureKind.entries.forEach { kind ->
                        val selected = kind == action.kind
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    picking = false
                                    when (kind) {
                                        GestureKind.OpenApp -> pickingApp = true
                                        else -> {
                                            onChange(GestureAction(kind))
                                            if (kind == GestureKind.Lock && !lockServiceOn) onNeedsLockService()
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(kind.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (pickingApp) {
        AlertDialog(
            onDismissRequest = { pickingApp = false },
            title = { Text(stringResource(R.string.choose_an_app)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps.values.sortedBy { it.label.lowercase() }, key = { it.key }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    pickingApp = false
                                    onChange(GestureAction(GestureKind.OpenApp, app.key))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app, size = 36.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingApp = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
