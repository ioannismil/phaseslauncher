package com.themelauncher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a set of items identified by string keys, in any layout (lazy grid or row).
 *
 * The gesture is long-press-then-move, so it coexists with an item's own tap and long-press
 * menu: a long press without moving still opens the menu; moving after the long press closes
 * it and picks the item up. The new order is shown immediately and handed to [onCommit] on drop.
 *
 * With merging on ([onMerge] set), holding the dragged item over the middle of another one for a
 * moment marks that one as a merge target (it grows), and dropping there calls [onMerge] instead
 * (e.g. to make a folder). Reordering then waits for the finger to rest briefly on an item's edge,
 * so the finger can travel to an item's middle without shuffling everything on the way.
 */
@Stable
class ReorderState internal constructor() {
    internal var onCommit: (List<String>) -> Unit = {}
    internal var onMove: () -> Unit = {}

    /** Called on a drop onto a merge target with the order at that moment, the dragged key and the target key. */
    internal var onMerge: ((List<String>, String, String) -> Unit)? = null
    internal var canMerge: (dragged: String, target: String) -> Boolean = { _, _ -> true }
    internal var onMergeReady: () -> Unit = {}
    internal var sourceKeys: List<String> = emptyList()

    /** Item bounds in window coordinates, excluding the drag translation. */
    internal val bounds = mutableStateMapOf<String, Rect>()

    /** Areas (e.g. widgets) that handle their own long-press, so they don't count as empty space. */
    internal val blockers = mutableStateMapOf<String, Rect>()
    internal var containerOrigin = Offset.Zero

    /** How the layout reads: rows stacked bottom-up, and/or items running right to left. */
    var rowsUpward = false
    var rightToLeft = false

    var draggingKey by mutableStateOf<String?>(null)
        private set

    /** The item the dragged one would be merged into if dropped now. */
    var mergeTargetKey by mutableStateOf<String?>(null)
        private set

    private var hoverKey: String? = null
    private var hoverInMiddle = false
    private var hoverSince = 0L

    /** Order shown while dragging, and after a drop until the source list catches up. */
    private var order by mutableStateOf<List<String>?>(null)
    private var startOrder: List<String> = emptyList()
    private var pointer by mutableStateOf(Offset.Zero)
    private var grab = Offset.Zero
    private var lastSwapped: String? = null

    fun <T> arrange(source: List<T>, key: (T) -> String): List<T> {
        val current = order ?: return source
        val byKey = source.associateBy(key)
        // Anything added meanwhile goes at the end; anything removed is skipped.
        return current.mapNotNull(byKey::get) + source.filter { key(it) !in current }
    }

    internal fun onSourceChanged(keys: List<String>) {
        sourceKeys = keys
        bounds.keys.retainAll(keys.toSet())
        if (draggingKey == null) order = null
    }

    internal fun keyAt(window: Offset): String? = bounds.entries.firstOrNull { it.value.contains(window) }?.key

    /** The item whose middle (its icon, roughly) is under [window], if any. */
    fun keyInMiddleAt(window: Offset): String? = bounds.entries.firstOrNull { (_, r) -> r.contains(window) && isInMiddle(r, window) }?.key

    /** Where an item dropped at [window] goes in [keys] (reading order), excluding [except]. */
    fun dropIndex(window: Offset, keys: List<String>, except: String? = null): Int {
        val placed = keys.filter { it != except }
        val index = placed.indexOfFirst { key ->
            val r = bounds[key] ?: return@indexOfFirst false
            // Before an item on an earlier row, or before an item on this row whose middle is past the finger.
            val earlierRow = if (rowsUpward) window.y > r.bottom else window.y < r.top
            val sameRow = window.y >= r.top && window.y <= r.bottom
            val beforeMiddle = if (rightToLeft) window.x > r.center.x else window.x < r.center.x
            earlierRow || (sameRow && beforeMiddle)
        }
        return if (index < 0) placed.size else index
    }

    private fun isInMiddle(r: Rect, window: Offset): Boolean {
        // The icon sits in the upper part of a tile, above its label.
        val middle = Offset(r.center.x, r.top + r.height * 0.42f)
        return (window - middle).getDistance() < minOf(r.width, r.height) * MIDDLE_FRACTION
    }

    internal fun start(key: String, window: Offset) {
        val itemBounds = bounds[key] ?: return
        startOrder = order ?: sourceKeys
        order = startOrder
        draggingKey = key
        lastSwapped = null
        pointer = window
        grab = window - itemBounds.topLeft
    }

    internal fun dragTo(window: Offset, now: Long = nowMillis()) {
        pointer = window
        val dragging = draggingKey ?: return
        // The item we just swapped with animates through the finger on its way to its new slot;
        // ignore it until the finger has left it, or the two would swap straight back.
        lastSwapped?.let { if (bounds[it]?.contains(window) != true) lastSwapped = null }
        val target = bounds.entries
            .firstOrNull { it.key != dragging && it.key != lastSwapped && it.value.contains(window) }
            ?.key
        if (onMerge == null) {
            if (target != null) swap(dragging, target)
            return
        }
        if (target == null) {
            clearHover()
            return
        }
        val inMiddle = isInMiddle(bounds.getValue(target), window) && canMerge(dragging, target)
        if (target != hoverKey || inMiddle != hoverInMiddle) {
            mergeTargetKey = null
            hoverKey = target
            hoverInMiddle = inMiddle
            hoverSince = now
        }
        tick(now)
    }

    /** Re-checks a resting finger: merges and dwell-reorders happen while it doesn't move. */
    internal fun tick(now: Long = nowMillis()) {
        val dragging = draggingKey ?: return
        val target = hoverKey ?: return
        val held = now - hoverSince
        if (hoverInMiddle) {
            if (held >= MERGE_DWELL_MS && mergeTargetKey != target) {
                mergeTargetKey = target
                onMergeReady()
            }
        } else if (held >= SWAP_DWELL_MS) {
            swap(dragging, target)
            clearHover()
        }
    }

    private fun clearHover() {
        hoverKey = null
        mergeTargetKey = null
    }

    private fun swap(dragging: String, target: String) {
        val list = order?.toMutableList() ?: return
        val from = list.indexOf(dragging)
        val to = list.indexOf(target)
        if (from < 0 || to < 0) return
        list.add(to, list.removeAt(from))
        order = list
        lastSwapped = target
        onMove()
    }

    internal fun end() {
        val result = order
        val dragged = draggingKey
        val mergeInto = mergeTargetKey
        draggingKey = null
        clearHover()
        val merge = onMerge
        if (merge != null && dragged != null && mergeInto != null) {
            // The dragged item leaves the list; keep the current order until the source catches up.
            merge(result ?: sourceKeys, dragged, mergeInto)
            return
        }
        if (result != null && result != startOrder) onCommit(result) else order = null
    }

    /** Translation that keeps the dragged item under the finger. */
    fun offsetOf(key: String): Offset {
        if (key != draggingKey) return Offset.Zero
        val slot = bounds[key]?.topLeft ?: return Offset.Zero
        return pointer - grab - slot
    }
}

/**
 * [onMerge] turns on merging (see [ReorderState]); [canMerge] says whether the dragged item may be
 * merged into the target.
 */
@Composable
fun rememberReorderState(
    keys: List<String>,
    onCommit: (List<String>) -> Unit,
    onMerge: ((order: List<String>, dragged: String, target: String) -> Unit)? = null,
    canMerge: (dragged: String, target: String) -> Boolean = { _, _ -> true },
): ReorderState {
    val state = remember { ReorderState() }
    val haptics = LocalHapticFeedback.current
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentOnMerge by rememberUpdatedState(onMerge)
    val currentCanMerge by rememberUpdatedState(canMerge)
    state.onCommit = { currentOnCommit(it) }
    state.onMerge = if (onMerge != null) { order, dragged, target -> currentOnMerge?.invoke(order, dragged, target) } else null
    state.canMerge = { dragged, target -> currentCanMerge(dragged, target) }
    state.onMergeReady = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    state.onMove = { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    LaunchedEffect(keys) { state.onSourceChanged(keys) }
    if (state.sourceKeys.isEmpty()) state.sourceKeys = keys
    return state
}

/** Put on the container (grid or row) that holds the reorderable items. */
fun Modifier.reorderContainer(
    state: ReorderState,
    onDragStart: () -> Unit,
    onEmptyLongPress: (() -> Unit)? = null,
): Modifier = this
    .onGloballyPositioned { state.containerOrigin = it.positionInWindow() }
    .pointerInput(state, onEmptyLongPress) {
        awaitEachGesture {
            // Observe in the Initial pass and consume nothing until a drag actually starts,
            // so taps, long-press menus and scrolling behave exactly as without reordering.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downWindow = state.containerOrigin + down.position
            val key = state.keyAt(downWindow)
            if (key == null && (onEmptyLongPress == null || state.blockers.values.any { it.contains(downWindow) })) {
                return@awaitEachGesture
            }

            val heldStill = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) return@withTimeoutOrNull false
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull false
                    }
                }
                false
            } ?: true
            if (!heldStill) return@awaitEachGesture
            if (key == null) {
                // Long-pressed the space between items.
                onEmptyLongPress?.invoke()
                return@awaitEachGesture
            }

            var dragging = false
            while (true) {
                // Wake up now and then while the finger rests, so dwell-based merging and reordering happen.
                val event = withTimeoutOrNull(TICK_MS) { awaitPointerEvent(PointerEventPass.Initial) }
                if (event == null) {
                    if (dragging) state.tick()
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging) change.consume()
                    break
                }
                if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    onDragStart()
                    state.start(key, state.containerOrigin + down.position)
                }
                if (dragging) {
                    change.consume()
                    state.dragTo(state.containerOrigin + change.position)
                }
            }
            if (dragging) state.end()
        }
    }

/** Put on each reorderable item. */
fun Modifier.reorderItem(state: ReorderState, key: String): Modifier = this
    .zIndex(if (state.draggingKey == key) 1f else 0f)
    .onGloballyPositioned { state.bounds[key] = Rect(it.positionInWindow(), it.size.toSize()) }
    .graphicsLayer {
        val offset = state.offsetOf(key)
        translationX = offset.x
        translationY = offset.y
        val lifted = state.draggingKey == key
        val merging = state.mergeTargetKey != null
        // The merge target swells to take the dragged item, which shrinks toward it.
        val scale = when {
            lifted && merging -> 0.85f
            lifted -> 1.12f
            state.mergeTargetKey == key -> 1.18f
            else -> 1f
        }
        scaleX = scale
        scaleY = scale
        alpha = if (lifted) 0.92f else 1f
    }

/** Monotonic milliseconds; plain JVM so the state is unit-testable. */
private fun nowMillis(): Long = System.nanoTime() / 1_000_000

private const val MIDDLE_FRACTION = 0.3f
private const val MERGE_DWELL_MS = 350L
private const val SWAP_DWELL_MS = 160L
private const val TICK_MS = 60L

/** Marks an area inside the container that isn't reorderable and isn't empty space (e.g. a widget). */
@Composable
fun Modifier.reorderBlocker(state: ReorderState, key: String): Modifier {
    DisposableEffect(state, key) { onDispose { state.blockers.remove(key) } }
    return onGloballyPositioned { state.blockers[key] = Rect(it.positionInWindow(), it.size.toSize()) }
}
