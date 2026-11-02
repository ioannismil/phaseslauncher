package com.phaseslauncher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.focus.BlockMode
import com.phaseslauncher.data.focus.FocusBlock
import com.phaseslauncher.ui.editor.minutes
import kotlinx.coroutines.delay

/**
 * Shown instead of opening an app the active theme holds back: a pause that has to be waited out
 * before "Open anyway" works, or a plain refusal.
 */
@Composable
fun FocusPauseDialog(
    block: FocusBlock,
    onOpenAnyway: () -> Unit,
    onCancel: () -> Unit,
    onEditTheme: () -> Unit,
) {
    val focus = block.theme.focus
    val wait = if (block.mode == BlockMode.Pause) focus.pauseSeconds else 0
    var left by remember(block) { mutableIntStateOf(wait) }
    LaunchedEffect(block) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    val title = when (block) {
        is FocusBlock.Blocked -> stringResource(R.string.is_paused_on, block.app.label, block.theme.name)
        is FocusBlock.OverLimit -> stringResource(R.string.time_s_up_for, if (block.themeTotal) stringResource(R.string.s_apps, block.theme.name) else block.app.label)
    }
    val body = when (block) {
        is FocusBlock.Blocked -> if (block.mode == BlockMode.Pause) {
            stringResource(R.string.take_a_breath_still_want_it_it_will_open_freely, minutes(focus.allowMinutes))
        } else {
            stringResource(R.string.it_can_t_be_opened_while_is_on, block.theme.name)
        }
        is FocusBlock.OverLimit -> stringResource(R.string.used_of_today, minutes(block.usedMinutes), minutes(block.limitMinutes)) +
            if (block.mode == BlockMode.Pause) stringResource(R.string.opening_it_anyway_gives_you, minutes(focus.allowMinutes)) else ""
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                if (block.mode == BlockMode.Pause && wait > 0) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { 1f - left / wait.toFloat() })
                }
                TextButton(onClick = onEditTheme) {
                    Text(stringResource(R.string.change_s_focus_settings, block.theme.name), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            if (block.mode == BlockMode.Pause) {
                TextButton(onClick = onOpenAnyway, enabled = left == 0) { Text(if (left > 0) stringResource(R.string.open_anyway, left) else stringResource(R.string.open_anyway_2)) }
            } else {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = if (block.mode == BlockMode.Pause) ({ TextButton(onClick = onCancel) { Text(stringResource(R.string.don_t_open)) } }) else null,
    )
}
