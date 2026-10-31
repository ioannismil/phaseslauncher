package com.themelauncher.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ReorderStateTest {

    private lateinit var state: ReorderState
    private var committed: List<String>? = null

    /** Four 100px cells in a row: a b c d. */
    private fun layOut(order: List<String>) {
        order.forEachIndexed { i, key -> state.bounds[key] = Rect(i * 100f, 0f, i * 100f + 100f, 100f) }
    }

    @Before
    fun setUp() {
        state = ReorderState()
        state.onCommit = { committed = it }
        state.onSourceChanged(listOf("a", "b", "c", "d"))
        layOut(listOf("a", "b", "c", "d"))
    }

    @Test
    fun draggingOverAnItemMovesTheDraggedItemThere() {
        state.start("d", Offset(350f, 50f))
        state.dragTo(Offset(250f, 50f))
        state.dragTo(Offset(150f, 50f))
        state.dragTo(Offset(50f, 50f))
        state.end()
        assertEquals(listOf("d", "a", "b", "c"), committed)
    }

    @Test
    fun anItemAnimatingBackUnderTheFingerDoesNotSwapBack() {
        state.start("d", Offset(350f, 50f))
        state.dragTo(Offset(250f, 50f)) // swaps with c → a b d c
        // c is still animating and its (stale) bounds still cover the finger.
        state.dragTo(Offset(260f, 50f))
        state.end()
        assertEquals(listOf("a", "b", "d", "c"), committed)
    }

    @Test
    fun noMoveMeansNoCommit() {
        state.start("b", Offset(150f, 50f))
        state.dragTo(Offset(160f, 50f))
        state.end()
        assertNull(committed)
        assertEquals(listOf("a", "b", "c", "d"), state.arrange(listOf("a", "b", "c", "d")) { it })
    }

    @Test
    fun arrangeKeepsTheDroppedOrderUntilTheSourceCatchesUp() {
        state.start("a", Offset(50f, 50f))
        state.dragTo(Offset(150f, 50f))
        state.end()
        val source = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "a", "c", "d"), state.arrange(source) { it })

        state.onSourceChanged(listOf("b", "a", "c", "d"))
        assertEquals(listOf("b", "a", "c", "d"), state.arrange(listOf("b", "a", "c", "d")) { it })
    }

    @Test
    fun theDraggedItemFollowsTheFinger() {
        state.start("a", Offset(40f, 60f))
        state.dragTo(Offset(70f, 90f))
        assertEquals(Offset(30f, 30f), state.offsetOf("a"))
        assertEquals(Offset.Zero, state.offsetOf("b"))
    }

    private fun enableMerging(): MutableList<Triple<List<String>, String, String>> {
        val merges = mutableListOf<Triple<List<String>, String, String>>()
        state.onMerge = { order, dragged, target -> merges += Triple(order, dragged, target) }
        return merges
    }

    @Test
    fun restingOnAnItemsMiddleMergesInsteadOfReordering() {
        val merges = enableMerging()
        state.start("a", Offset(50f, 40f))
        state.dragTo(Offset(250f, 42f), now = 1_000) // middle of c
        state.tick(now = 1_200)
        assertNull(state.mergeTargetKey)
        state.tick(now = 1_400)
        assertEquals("c", state.mergeTargetKey)
        state.end()
        assertEquals(listOf(Triple(listOf("a", "b", "c", "d"), "a", "c")), merges)
        assertNull(committed)
    }

    @Test
    fun withMergingOnReorderingWaitsForTheFingerToRestOnAnEdge() {
        enableMerging()
        state.start("a", Offset(50f, 40f))
        state.dragTo(Offset(205f, 90f), now = 1_000) // c's lower-left corner, not its middle
        state.end()
        assertNull(committed) // passed over without resting: no swap

        state.start("a", Offset(50f, 40f))
        state.dragTo(Offset(205f, 90f), now = 2_000)
        state.tick(now = 2_200)
        state.end()
        assertEquals(listOf("b", "c", "a", "d"), committed)
    }

    @Test
    fun itemsThatCannotTakeTheDraggedOneAreReorderedNotMerged() {
        val merges = enableMerging()
        state.canMerge = { _, target -> target != "c" }
        state.start("a", Offset(50f, 40f))
        state.dragTo(Offset(250f, 42f), now = 1_000)
        state.tick(now = 2_000)
        state.end()
        assertEquals(emptyList<Any>(), merges)
        assertEquals(listOf("b", "c", "a", "d"), committed)
    }

    @Test
    fun dropIndexFollowsReadingOrder() {
        val keys = listOf("a", "b", "c", "d")
        assertEquals(0, state.dropIndex(Offset(20f, 50f), keys))
        assertEquals(2, state.dropIndex(Offset(180f, 50f), keys))
        assertEquals(4, state.dropIndex(Offset(390f, 50f), keys))
        assertEquals(4, state.dropIndex(Offset(50f, 500f), keys))
    }

    @Test
    fun dropIndexFollowsBottomUpRightToLeftOrder() {
        // Two rows filled from the bottom right: a b on the bottom row (right to left), c d above.
        state.rowsUpward = true
        state.rightToLeft = true
        state.bounds["a"] = Rect(100f, 100f, 200f, 200f)
        state.bounds["b"] = Rect(0f, 100f, 100f, 200f)
        state.bounds["c"] = Rect(100f, 0f, 200f, 100f)
        state.bounds["d"] = Rect(0f, 0f, 100f, 100f)
        val keys = listOf("a", "b", "c", "d")
        assertEquals(0, state.dropIndex(Offset(190f, 150f), keys))
        assertEquals(1, state.dropIndex(Offset(120f, 150f), keys))
        assertEquals(2, state.dropIndex(Offset(190f, 50f), keys))
        assertEquals(4, state.dropIndex(Offset(10f, 50f), keys))
        assertEquals(0, state.dropIndex(Offset(50f, 300f), keys))
    }
}
