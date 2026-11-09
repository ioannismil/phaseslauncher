package com.phaseslauncher.ui.drawer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far the drawer is pulled up, shared by every gesture that moves it: a swipe on the home
 * screen, an overscroll of the home grid, a pull down on the drawer and the predictive back
 * gesture. The drawer tracks the finger 1:1; on release a fling decides, otherwise how far it got.
 */
@Stable
class DrawerSwipeState(private val scope: CoroutineScope) {
    /** 0 = closed, 1 = fully open. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** Set while a finger is on it; the drawer's open state is only synced when nobody is dragging. */
    var isDragging by mutableStateOf(false)
        private set

    /** Distance for a full open; the height of the screen. */
    var heightPx = 0f

    /** Called when a released drag settles; open = it ends up open. */
    var onSettle: (open: Boolean) -> Unit = {}

    private var settleJob: Job? = null
    private var settleTarget: Float? = null
    private var dragStartedOpen = false

    val isVisible: Boolean get() = progress > 0f

    /** Moves with the finger; [deltaY] is in pixels, positive = down. */
    fun dragBy(deltaY: Float) {
        if (heightPx <= 0f) return
        if (!isDragging) {
            isDragging = true
            dragStartedOpen = progress > 0.5f
            settleJob?.cancel()
            settleTarget = null
        }
        progress = (progress - deltaY / heightPx).coerceIn(0f, 1f)
    }

    /** Ends a drag with the finger's [velocityY] (px/s, positive = down); settles and reports where. */
    fun release(velocityY: Float) {
        if (!isDragging) return
        isDragging = false
        val open = when {
            velocityY < -FLING_VELOCITY -> true

            velocityY > FLING_VELOCITY -> false

            // A short pull is enough to commit either way, like the system launcher.
            dragStartedOpen -> progress > 1f - COMMIT_FRACTION

            else -> progress > COMMIT_FRACTION
        }
        animateTo(open, velocityY)
        onSettle(open)
    }

    /** The predictive back gesture shrinks the drawer down a little as the user swipes. */
    fun previewBack(fraction: Float) {
        settleJob?.cancel()
        settleTarget = null
        progress = 1f - fraction * BACK_PREVIEW_DISTANCE
    }

    /** Animates open or closed unless a drag is under way or it's already heading there. */
    fun animateTo(open: Boolean, velocityY: Float = 0f) {
        if (isDragging) return
        val target = if (open) 1f else 0f
        if (settleTarget == target || (settleJob?.isActive != true && progress == target)) return
        settleJob?.cancel()
        settleTarget = target
        val initialVelocity = if (heightPx > 0f) -velocityY / heightPx else 0f
        settleJob = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
            settleTarget = null
        }
    }

    private companion object {
        const val FLING_VELOCITY = 1200f
        const val COMMIT_FRACTION = 0.2f
        const val BACK_PREVIEW_DISTANCE = 0.15f
    }
}
