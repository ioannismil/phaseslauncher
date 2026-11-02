package com.phaseslauncher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.remember
import com.phaseslauncher.data.apps.AppInfo

/** Where a dropped app lands on the home grid. */
sealed interface HomeDrop {
    /** Onto the middle of this item: into the folder, or a new folder with this app. */
    data class Into(val key: String) : HomeDrop

    /** Between items: at this index among the items on screen. */
    data class At(val index: Int) : HomeDrop
}

/**
 * An app carried from the drawer to the home screen. The drawer starts it; the launcher's root
 * follows the finger (the drawer closes under it) and drops it where it's released.
 */
@Stable
class ExternalDragState {
    var app by mutableStateOf<AppInfo?>(null)
        private set

    /** Finger position in window coordinates. */
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Set by the home grid while it's on screen; null over anything else. */
    var gridDrop: ((Offset) -> HomeDrop?)? = null
    var dockBounds: Rect? = null

    internal var onStart: (AppInfo) -> Unit = {}
    internal var onDrop: (AppInfo, Offset) -> Unit = { _, _ -> }

    val isDragging: Boolean get() = app != null

    fun start(app: AppInfo, window: Offset) {
        this.app = app
        position = window
        onStart(app)
    }

    fun move(window: Offset) {
        position = window
    }

    fun drop() {
        val dropped = app ?: return
        app = null
        onDrop(dropped, position)
    }

    fun cancel() {
        app = null
    }
}

val LocalExternalDrag = staticCompositionLocalOf<ExternalDragState?> { null }

/**
 * Lets [app]'s tile be carried out of the drawer: long-press, then move. A long press without
 * moving still opens the tile's menu. Watches in the Initial pass and consumes only once the
 * drag starts, so taps, menus and scrolling are untouched.
 */
fun Modifier.dragToHome(app: AppInfo, state: ExternalDragState?): Modifier =
    if (state == null) {
        this
    } else {
        composed {
            val origin = remember { arrayOf(Offset.Zero) }
            this
                .onGloballyPositioned { origin[0] = it.positionInWindow() }
                .pointerInput(app.key, state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) return@withTimeoutOrNull false
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    return@withTimeoutOrNull false
                                }
                            }
                            @Suppress("UNREACHABLE_CODE") false
                        } ?: true
                        if (!held) return@awaitEachGesture
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                change.consume()
                                // From here the launcher's root follows the finger.
                                state.start(app, origin[0] + change.position)
                                break
                            }
                        }
                    }
                }
        }
    }

/** Put on the launcher's root: follows a carried app and drops it on release. */
fun Modifier.externalDragTracker(state: ExternalDragState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (state.isDragging) {
                    change.consume()
                    if (change.pressed) state.move(change.position) else state.drop()
                }
                if (!change.pressed) break
            }
            // A gesture cut short (e.g. cancelled by the system) mustn't leave an app hanging.
            if (state.isDragging) state.cancel()
        }
    }
