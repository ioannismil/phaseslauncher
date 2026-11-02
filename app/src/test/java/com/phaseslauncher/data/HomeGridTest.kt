package com.phaseslauncher.data

import com.phaseslauncher.data.items.GridCell
import com.phaseslauncher.data.items.GridEntry
import com.phaseslauncher.data.items.GridPlacement
import com.phaseslauncher.data.items.HomeGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGridTest {

    private fun List<GridPlacement>.at(key: String) = first { it.key == key }.let { GridCell(it.x, it.y) }

    private fun assertNoOverlaps(layout: List<GridPlacement>) {
        layout.forEachIndexed { i, a ->
            layout.drop(i + 1).forEach { b -> assertFalse("$a overlaps $b", a.overlaps(b)) }
        }
    }

    @Test
    fun unplacedItemsFillTopDownLeftToRight() {
        val layout = HomeGrid.resolve(listOf("a", "b", "c", "d", "e").map { GridEntry(it, null) }, columns = 4, rows = 6)
        assertEquals(GridCell(0, 0), layout.at("a"))
        assertEquals(GridCell(3, 0), layout.at("d"))
        assertEquals(GridCell(0, 1), layout.at("e"))
    }

    @Test
    fun bottomUpRightToLeftStartsInTheBottomRightCorner() {
        val layout = HomeGrid.resolve(listOf("a", "b").map { GridEntry(it, null) }, columns = 4, rows = 6, fromBottom = true, rightToLeft = true)
        assertEquals(GridCell(3, 5), layout.at("a"))
        assertEquals(GridCell(2, 5), layout.at("b"))
    }

    @Test
    fun placedItemsKeepTheirCellsAndOthersFillAroundThem() {
        val layout = HomeGrid.resolve(
            listOf(GridEntry("a", null), GridEntry("b", GridCell(0, 0)), GridEntry("c", GridCell(2, 3))),
            columns = 4, rows = 6,
        )
        assertEquals(GridCell(0, 0), layout.at("b"))
        assertEquals(GridCell(2, 3), layout.at("c"))
        assertEquals(GridCell(1, 0), layout.at("a"))
    }

    @Test
    fun cellsOffTheGridOrAlreadyTakenAreReplaced() {
        val layout = HomeGrid.resolve(
            listOf(GridEntry("a", GridCell(1, 0)), GridEntry("b", GridCell(1, 0)), GridEntry("c", GridCell(5, 0))),
            columns = 4, rows = 6,
        )
        assertEquals(GridCell(1, 0), layout.at("a"))
        assertNoOverlaps(layout)
        assertEquals(3, layout.size)
    }

    @Test
    fun unplacedWidgetsAreFullWidthAboveTheApps() {
        val layout = HomeGrid.resolve(
            listOf(GridEntry(HomeGrid.widgetKey(7), null, width = 9, height = 2), GridEntry("a", null)),
            columns = 4, rows = 6,
        )
        assertEquals(GridPlacement(HomeGrid.widgetKey(7), 0, 0, 4, 2), layout.first { it.key == HomeGrid.widgetKey(7) })
        assertEquals(GridCell(0, 2), layout.at("a"))
    }

    @Test
    fun movingToAFreeCellLeavesTheRestAlone() {
        val start = listOf(GridPlacement("a", 0, 0), GridPlacement("b", 1, 0))
        val moved = HomeGrid.move(start, "a", GridCell(3, 4), columns = 4)
        assertEquals(GridCell(3, 4), moved.at("a"))
        assertEquals(GridCell(1, 0), moved.at("b"))
    }

    @Test
    fun movingOntoAnotherItemSwapsThem() {
        val start = listOf(GridPlacement("a", 0, 0), GridPlacement("b", 2, 1))
        val moved = HomeGrid.move(start, "a", GridCell(2, 1), columns = 4)
        assertEquals(GridCell(2, 1), moved.at("a"))
        assertEquals(GridCell(0, 0), moved.at("b"))
    }

    @Test
    fun aWidgetDroppedOnAppsTradesPlacesWithThem() {
        val widget = HomeGrid.widgetKey(1)
        val start = listOf(
            GridPlacement(widget, 0, 0, 2, 2),
            GridPlacement("a", 2, 0), GridPlacement("b", 3, 0),
            GridPlacement("c", 2, 1), GridPlacement("d", 3, 1),
        )
        val moved = HomeGrid.move(start, widget, GridCell(2, 0), columns = 4)
        assertEquals(GridCell(2, 0), moved.at(widget))
        assertEquals(GridCell(0, 0), moved.at("a"))
        assertEquals(GridCell(1, 1), moved.at("d"))
        assertNoOverlaps(moved)
    }

    @Test
    fun displacedItemsGoToTheNearestFreeSpot() {
        // A full first row; dropping a 2×1 widget on it pushes two apps out, into the row below.
        val widget = HomeGrid.widgetKey(1)
        val start = listOf(GridPlacement("a", 0, 0), GridPlacement("b", 1, 0), GridPlacement("c", 2, 0), GridPlacement("d", 3, 0), GridPlacement(widget, 0, 3, 2, 1))
        val moved = HomeGrid.move(start, widget, GridCell(1, 0), columns = 4)
        assertNoOverlaps(moved)
        assertEquals(GridCell(1, 0), moved.at(widget))
        assertEquals(GridCell(0, 0), moved.at("a"))
        assertEquals(GridCell(3, 0), moved.at("d"))
        // b and c traded into the widget's old spot.
        assertEquals(GridCell(0, 3), moved.at("b"))
        assertEquals(GridCell(1, 3), moved.at("c"))
    }

    @Test
    fun movesStayInsideTheColumns() {
        val widget = HomeGrid.widgetKey(1)
        val moved = HomeGrid.move(listOf(GridPlacement(widget, 0, 0, 3, 1)), widget, GridCell(3, 2), columns = 4)
        assertEquals(GridCell(1, 2), moved.at(widget))
    }

    @Test
    fun insertingPushesTheOccupantAside() {
        val start = listOf(GridPlacement("a", 1, 1))
        val layout = HomeGrid.insert(start, "new", GridCell(1, 1), columns = 4)
        assertEquals(GridCell(1, 1), layout.at("new"))
        assertNoOverlaps(layout)
        assertEquals(2, layout.size)
    }

    @Test
    fun growingAWidgetMovesWhatItNowCovers() {
        val widget = HomeGrid.widgetKey(1)
        val start = listOf(GridPlacement(widget, 0, 0, 2, 1), GridPlacement("a", 0, 1), GridPlacement("b", 3, 3))
        val layout = HomeGrid.resize(start, widget, width = 2, height = 2, columns = 4)
        assertEquals(GridPlacement(widget, 0, 0, 2, 2), layout.first { it.key == widget })
        assertEquals(GridCell(3, 3), layout.at("b"))
        assertNoOverlaps(layout)
    }

    @Test
    fun widgetKeysRoundTrip() {
        assertEquals(42, HomeGrid.widgetId(HomeGrid.widgetKey(42)))
        assertNull(HomeGrid.widgetId("com.example/.Main"))
    }

    @Test
    fun readingOrderIsRowByRow() {
        val order = HomeGrid.readingOrder(listOf(GridPlacement("c", 0, 2), GridPlacement("b", 3, 0), GridPlacement("a", 0, 0)))
        assertEquals(listOf("a", "b", "c"), order.map { it.key })
    }
}
