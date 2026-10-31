package com.themelauncher.ui.home

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Columns and icon size (dp) for the home grid. */
data class GridFit(val columns: Int, val iconSizeDp: Int)

/**
 * Space around and between tiles, in dp. Must match the home grid's padding and [com.themelauncher.ui.components.TilePadding].
 */
data class GridMetrics(
    val gridPaddingHorizontal: Float = 24f,
    val gridPaddingVertical: Float = 16f,
    val rowSpacing: Float = 4f,
    val tilePaddingHorizontal: Float = 8f,
    val tilePaddingVertical: Float = 16f,
    /** Gap plus one line of label text; 0 without labels. */
    val labelHeight: Float = 0f,
)

/**
 * Picks the column count and the largest icon size at which [count] tiles fit in [widthDp] by
 * [heightDp] without scrolling. Ties go to fewer columns. When even [minIconDp] icons don't fit,
 * returns those (the grid then scrolls).
 */
fun fitGrid(
    count: Int,
    widthDp: Float,
    heightDp: Float,
    metrics: GridMetrics = GridMetrics(),
    columnRange: IntRange = 3..8,
    minIconDp: Int = 36,
    maxIconDp: Int = 88,
): GridFit {
    if (count <= 0) return GridFit(columnRange.first, maxIconDp)
    var best = GridFit(columnRange.last, minIconDp)
    var bestSize = Float.NEGATIVE_INFINITY
    for (columns in columnRange) {
        val rows = ceil(count / columns.toFloat()).toInt()
        val cellWidth = (widthDp - metrics.gridPaddingHorizontal) / columns
        val rowHeight = (heightDp - metrics.gridPaddingVertical - metrics.rowSpacing * (rows - 1)) / rows
        val size = min(
            cellWidth - metrics.tilePaddingHorizontal,
            rowHeight - metrics.tilePaddingVertical - metrics.labelHeight,
        )
        if (size > bestSize) {
            bestSize = size
            best = GridFit(columns, floor(size).toInt().coerceIn(minIconDp, maxIconDp))
        }
        // More columns only make icons narrower once rows no longer shrink; stop at the cap.
        if (size >= maxIconDp) break
    }
    return best
}
