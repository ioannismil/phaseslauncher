package com.phaseslauncher.ui.home

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Swiping sideways between phases: the phase's apps follow the finger, and on release either
 * spring back or slide out while the next phase's apps slide in from the other side.
 */
@Stable
class PhaseSwipeState(private val scope: CoroutineScope) {
    /** How far the apps are pushed sideways, in px; negative = toward the left (next phase). */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Width of the swipe area; a full swipe is this far. */
    var widthPx = 0f

    private var settleJob: Job? = null

    /** 1 when fully in place, fading as the apps leave. */
    val alpha: Float get() = if (widthPx > 0f) (1f - abs(offset) / widthPx * FADE).coerceIn(0f, 1f) else 1f

    fun dragBy(deltaX: Float) {
        settleJob?.cancel()
        offset += deltaX
    }

    /**
     * Ends a swipe with the finger's [velocityX] (px/s). A flick or a long enough drag calls
     * [step] with +1 (swiped left: next phase) or -1 (swiped right: previous phase).
     */
    fun release(velocityX: Float, step: (Int) -> Unit) {
        val width = widthPx
        val direction = when {
            width <= 0f -> 0
            velocityX < -FLING_VELOCITY && offset < 0f -> 1
            velocityX > FLING_VELOCITY && offset > 0f -> -1
            offset < -width * COMMIT_FRACTION -> 1
            offset > width * COMMIT_FRACTION -> -1
            else -> 0
        }
        settleJob?.cancel()
        settleJob = scope.launch {
            if (direction == 0) {
                animate(offset, 0f, initialVelocity = velocityX, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
                return@launch
            }
            // Out the side it was swiped toward...
            animate(offset, -direction * width, animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing)) { v, _ -> offset = v }
            step(direction)
            // ...and the next phase's apps come in from the other side.
            offset = direction * width * ENTER_FROM
            animate(offset, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
        }
    }

    private companion object {
        const val FLING_VELOCITY = 800f
        const val COMMIT_FRACTION = 0.25f
        const val EXIT_MS = 140
        const val ENTER_FROM = 0.3f
        const val FADE = 1.5f
    }
}
