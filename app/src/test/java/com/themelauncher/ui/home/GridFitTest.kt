package com.themelauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class GridFitTest {

    private val metrics = GridMetrics(labelHeight = 22f)

    /** Total height the grid needs for [fit], using the same formula as the layout. */
    private fun neededHeight(count: Int, fit: GridFit): Float {
        val rows = ceil(count / fit.columns.toFloat()).toInt()
        val row = fit.iconSizeDp + metrics.tilePaddingVertical + metrics.labelHeight
        return metrics.gridPaddingVertical + rows * row + (rows - 1) * metrics.rowSpacing
    }

    private fun neededWidth(fit: GridFit): Float =
        metrics.gridPaddingHorizontal + fit.columns * (fit.iconSizeDp + metrics.tilePaddingHorizontal)

    @Test
    fun fewAppsGetTheLargestIconsInTheFewestColumns() {
        val fit = fitGrid(2, 360f, 520f, metrics)
        assertEquals(GridFit(3, 88), fit)
    }

    @Test
    fun resultAlwaysFitsWhenPossible() {
        for (count in 1..60) {
            val fit = fitGrid(count, 360f, 520f, metrics)
            if (fit.iconSizeDp > 36) {
                assertTrue("count=$count $fit too tall", neededHeight(count, fit) <= 520f)
                assertTrue("count=$count $fit too wide", neededWidth(fit) <= 360f)
            }
        }
    }

    @Test
    fun iconsShrinkAsAppsAreAdded() {
        var previous = Int.MAX_VALUE
        for (count in listOf(4, 12, 24, 40)) {
            val size = fitGrid(count, 360f, 520f, metrics).iconSizeDp
            assertTrue("count=$count: $size > $previous", size <= previous)
            previous = size
        }
    }

    @Test
    fun manyAppsUseMoreColumns() {
        assertTrue(fitGrid(40, 360f, 520f, metrics).columns > fitGrid(6, 360f, 520f, metrics).columns)
    }

    @Test
    fun tooManyAppsFallBackToTheSmallestIcons() {
        assertEquals(GridFit(8, 36), fitGrid(400, 360f, 520f, metrics))
    }

    @Test
    fun noAppsIsHarmless() {
        assertEquals(GridFit(3, 88), fitGrid(0, 360f, 520f, metrics))
    }
}
