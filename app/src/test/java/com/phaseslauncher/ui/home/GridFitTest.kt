package com.phaseslauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridFitTest {

    private val metrics = GridMetrics(labelHeight = 22f)

    /** Total height [rows] rows need at [iconDp], using the same formula as the layout. */
    private fun neededHeight(rows: Int, iconDp: Int): Float = metrics.gridPaddingVertical + rows * metrics.rowHeight(iconDp.toFloat())

    private fun neededWidth(columns: Int, iconDp: Int): Float =
        metrics.gridPaddingHorizontal + columns * (iconDp + metrics.tilePaddingHorizontal)

    @Test
    fun fewRowsGetTheLargestIcons() {
        assertEquals(88, fitIconSize(rows = 2, columns = 3, widthDp = 360f, heightDp = 520f, metrics = metrics))
    }

    @Test
    fun resultAlwaysFitsWhenPossible() {
        for (columns in 3..6) {
            for (rows in 1..12) {
                val size = fitIconSize(rows, columns, 360f, 520f, metrics)
                if (size > 36) {
                    assertTrue("rows=$rows columns=$columns $size too tall", neededHeight(rows, size) <= 520f)
                    assertTrue("rows=$rows columns=$columns $size too wide", neededWidth(columns, size) <= 360f)
                }
            }
        }
    }

    @Test
    fun iconsShrinkAsRowsAreAdded() {
        var previous = Int.MAX_VALUE
        for (rows in 1..10) {
            val size = fitIconSize(rows, 4, 360f, 520f, metrics)
            assertTrue("rows=$rows: $size > $previous", size <= previous)
            previous = size
        }
    }

    @Test
    fun moreColumnsMeanNarrowerIcons() {
        assertTrue(fitIconSize(2, 6, 360f, 520f, metrics) < fitIconSize(2, 3, 360f, 520f, metrics))
    }

    @Test
    fun tooManyRowsFallBackToTheSmallestIcons() {
        assertEquals(36, fitIconSize(rows = 40, columns = 4, widthDp = 360f, heightDp = 520f, metrics = metrics))
    }

    @Test
    fun noRowsIsHarmless() {
        assertEquals(88, fitIconSize(rows = 0, columns = 4, widthDp = 360f, heightDp = 520f, metrics = metrics))
    }
}
