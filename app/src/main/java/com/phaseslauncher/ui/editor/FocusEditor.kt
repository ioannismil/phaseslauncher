package com.phaseslauncher.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.filterApps
import com.phaseslauncher.data.focus.BlockMode
import com.phaseslauncher.data.focus.LimitScope
import com.phaseslauncher.data.focus.NotificationFilter
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.ui.components.AppIcon

private enum class FocusChoice { BlockMode, Pause, Allow, Filter, ThemeLimit, LimitScope, LimitMode }

private enum class AppPick { Blocked, Notifications }

/**
 * A theme's focus settings: apps it blocks, whose notifications get through, and daily limits.
 * They only cover apps opened from Phases (and its notification dots), which the text says.
 */
@Composable
fun FocusSection(
    env: ThemeEditorEnvironment,
    focus: ThemeFocus,
    onFocusChange: (ThemeFocus) -> Unit,
    allApps: List<AppInfo>,
) {
    var choosing by remember { mutableStateOf<FocusChoice?>(null) }
    var pickingApps by remember { mutableStateOf<AppPick?>(null) }
    var editingLimits by remember { mutableStateOf(false) }
    var notificationAccess by remember { mutableStateOf(env.hasNotificationAccess()) }
    var usageAccess by remember { mutableStateOf(env.hasUsageAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationAccess = env.hasNotificationAccess()
        usageAccess = env.hasUsageAccess()
    }
    val labels = remember(allApps) { allApps.associate { it.key to it.label } }
    fun appsLabel(keys: Set<String>, none: String) = when (keys.size) {
        0 -> none
        1 -> labels[keys.first()] ?: "1 app"
        2 -> keys.mapNotNull(labels::get).joinToString(", ").ifBlank { "2 apps" }
        else -> "${keys.size} apps"
    }

    Text(
        "Applies while this phase is on, to apps opened from Phases.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Blocking
    ValueRow("Blocked apps", appsLabel(focus.blockedApps, "None")) { pickingApps = AppPick.Blocked }
    ToggleRow(
        title = "Block apps outside this phase",
        subtitle = if (focus.blockOtherApps) "Only this phase's apps and the dock open freely" else "Off",
        checked = focus.blockOtherApps,
        onCheckedChange = { onFocusChange(focus.copy(blockOtherApps = it)) },
    )
    if (focus.blocksAnything) {
        ValueRow("Opening a blocked app", focus.blockMode.label) { choosing = FocusChoice.BlockMode }
        if (focus.blockMode == BlockMode.Pause) {
            ValueRow("Pause for", seconds(focus.pauseSeconds)) { choosing = FocusChoice.Pause }
            ValueRow("Then let it open for", minutes(focus.allowMinutes)) { choosing = FocusChoice.Allow }
        }
        ToggleRow(
            title = "Hide blocked apps from the drawer",
            subtitle = if (focus.hideBlockedApps) "They come back when you switch phases" else "Shown, but held back when opened",
            checked = focus.hideBlockedApps,
            onCheckedChange = { onFocusChange(focus.copy(hideBlockedApps = it)) },
        )
    }

    // Notifications
    Spacer(Modifier.height(8.dp))
    Text("Notifications", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ValueRow("Allow notifications from", focus.notificationFilter.label) { choosing = FocusChoice.Filter }
    if (focus.notificationFilter != NotificationFilter.Off) {
        ValueRow(
            if (focus.notificationFilter == NotificationFilter.Chosen) "Allowed apps" else "Also allow",
            appsLabel(focus.allowedNotificationApps, "None"),
        ) { pickingApps = AppPick.Notifications }
        ToggleRow(
            title = "Keep the others out of the notification shade",
            subtitle = if (focus.hideNotifications) {
                "Snoozed while this phase is on; they return within 10 minutes after"
            } else {
                "Off: only their notification dots are hidden"
            },
            checked = focus.hideNotifications,
            onCheckedChange = { onFocusChange(focus.copy(hideNotifications = it)) },
        )
        ToggleRow(
            title = "Always let calls, alarms and reminders through",
            subtitle = if (focus.allowUrgent) "On" else "Off",
            checked = focus.allowUrgent,
            onCheckedChange = { onFocusChange(focus.copy(allowUrgent = it)) },
        )
        if (!notificationAccess) {
            Warning("Needs notification access to filter notifications.", "Allow access") { env.requestNotificationAccess() }
        }
    }

    // Limits
    Spacer(Modifier.height(8.dp))
    Text("Daily limits", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ValueRow(
        "Per app",
        when (focus.appLimits.size) {
            0 -> "None"
            1 -> focus.appLimits.entries.first().let { (k, v) -> "${labels[k] ?: "1 app"}: ${minutes(v)}" }
            else -> "${focus.appLimits.size} apps"
        },
    ) { editingLimits = true }
    ValueRow(
        "All of this phase's apps together",
        if (focus.themeLimitMinutes == 0) "No limit" else "${minutes(focus.themeLimitMinutes)} a day",
    ) { choosing = FocusChoice.ThemeLimit }
    if (focus.hasLimits) {
        ValueRow("Limits count", focus.limitScope.label) { choosing = FocusChoice.LimitScope }
        ValueRow("When time's up", focus.limitMode.label) { choosing = FocusChoice.LimitMode }
        if (!usageAccess) {
            Warning("Needs usage access to know how long you've used apps today.", "Allow access") { env.requestUsageAccess() }
        }
    }
    if (!focus.isDefault) {
        TextButton(onClick = { onFocusChange(ThemeFocus()) }) { Text("Reset focus settings") }
    }

    when (choosing) {
        FocusChoice.BlockMode -> ChoiceDialog("Opening a blocked app", BlockMode.entries, focus.blockMode, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(blockMode = it))
        }, { choosing = null })
        FocusChoice.Pause -> ChoiceDialog("Pause for", ThemeFocus.PAUSE_CHOICES, focus.pauseSeconds, ::seconds, {
            choosing = null
            onFocusChange(focus.copy(pauseSeconds = it))
        }, { choosing = null })
        FocusChoice.Allow -> ChoiceDialog("Then let it open for", ThemeFocus.ALLOW_CHOICES, focus.allowMinutes, ::minutes, {
            choosing = null
            onFocusChange(focus.copy(allowMinutes = it))
        }, { choosing = null })
        FocusChoice.Filter -> ChoiceDialog("Allow notifications from", NotificationFilter.entries, focus.notificationFilter, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(notificationFilter = it))
            if (it != NotificationFilter.Off && !notificationAccess) env.requestNotificationAccess()
        }, { choosing = null })
        FocusChoice.ThemeLimit -> ChoiceDialog(
            "All of this phase's apps together",
            listOf(0) + ThemeFocus.LIMIT_CHOICES,
            focus.themeLimitMinutes,
            { if (it == 0) "No limit" else "${minutes(it)} a day" },
            {
                choosing = null
                onFocusChange(focus.copy(themeLimitMinutes = it))
            },
            { choosing = null },
        )
        FocusChoice.LimitScope -> ChoiceDialog("Limits count", LimitScope.entries, focus.limitScope, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(limitScope = it))
        }, { choosing = null })
        FocusChoice.LimitMode -> ChoiceDialog("When time's up", BlockMode.entries, focus.limitMode, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(limitMode = it))
        }, { choosing = null })
        null -> Unit
    }

    when (pickingApps) {
        AppPick.Blocked -> AppPickerDialog("Blocked apps", allApps, focus.blockedApps, {
            pickingApps = null
            onFocusChange(focus.copy(blockedApps = it))
        }, { pickingApps = null })
        AppPick.Notifications -> AppPickerDialog("Allowed notifications", allApps, focus.allowedNotificationApps, {
            pickingApps = null
            onFocusChange(focus.copy(allowedNotificationApps = it))
        }, { pickingApps = null })
        null -> Unit
    }

    if (editingLimits) {
        LimitsDialog(allApps, focus.appLimits, {
            editingLimits = false
            onFocusChange(focus.copy(appLimits = it))
            if (it.isNotEmpty() && !usageAccess) env.requestUsageAccess()
        }, { editingLimits = false })
    }
}

@Composable
private fun Warning(text: String, action: String, onAction: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    TextButton(onClick = onAction) { Text(action) }
}

/** Pick any number of apps, with search. */
@Composable
fun AppPickerDialog(
    title: String,
    allApps: List<AppInfo>,
    initial: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }
    // Chosen apps first, so they're easy to review.
    val visible = remember(allApps, query) {
        filterApps(allApps, query).sortedByDescending { it.key in initial }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(visible, key = { it.key }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = app.key in selected, role = Role.Checkbox) {
                                    selected = if (it) selected + app.key else selected - app.key
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Checkbox(checked = app.key in selected, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(selected) }) { Text(if (selected.isEmpty()) "Save (none)" else "Save (${selected.size})") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LimitsDialog(
    allApps: List<AppInfo>,
    initial: Map<String, Int>,
    onDone: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var limits by remember { mutableStateOf(initial) }
    var adding by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    val byKey = remember(allApps) { allApps.associateBy { it.key } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily limits per app") },
        text = {
            Column {
                if (limits.isEmpty()) {
                    Text("No limits yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(limits.entries.sortedBy { byKey[it.key]?.label ?: it.key }.toList(), key = { it.key }) { (key, minutes) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingKey = key }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            byKey[key]?.let { AppIcon(it, size = 32.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(byKey[key]?.label ?: "App not installed", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${minutes(minutes)} a day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { limits = limits - key }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove limit") }
                        }
                    }
                }
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add apps")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(limits) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (adding) {
        AppPickerDialog("Add limits", allApps.filter { it.key !in limits }, emptySet(), { keys ->
            adding = false
            limits = limits + keys.associateWith { DEFAULT_LIMIT }
        }, { adding = false })
    }
    editingKey?.let { key ->
        ChoiceDialog("${byKey[key]?.label ?: "App"}: daily limit", ThemeFocus.LIMIT_CHOICES, limits[key] ?: DEFAULT_LIMIT, ::minutes, {
            editingKey = null
            limits = limits + (key to it)
        }, { editingKey = null })
    }
}

private fun seconds(s: Int) = if (s == 0) "No wait" else if (s < 60) "$s seconds" else "${s / 60} minute${if (s >= 120) "s" else ""}"

internal fun minutes(m: Int) = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

private const val DEFAULT_LIMIT = 30
