package com.themelauncher.ui.home

import androidx.compose.ui.geometry.Offset
import com.themelauncher.data.settings.SwitcherSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcGeometryTest {

    private val width = 1080f
    private val height = 2220f

    private fun geometry(count: Int, side: SwitcherSide, anchorY: Float = 1200f) = arcGeometry(
        count = count,
        anchor = Offset(if (side == SwitcherSide.Right) width else 0f, anchorY),
        side = side,
        width = width,
        height = height,
        spacing = 200f,
        minRadius = 400f,
        ringGap = 250f,
        margin = 150f,
    )

    @Test
    fun rightSideItemsOpenTowardTheMiddleTopToBottom() {
        val g = geometry(4, SwitcherSide.Right)
        assertEquals(width, g.center.x)
        assertTrue(g.positions.all { it.x < width && it.x > 0f })
        assertEquals(g.positions.sortedBy { it.y }, g.positions)
    }

    @Test
    fun leftSideMirrorsRightSide() {
        val right = geometry(4, SwitcherSide.Right)
        val left = geometry(4, SwitcherSide.Left)
        right.positions.zip(left.positions).forEach { (r, l) ->
            assertEquals(width - r.x, l.x, 0.01f)
            assertEquals(r.y, l.y, 0.01f)
        }
    }

    @Test
    fun arcStaysOnScreenWhenAnchoredNearTheBottom() {
        val g = geometry(5, SwitcherSide.Right, anchorY = height - 10f)
        assertTrue(g.positions.all { it.y in 0f..height })
    }

    @Test
    fun manyThemesUseTwoRingsWithinTheMaxRadius() {
        val g = geometry(16, SwitcherSide.Right)
        assertEquals(16, g.positions.size)
        val distances = g.positions.map { (it - g.center).getDistance() }
        // Two clusters of distances: the outer ring and the inner ring.
        val rings = distances.sorted().fold(mutableListOf<Float>()) { acc, d ->
            if (acc.isEmpty() || d - acc.last() > 1f) acc += d
            acc
        }
        assertEquals(2, rings.size)
        assertTrue(distances.all { it <= g.radius + 0.5f })
    }

    @Test
    fun nearestItemIgnoresTheDeadZoneAndPicksTheClosest() {
        val g = geometry(3, SwitcherSide.Right)
        assertNull(nearestItem(g, g.center + Offset(-20f, 0f), deadZone = 100f))
        assertEquals(2, nearestItem(g, g.positions[2] + Offset(5f, 5f), deadZone = 100f))
    }
}
