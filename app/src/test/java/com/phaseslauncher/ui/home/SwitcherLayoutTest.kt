package com.phaseslauncher.ui.home

import androidx.compose.ui.geometry.Offset
import com.phaseslauncher.data.settings.SwitcherSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun tiles(count: Int, mirrored: Boolean = false, anchorY: Float = 1200f) = tileLayout(
        count = count,
        anchorY = anchorY,
        width = width,
        top = 140f,
        bottom = 2080f,
        margin = 44f,
        gap = 28f,
        maxTileHeight = 340f,
        minTileHeight = 200f,
        mirrored = mirrored,
    )

    @Test
    fun eachTilePicksItselfAndTheGridFitsTheArea() {
        for (mirrored in listOf(false, true)) {
            for (count in listOf(1, 3, 5, 8, 14, 30)) {
                val t = tiles(count, mirrored)
                for (i in 0 until count) assertEquals("$count $mirrored", i, t.indexAt(t.centerOf(i), slack = 0f))
                assertTrue("$count", t.top >= 140f && t.top + t.height <= 2080f + 0.5f)
                assertTrue(t.left + t.width <= width - 44f + 0.5f)
            }
        }
    }

    @Test
    fun tilesStartOnTheBarSideAndCancelOffTheGrid() {
        val left = tiles(5)
        val right = tiles(5, mirrored = true)
        assertTrue(left.centerOf(0).x < width / 2f)
        assertTrue(right.centerOf(0).x > width / 2f)
        // The bar's edge, at the grid's height, is still on the grid.
        assertEquals(0, right.indexAt(Offset(width, right.centerOf(0).y), slack = 60f))
        assertNull(left.indexAt(Offset(width / 2f, left.top - 200f), slack = 60f))
        // The empty cell at the end of a partial last row picks nothing.
        assertNull(left.indexAt(left.centerOf(4) + Offset(left.tileWidth + left.gap, 0f), slack = 0f))
    }

    @Test
    fun crowdedTilesUseMoreColumns() {
        assertEquals(2, tiles(4).columns)
        assertTrue(tiles(30).columns > 3)
    }

    @Test
    fun magnificationPeaksUnderTheFingerAndFades() {
        assertEquals(1f, magnification(0f), 0.0001f)
        assertTrue(magnification(1f) < 1f && magnification(1f) > magnification(2f))
        assertEquals(magnification(-1.5f), magnification(1.5f), 0.0001f)
        assertTrue(magnification(4f) < 0.01f)
    }

    private fun slider(count: Int, fromRight: Boolean, anchorY: Float = 1200f) =
        sliderLayout(count, anchorY, width, height, margin = 28f, above = 470f, below = 220f, fromRight = fromRight)

    @Test
    fun sliderRunsInwardFromTheBar() {
        for (fromRight in listOf(false, true)) {
            val s = slider(6, fromRight)
            for (i in 0 until 6) assertEquals(i, s.indexAt(Offset(s.centerX(i), s.y), reach = 100f))
            val edge = if (fromRight) width else 0f
            assertEquals(0, s.indexAt(Offset(edge, s.y), reach = 100f))
        }
    }

    @Test
    fun sliderCancelsAwayFromTheTrackAndStaysClearOfTheEdges() {
        val s = slider(6, fromRight = true)
        assertNull(s.indexAt(Offset(width / 2f, s.y + 300f), reach = 100f))
        assertEquals(470f, slider(6, fromRight = true, anchorY = 10f).y, 0.01f)
        assertEquals(height - 220f, slider(6, fromRight = true, anchorY = height).y, 0.01f)
    }
}
