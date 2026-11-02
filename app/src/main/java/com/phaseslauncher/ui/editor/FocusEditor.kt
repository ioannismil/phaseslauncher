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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.filterApps
import com.phaseslauncher.data.focus.BlockMode
import com.phaseslauncher.data.focus.LimitScope
import com.phaseslauncher.data.focus.NotificationFilter
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.ui.components.AppIcon
import com.phaseslauncher.ui.components.label

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
    @Composable
    fun appsLabel(keys: Set<String>, none: String) = when (keys.size) {
        0 -> none
        1 -> labels[keys.first()] ?: pluralStringResource(R.plurals.apps_count, 1, 1)
        2 -> keys.mapNotNull(labels::get).joinToString(", ").ifBlank { pluralStringResource(R.plurals.apps_count, 2, 2) }
        else -> pluralStringResource(R.plurals.apps_count, keys.size, keys.size)
    }

    Text(
        stringResource(R.string.applies_while_this_phase_is_on_to_apps_opened_fr),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Blocking
    ValueRow(stringResource(R.string.blocked_apps), appsLabel(focus.blockedApps, stringResource(R.string.none))) { pickingApps = AppPick.Blocked }
    ToggleRow(
        title = stringResource(R.string.block_apps_outside_this_phase),
        subtitle = if (focus.blockOtherApps) stringResource(R.string.only_this_phase_s_apps_and_the_dock_open_freely) else stringResource(R.string.off),
        checked = focus.blockOtherApps,
        onCheckedChange = { onFocusChange(focus.copy(blockOtherApps = it)) },
    )
    if (focus.blocksAnything) {
        ValueRow(stringResource(R.string.opening_a_blocked_app), focus.blockMode.label) { choosing = FocusChoice.BlockMode }
        if (focus.blockMode == BlockMode.Pause) {
            ValueRow(stringResource(R.string.pause_for), seconds(focus.pauseSeconds)) { choosing = FocusChoice.Pause }
            ValueRow(stringResource(R.string.then_let_it_open_for), minutes(focus.allowMinutes)) { choosing = FocusChoice.Allow }
        }
        ToggleRow(
            title = stringResource(R.string.hide_blocked_apps_from_the_drawer),
            subtitle = if (focus.hideBlockedApps) stringResource(R.string.they_come_back_when_you_switch_phases) else stringResource(R.string.shown_but_held_back_when_opened),
            checked = focus.hideBlockedApps,
            onCheckedChange = { onFocusChange(focus.copy(hideBlockedApps = it)) },
        )
    }

    // Notifications
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ValueRow(stringResource(R.string.allow_notifications_from), focus.notificationFilter.label) { choosing = FocusChoice.Filter }
    if (focus.notificationFilter != NotificationFilter.Off) {
        ValueRow(
            if (focus.notificationFilter == NotificationFilter.Chosen) stringResource(R.string.allowed_apps) else stringResource(R.string.also_allow),
            appsLabel(focus.allowedNotificationApps, stringResource(R.string.none)),
        ) { pickingApps = AppPick.Notifications }
        ToggleRow(
            title = stringResource(R.string.keep_the_others_out_of_the_notification_shade),
            subtitle = if (focus.hideNotifications) {
                stringResource(R.string.snoozed_while_this_phase_is_on_they_return_withi)
            } else {
                stringResource(R.string.off_only_their_notification_dots_are_hidden)
            },
            checked = focus.hideNotifications,
            onCheckedChange = { onFocusChange(focus.copy(hideNotifications = it)) },
        )
        ToggleRow(
            title = stringResource(R.string.always_let_calls_alarms_and_reminders_through),
            subtitle = if (focus.allowUrgent) stringResource(R.string.on) else stringResource(R.string.off),
            checked = focus.allowUrgent,
            onCheckedChange = { onFocusChange(focus.copy(allowUrgent = it)) },
        )
        if (!notificationAccess) {
            Warning(stringResource(R.string.needs_notification_access_to_filter_notification), stringResource(R.string.allow_access)) { env.requestNotificationAccess() }
        }
    }

    // Limits
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.daily_limits), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ValueRow(
        stringResource(R.string.per_app),
        when (focus.appLimits.size) {
            0 -> stringResource(R.string.none)
            1 -> focus.appLimits.entries.first().let { (k, v) ->
                stringResource(R.string.app_limit, labels[k] ?: pluralStringResource(R.plurals.apps_count, 1, 1), minutes(v))
            }
            else -> pluralStringResource(R.plurals.apps_count, focus.appLimits.size, focus.appLimits.size)
        },
    ) { editingLimits = true }
    ValueRow(
        stringResource(R.string.all_of_this_phase_s_apps_together),
        if (focus.themeLimitMinutes == 0) stringResource(R.string.no_limit) else stringResource(R.string.a_day, minutes(focus.themeLimitMinutes)),
    ) { choosing = FocusChoice.ThemeLimit }
    if (focus.hasLimits) {
        ValueRow(stringResource(R.string.limits_count), focus.limitScope.label) { choosing = FocusChoice.LimitScope }
        ValueRow(stringResource(R.string.when_time_s_up), focus.limitMode.label) { choosing = FocusChoice.LimitMode }
        if (!usageAccess) {
            Warning(stringResource(R.string.needs_usage_access_to_know_how_long_you_ve_used), stringResource(R.string.allow_access)) { env.requestUsageAccess() }
        }
    }
    if (!focus.isDefault) {
        TextButton(onClick = { onFocusChange(ThemeFocus()) }) { Text(stringResource(R.string.reset_focus_settings)) }
    }

    when (choosing) {
        FocusChoice.BlockMode -> ChoiceDialog(stringResource(R.string.opening_a_blocked_app), BlockMode.entries, focus.blockMode, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(blockMode = it))
        }, { choosing = null })
        FocusChoice.Pause -> ChoiceDialog(stringResource(R.string.pause_for), ThemeFocus.PAUSE_CHOICES, focus.pauseSeconds, { seconds(it) }, {
            choosing = null
            onFocusChange(focus.copy(pauseSeconds = it))
        }, { choosing = null })
        FocusChoice.Allow -> ChoiceDialog(stringResource(R.string.then_let_it_open_for), ThemeFocus.ALLOW_CHOICES, focus.allowMinutes, { minutes(it) }, {
            choosing = null
            onFocusChange(focus.copy(allowMinutes = it))
        }, { choosing = null })
        FocusChoice.Filter -> ChoiceDialog(stringResource(R.string.allow_notifications_from), NotificationFilter.entries, focus.notificationFilter, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(notificationFilter = it))
            if (it != NotificationFilter.Off && !notificationAccess) env.requestNotificationAccess()
        }, { choosing = null })
        FocusChoice.ThemeLimit -> ChoiceDialog(
            stringResource(R.string.all_of_this_phase_s_apps_together),
            listOf(0) + ThemeFocus.LIMIT_CHOICES,
            focus.themeLimitMinutes,
            { if (it == 0) stringResource(R.string.no_limit) else stringResource(R.string.a_day, minutes(it)) },
            {
                choosing = null
                onFocusChange(focus.copy(themeLimitMinutes = it))
            },
            { choosing = null },
        )
        FocusChoice.LimitScope -> ChoiceDialog(stringResource(R.string.limits_count), LimitScope.entries, focus.limitScope, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(limitScope = it))
        }, { choosing = null })
        FocusChoice.LimitMode -> ChoiceDialog(stringResource(R.string.when_time_s_up), BlockMode.entries, focus.limitMode, { it.label }, {
            choosing = null
            onFocusChange(focus.copy(limitMode = it))
        }, { choosing = null })
        null -> Unit
    }

    when (pickingApps) {
        AppPick.Blocked -> AppPickerDialog(stringResource(R.string.blocked_apps), allApps, focus.blockedApps, {
            pickingApps = null
            onFocusChange(focus.copy(blockedApps = it))
        }, { pickingApps = null })
        AppPick.Notifications -> AppPickerDialog(stringResource(R.string.allowed_notifications), allApps, focus.allowedNotificationApps, {
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
                    placeholder = { Text(stringResource(R.string.search_apps)) },
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
        confirmButton = { TextButton(onClick = { onDone(selected) }) { Text(if (selected.isEmpty()) stringResource(R.string.save_none) else stringResource(R.string.save_2, selected.size)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LimitsDialog(
    allApps: List<AppInfo>,
    initial: Map<String, Int>,
    onDone: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = LocalResources.current
    var limits by remember { mutableStateOf(initial) }
    var adding by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    val byKey = remember(allApps) { allApps.associateBy { it.key } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.daily_limits_per_app)) },
        text = {
            Column {
                if (limits.isEmpty()) {
                    Text(stringResource(R.string.no_limits_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(byKey[key]?.label ?: stringResource(R.string.app_not_installed), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.a_day, minutes(minutes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { limits = limits - key }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_limit)) }
                        }
                    }
                }
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_apps))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(limits) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (adding) {
        AppPickerDialog(stringResource(R.string.add_limits), allApps.filter { it.key !in limits }, emptySet(), { keys ->
            adding = false
            limits = limits + keys.associateWith { DEFAULT_LIMIT }
        }, { adding = false })
    }
    editingKey?.let { key ->
        ChoiceDialog(res.getString(R.string.daily_limit, byKey[key]?.label ?: res.getString(R.string.app)), ThemeFocus.LIMIT_CHOICES, limits[key] ?: DEFAULT_LIMIT, { minutes(it) }, {
            editingKey = null
            limits = limits + (key to it)
        }, { editingKey = null })
    }
}

@Composable
private fun seconds(s: Int) = when {
    s == 0 -> stringResource(R.string.no_wait)
    s < 60 -> pluralStringResource(R.plurals.seconds_count, s, s)
    else -> pluralStringResource(R.plurals.minutes_count, s / 60, s / 60)
}

/** "45 min", "2 h", "1 h 30 min". */
@Composable
internal fun minutes(m: Int) = when {
    m < 60 -> stringResource(R.string.minutes_short, m)
    m % 60 == 0 -> stringResource(R.string.hours_short, m / 60)
    else -> stringResource(R.string.hours_minutes_short, m / 60, m % 60)
}

private const val DEFAULT_LIMIT = 30
