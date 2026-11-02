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
import androidx.compose.ui.unit.dp
import com.phaseslauncher.data.focus.BlockMode
import com.phaseslauncher.data.focus.FocusBlock
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
        is FocusBlock.Blocked -> "${block.app.label} is paused on ${block.theme.name}"
        is FocusBlock.OverLimit -> "Time's up for ${if (block.themeTotal) "${block.theme.name}'s apps" else block.app.label}"
    }
    val body = when (block) {
        is FocusBlock.Blocked -> if (block.mode == BlockMode.Pause) {
            "Take a breath. Still want it? It will open freely for ${minutesText(focus.allowMinutes)}."
        } else {
            "It can't be opened while ${block.theme.name} is on."
        }
        is FocusBlock.OverLimit -> "Used ${minutesText(block.usedMinutes)} of ${minutesText(block.limitMinutes)} today." +
            if (block.mode == BlockMode.Pause) " Opening it anyway gives you ${minutesText(focus.allowMinutes)}." else ""
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
                    Text("Change ${block.theme.name}'s focus settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            if (block.mode == BlockMode.Pause) {
                TextButton(onClick = onOpenAnyway, enabled = left == 0) { Text(if (left > 0) "Open anyway ($left)" else "Open anyway") }
            } else {
                TextButton(onClick = onCancel) { Text("OK") }
            }
        },
        dismissButton = if (block.mode == BlockMode.Pause) ({ TextButton(onClick = onCancel) { Text("Don't open") } }) else null,
    )
}

private fun minutesText(m: Int) = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}
