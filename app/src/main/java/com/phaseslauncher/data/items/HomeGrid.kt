package com.phaseslauncher.data.items

import kotlin.math.max

/** A cell on a phase's home grid: column [x] from the left, row [y] from the top. */
data class GridCell(val x: Int, val y: Int)

/** Something on the home grid ([key]: an item key or [HomeGrid.widgetKey]) and the cells it covers. */
data class GridPlacement(val key: String, val x: Int, val y: Int, val width: Int = 1, val height: Int = 1) {
    /** Exclusive. */
    val right: Int get() = x + width

    /** Exclusive. */
    val bottom: Int get() = y + height

    fun overlaps(other: GridPlacement): Boolean = x < other.right && other.x < right && y < other.bottom && other.y < bottom

    fun covers(cell: GridCell): Boolean = cell.x in x until right && cell.y in y until bottom
}

/** Something to put on the grid. A null [cell] (or one that no longer fits) means "find it a spot". */
data class GridEntry(val key: String, val cell: GridCell?, val width: Int = 1, val height: Int = 1)

/**
 * Free placement on a phase's home grid. Pure, so it is unit-tested: the grid works out where
 * everything is with [resolve], changes it with [move], [insert] and [resize], and saves the result.
 * What's in the way of a move goes to the nearest free spot; the grid grows downward as needed.
 */
object HomeGrid {
    private const val WIDGET_PREFIX = "widget:"

    fun widgetKey(appWidgetId: Int): String = WIDGET_PREFIX + appWidgetId

    fun widgetId(key: String): Int? = if (key.startsWith(WIDGET_PREFIX)) key.removePrefix(WIDGET_PREFIX).toIntOrNull() else null

    /**
     * Where each entry goes on a grid [columns] wide. Entries keep their cell if it's on the grid
     * and not taken by an earlier entry; the rest take the first free spots, top-down (or, with
     * [fromBottom], from the last of [rows] rows upward), left to right (or [rightToLeft]).
     */
    fun resolve(
        entries: List<GridEntry>,
        columns: Int,
        rows: Int,
        fromBottom: Boolean = false,
        rightToLeft: Boolean = false,
    ): List<GridPlacement> {
        val placed = ArrayList<GridPlacement>(entries.size)
        val pending = ArrayList<GridPlacement>()
        for (entry in entries) {
            val width = entry.width.coerceIn(1, columns)
            val height = max(1, entry.height)
            val cell = entry.cell
            val wanted = cell?.let { GridPlacement(entry.key, it.x, it.y, width, height) }
            if (wanted != null && wanted.x >= 0 && wanted.y >= 0 && wanted.right <= columns && placed.none(wanted::overlaps)) {
                placed += wanted
            } else {
                pending += GridPlacement(entry.key, 0, 0, width, height)
            }
        }
        pending.forEach { placed += firstFree(placed, it, columns, rows, fromBottom, rightToLeft) }
        return placed
    }

    /** [layout] with [key] moved so its top-left is [to]; anything in the way moves out of it. */
    fun move(layout: List<GridPlacement>, key: String, to: GridCell, columns: Int): List<GridPlacement> {
        val moving = layout.firstOrNull { it.key == key } ?: return layout
        val target = moving.copy(x = to.x.coerceIn(0, columns - moving.width), y = max(0, to.y))
        if (target == moving) return layout
        return place(layout.filter { it.key != key }, target, origin = moving, columns)
    }

    /** [layout] with a new 1×1 item [key] at [at]; anything there moves to the nearest free spot. */
    fun insert(layout: List<GridPlacement>, key: String, at: GridCell, columns: Int): List<GridPlacement> {
        val target = GridPlacement(key, at.x.coerceIn(0, columns - 1), max(0, at.y))
        return place(layout.filter { it.key != key }, target, origin = null, columns)
    }

    /** [layout] with [key] resized, keeping its top-left where it can; what it now covers moves out. */
    fun resize(layout: List<GridPlacement>, key: String, width: Int, height: Int, columns: Int): List<GridPlacement> {
        val current = layout.firstOrNull { it.key == key } ?: return layout
        val w = width.coerceIn(1, columns)
        val target = current.copy(x = current.x.coerceAtMost(columns - w), width = w, height = max(1, height))
        if (target == current) return layout
        return place(layout.filter { it.key != key }, target, origin = null, columns)
    }

    /** How many rows [layout] takes up. */
    fun rowsUsed(layout: List<GridPlacement>): Int = layout.maxOfOrNull { it.bottom } ?: 0

    /** Top to bottom, then left to right: the order the items read in (and are listed in elsewhere). */
    fun readingOrder(layout: List<GridPlacement>): List<GridPlacement> = layout.sortedWith(compareBy({ it.y }, { it.x }))

    /**
     * Puts [target] in [others], moving whatever it covers. Displaced items first try the same
     * spot relative to [origin] (where the moved item came from, so a swap stays a swap and a widget
     * dropped on a row of apps trades places with them), then the nearest free spot.
     */
    private fun place(others: List<GridPlacement>, target: GridPlacement, origin: GridPlacement?, columns: Int): List<GridPlacement> {
        val (displaced, staying) = others.partition { it.overlaps(target) }
        val result = ArrayList<GridPlacement>(others.size + 1)
        result += staying
        result += target
        for (item in displaced.sortedWith(compareBy({ it.y }, { it.x }))) {
            val traded = origin?.let { item.copy(x = it.x + (item.x - target.x), y = it.y + (item.y - target.y)) }
                ?.takeIf { it.x >= 0 && it.y >= 0 && it.right <= columns && result.none(it::overlaps) }
            result += traded ?: nearestFree(result, item, columns)
        }
        return result
    }

    private fun nearestFree(occupied: List<GridPlacement>, item: GridPlacement, columns: Int): GridPlacement {
        // Below everything is always free, so the search ends there at the latest.
        val lastRow = max(rowsUsed(occupied), item.bottom)
        var best: GridPlacement? = null
        var bestDistance = Int.MAX_VALUE
        for (y in 0..lastRow) {
            for (x in 0..columns - item.width) {
                val candidate = item.copy(x = x, y = y)
                if (occupied.any(candidate::overlaps)) continue
                val dx = x - item.x
                val dy = y - item.y
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
            }
        }
        return best ?: item.copy(x = 0, y = lastRow)
    }

    private fun firstFree(
        occupied: List<GridPlacement>,
        item: GridPlacement,
        columns: Int,
        rows: Int,
        fromBottom: Boolean,
        rightToLeft: Boolean,
    ): GridPlacement {
        val xs = if (rightToLeft) (columns - item.width downTo 0) else (0..columns - item.width)
        val start = max(0, rows - item.height)
        // Bottom-up fills the screen's rows from the last one up, then carries on below them.
        val ys = if (fromBottom) (start downTo 0).asSequence() + generateSequence(start + 1) { it + 1 } else generateSequence(0) { it + 1 }
        for (y in ys) {
            for (x in xs) {
                val candidate = item.copy(x = x, y = y)
                if (occupied.none(candidate::overlaps)) return candidate
            }
        }
        error("unreachable: rows below the layout are always free")
    }
}
