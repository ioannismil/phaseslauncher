package com.themelauncher.ui.home

import androidx.compose.ui.geometry.Offset
import com.themelauncher.data.settings.SwitcherSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcGeometryTest {

    private companion object {
        const val PITCH = 165f
        const val LABEL_ROOM = 520f
    }

    private val width = 1080f
    private val height = 2220f

    private fun geometry(count: Int, side: SwitcherSide, anchorY: Float = 1200f) = arcGeometry(
        count = count,
        anchor = Offset(if (side == SwitcherSide.Right) width else 0f, anchorY),
        side = side,
        width = width,
        height = height,
        pitch = PITCH,
        minRadius = 470f,
        maxSpacing = 240f,
        labelRoom = LABEL_ROOM,
        edgeInset = 150f,
        margin = 130f,
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
    fun itemsLieOnACircleEquallySpaced() {
        for (count in listOf(2, 5, 9, 17)) {
            val g = geometry(count, SwitcherSide.Right)
            g.positions.forEach { assertEquals("count=$count", g.radius, (it - g.arcCenter).getDistance(), 1f) }
            val chords = g.positions.zipWithNext { a, b -> (b - a).getDistance() }
            chords.forEach { assertEquals("count=$count", chords.first(), it, 1f) }
        }
    }

    @Test
    fun consecutiveItemsAreAtLeastAPitchApartInHeight() {
        for (count in 2..10) {
            val g = geometry(count, SwitcherSide.Right)
            assertEquals("count=$count", 1f, g.itemScale)
            g.positions.zipWithNext().forEach { (a, b) -> assertTrue("count=$count", b.y - a.y >= PITCH - 0.5f) }
        }
    }

    @Test
    fun fewItemsUseAWideArc() {
        val g = geometry(5, SwitcherSide.Right)
        // The ends curve back toward the edge, well short of the middle item.
        assertTrue(width - g.positions.first().x < (width - g.positions[2].x) * 0.6f)
    }

    @Test
    fun namesBesideTheItemsStayOnScreen() {
        for (count in listOf(3, 8, 14, 30)) {
            for (side in SwitcherSide.entries) {
                val g = geometry(count, side)
                g.positions.forEach { p ->
                    val fromEdge = if (side == SwitcherSide.Right) width - p.x else p.x
                    assertTrue("count=$count $side", fromEdge + LABEL_ROOM <= width)
                }
            }
        }
    }

    @Test
    fun crowdedArcsShrinkItemsAndStayOnScreen() {
        val g = geometry(30, SwitcherSide.Right)
        assertTrue(g.itemScale < 1f)
        assertTrue(g.positions.all { it.y in 0f..height })
        assertEquals(g.positions.sortedBy { it.y }, g.positions)
    }

    @Test
    fun nearestItemIgnoresTheDeadZoneAndPicksTheClosest() {
        val g = geometry(3, SwitcherSide.Right)
        assertNull(nearestItem(g, g.center + Offset(-20f, 0f), deadZone = 100f))
        assertEquals(2, nearestItem(g, g.positions[2] + Offset(5f, 5f), deadZone = 100f))
    }
}
