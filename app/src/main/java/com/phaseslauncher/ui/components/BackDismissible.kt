package com.phaseslauncher.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException

/**
 * A full-screen layer (like settings) that slides in, and follows the predictive back gesture:
 * it shrinks toward the swiping edge while the user swipes, then fades away when they let go.
 * Stays composed while it animates out, so [content] must cope with being shown after [visible]
 * turns false.
 */
@Composable
fun BackDismissible(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown = remember { Animatable(if (visible) 1f else 0f) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    LaunchedEffect(visible) {
        if (visible) backProgress = 0f
        shown.animateTo(if (visible) 1f else 0f, tween(if (visible) ENTER_MS else EXIT_MS, easing = FastOutSlowInEasing))
    }

    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { event ->
                backProgress = event.progress
                swipeEdge = event.swipeEdge
            }
            onDismiss()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    if (!visible && shown.value == 0f) return

    Box(
        modifier.graphicsLayer {
            val enter = shown.value
            val back = FastOutSlowInEasing.transform(backProgress)
            alpha = enter
            // Rises a little on the way in and sinks on the way out.
            translationY = (1f - enter) * ENTER_RISE.toPx()
            val scale = 1f - back * BACK_SHRINK
            scaleX = scale
            scaleY = scale
            // Drifts away from the edge the swipe started on, like system screens do.
            val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            translationX = direction * back * BACK_DRIFT.toPx()
            shape = RoundedCornerShape(BACK_CORNER * back)
            clip = back > 0f
        },
    ) { content() }
}

private const val ENTER_MS = 260
private const val EXIT_MS = 200
private const val BACK_SHRINK = 0.1f
private val ENTER_RISE = 48.dp
private val BACK_DRIFT = 8.dp
private val BACK_CORNER = 32.dp
