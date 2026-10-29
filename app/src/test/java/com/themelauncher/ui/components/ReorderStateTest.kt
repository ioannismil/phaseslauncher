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
}
