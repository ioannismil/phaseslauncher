package com.phaseslauncher.ui.home

import kotlin.math.floor
import kotlin.math.min

/**
 * Space around and between tiles, in dp. Must match the home grid's padding and [com.phaseslauncher.ui.components.TilePadding].
 */
data class GridMetrics(
    val gridPaddingHorizontal: Float = 24f,
    val gridPaddingVertical: Float = 16f,
    val rowSpacing: Float = 4f,
    val tilePaddingHorizontal: Float = 8f,
    val tilePaddingVertical: Float = 16f,
    /** Gap plus one line of label text; 0 without labels. */
    val labelHeight: Float = 0f,
) {
    /** One row of tiles with [iconDp] icons. */
    fun rowHeight(iconDp: Float): Float = iconDp + tilePaddingVertical + labelHeight + rowSpacing
}

/**
 * The largest icon size (dp) at which [rows] rows of [columns] tiles fit in [widthDp] by [heightDp]
 * without scrolling, between [minIconDp] and [maxIconDp]. The columns stay as the user set them;
 * only the icons (and so the rows) shrink.
 */
fun fitIconSize(
    rows: Int,
    columns: Int,
    widthDp: Float,
    heightDp: Float,
    metrics: GridMetrics = GridMetrics(),
    minIconDp: Int = 36,
    maxIconDp: Int = 88,
): Int {
    if (rows <= 0 || columns <= 0) return maxIconDp
    val cellWidth = (widthDp - metrics.gridPaddingHorizontal) / columns
    val rowHeight = (heightDp - metrics.gridPaddingVertical) / rows
    val size = min(
        cellWidth - metrics.tilePaddingHorizontal,
        rowHeight - metrics.rowSpacing - metrics.tilePaddingVertical - metrics.labelHeight,
    )
    return floor(size).toInt().coerceIn(minIconDp, maxIconDp)
}
