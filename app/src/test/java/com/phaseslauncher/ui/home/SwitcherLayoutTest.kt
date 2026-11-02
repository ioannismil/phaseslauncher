package com.phaseslauncher.ui.home

import com.phaseslauncher.data.settings.SwitcherSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitcherLayoutTest {

    private val width = 1080f
    private val height = 2220f

    private fun rail(count: Int, anchorY: Float = 1200f) = railLayout(
        count = count,
        extras = 2,
        anchorY = anchorY,
        screenHeight = height,
        row = 154f,
        minRow = 110f,
        divider = 36f,
        padding = 38f,
        margin = 88f,
    )

    @Test
    fun railCentersOnTheBarAndPicksTheRowUnderTheFinger() {
        val r = rail(6)
        assertEquals(1200f, r.top + r.height / 2f, 0.5f)
        r.rowTops.forEachIndexed { i, top -> assertEquals(i, r.indexAt(top + r.rowHeight / 2f)) }
        assertEquals(0, r.indexAt(-500f))
        assertEquals(5, r.indexAt(height + 500f))
    }

    @Test
    fun railStaysOnScreenNearTheEdgesAndShrinksRowsWhenCrowded() {
        val low = rail(6, anchorY = height - 10f)
        assertTrue(low.top + low.height <= height - 88f + 0.5f)
        val crowded = rail(20)
        assertTrue(crowded.rowHeight < 154f)
        assertTrue(crowded.rowHeight >= 110f)
    }

    @Test
    fun railLeavesRoomForTheDividerBeforeTheExtras() {
        val r = rail(5)
        assertEquals(r.rowHeight, r.rowTops[2] - r.rowTops[1], 0.01f)
        assertEquals(r.rowHeight + 36f, r.rowTops[3] - r.rowTops[2], 0.01f)
    }

    private fun dial(count: Int, side: SwitcherSide = SwitcherSide.Right) = dialGeometry(
        count = count,
        anchorY = 1200f,
        side = side,
        width = width,
        height = height,
        pitch = 170f,
        minRadius = 325f,
        outerPad = 115f,
        margin = 33f,
    )

    @Test
    fun dialStartsAtTheTopAndFitsOnScreen() {
        for (side in SwitcherSide.entries) {
            for (count in listOf(3, 8, 14, 30)) {
                val d = dial(count, side)
                assertTrue(d.positions[0].y < d.center.y)
                assertEquals(d.center.x, d.positions[0].x, 0.5f)
                val outer = d.radius + 115f
                assertTrue("$side $count", d.center.x - outer >= 0f && d.center.x + outer <= width)
                assertTrue(d.itemScale in 0.6f..1f)
            }
        }
    }

    @Test
    fun dialSitsTowardTheBarSide() {
        assertTrue(dial(6, SwitcherSide.Right).center.x > width / 2f)
        assertTrue(dial(6, SwitcherSide.Left).center.x < width / 2f)
    }

    @Test
    fun bandsFillTheirAreaWhicheverIsOpen() {
        val b = bandLayout(count = 6, top = 100f, bottom = 1900f, gap = 16f, maxExpanded = 640f)
        for (open in 0 until 6) {
            assertEquals(1900f, b.topOf(5, open) + if (open == 5) b.expanded else b.collapsed, 0.5f)
        }
    }

    @Test
    fun bandUnderTheFingerIsStableWhileItGrows() {
        val b = bandLayout(count = 6, top = 100f, bottom = 1900f, gap = 16f, maxExpanded = 640f)
        var y = 100f
        while (y < 1900f) {
            val picked = b.indexAt(y, current = null)
            // Once picked and expanded, the same finger position keeps it.
            assertEquals("y=$y", picked, b.indexAt(y, current = picked))
            y += 7f
        }
    }
}
