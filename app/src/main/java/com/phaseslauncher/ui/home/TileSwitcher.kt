package com.phaseslauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.theme.contentColorOn
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A grid of [count] tiles, in window pixels. Items fill rows from the bar's side ([mirrored] when
 * the bar is on the right), so the first phases sit nearest the thumb.
 */
internal data class TileLayout(
    val left: Float,
    val top: Float,
    val tileWidth: Float,
    val tileHeight: Float,
    val gap: Float,
    val columns: Int,
    val count: Int,
    val mirrored: Boolean,
) {
    val rows: Int get() = (count + columns - 1) / columns
    val width: Float get() = columns * tileWidth + (columns - 1) * gap
    val height: Float get() = rows * tileHeight + (rows - 1) * gap

    private fun columnOf(index: Int): Int = (index % columns).let { if (mirrored) columns - 1 - it else it }

    fun centerOf(index: Int): Offset = Offset(
        left + columnOf(index) * (tileWidth + gap) + tileWidth / 2f,
        top + (index / columns) * (tileHeight + gap) + tileHeight / 2f,
    )

    /** The tile under [p], counting the gaps and [slack] around the grid; null off the grid or on an empty cell. */
    fun indexAt(p: Offset, slack: Float): Int? {
        if (p.x < left - slack || p.x > left + width + slack || p.y < top - slack || p.y > top + height + slack) return null
        val col = floor((p.x - left + gap / 2f) / (tileWidth + gap)).toInt().coerceIn(0, columns - 1)
        val row = floor((p.y - top + gap / 2f) / (tileHeight + gap)).toInt().coerceIn(0, rows - 1)
        val index = row * columns + if (mirrored) columns - 1 - col else col
        return index.takeIf { it < count }
    }
}

/**
 * Two columns for a few items, more as they stop fitting between [top] and [bottom]; tiles are at
 * most [maxTileHeight] tall and the grid is centered on [anchorY] where there's room.
 */
internal fun tileLayout(
    count: Int,
    anchorY: Float,
    width: Float,
    top: Float,
    bottom: Float,
    margin: Float,
    gap: Float,
    maxTileHeight: Float,
    minTileHeight: Float,
    mirrored: Boolean,
): TileLayout {
    val n = max(count, 1)
    val available = bottom - top
    fun tileWidth(columns: Int) = (width - 2 * margin - (columns - 1) * gap) / columns
    fun tileHeight(columns: Int): Float {
        val rows = (n + columns - 1) / columns
        return minOf(maxTileHeight, tileWidth(columns) * 0.8f, (available - (rows - 1) * gap) / rows)
    }
    var columns = if (n <= 4) 2 else 3
    while (tileHeight(columns) < minTileHeight && columns < 5) columns++
    val tileHeight = max(1f, tileHeight(columns))
    val rows = (n + columns - 1) / columns
    val gridHeight = rows * tileHeight + (rows - 1) * gap
    val gridTop = (anchorY - gridHeight / 2f).coerceIn(top, max(top, bottom - gridHeight))
    return TileLayout(margin, gridTop, tileWidth(columns), tileHeight, gap, columns, n, mirrored)
}

@Composable
internal fun TileSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) = SwitcherFrame(state, scrimAlpha = 0.78f) { progress ->
    val density = LocalDensity.current
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val insets = WindowInsets.systemBars
    val topInset = insets.getTop(density).toFloat()
    val bottomInset = insets.getBottom(density).toFloat()
    val layout = with(density) {
        remember(entries.size, state.anchor, width, height, topInset, bottomInset, side) {
            tileLayout(
                count = entries.size,
                anchorY = state.anchor.y,
                width = width,
                top = topInset + 24.dp.toPx(),
                bottom = height - bottomInset - 24.dp.toPx(),
                margin = 16.dp.toPx(),
                gap = 10.dp.toPx(),
                maxTileHeight = 124.dp.toPx(),
                minTileHeight = 72.dp.toPx(),
                mirrored = side == SwitcherSide.Right,
            )
        }
    }
    SideEffect {
        val deadZone = with(density) { 24.dp.toPx() }
        val slack = with(density) { 28.dp.toPx() }
        state.hitTest = { f -> if (state.movedFromStart(f, deadZone)) layout.indexAt(f, slack) else null }
    }
    val highlighted = state.highlighted
    val alpha = progress.coerceIn(0f, 1f)
    val tileWidth = with(density) { layout.tileWidth.toDp() }
    val tileHeight = with(density) { layout.tileHeight.toDp() }
    val roomy = tileHeight >= 96.dp
    val fromX = if (side == SwitcherSide.Right) width else 0f

    entries.forEachIndexed { index, entry ->
        val theme = entry.theme
        val isHighlighted = highlighted == index
        val scale by animateFloatAsState(if (isHighlighted) 1.07f else 1f, label = "tileScale")
        val center = layout.centerOf(index)
        // Each tile flies in from the bar's edge, the far ones a little later.
        val stagger = (progress * (1f + 0.08f * index) - 0.08f * index).coerceIn(0f, 1f)
        val accent = entry.solid
        val filled = isHighlighted || (highlighted == null && index == active)
        val ink = if (filled) contentColorOn(accent) else Color.White
        EntryBox(
            entry = entry,
            state = state,
            onClick = { onSelect(index) },
            themeActions = themeActions,
            modifier = Modifier
                .zIndex(if (isHighlighted) 1f else 0f)
                .placeAt(alignX = 0.5f, x = { fromX + (center.x - fromX) * stagger }, y = { center.y })
                .graphicsLayer {
                    this.alpha = alpha * stagger
                    scaleX = scale * (0.7f + 0.3f * stagger)
                    scaleY = scale * (0.7f + 0.3f * stagger)
                }
                .width(tileWidth)
                .height(tileHeight)
                .clip(RoundedCornerShape(22.dp))
                .background(if (filled) accent else lerp(accent, Color(0xFF0B0D13), 0.62f))
                .then(
                    if (index == active && !filled) Modifier.border(2.dp, accent, RoundedCornerShape(22.dp)) else Modifier,
                ),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = if (roomy) Arrangement.SpaceBetween else Arrangement.Center,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PhaseDot(entry, size = if (roomy) 38.dp else 30.dp, onAccent = filled)
                    Spacer(Modifier.weight(1f))
                    if (index == active && theme != null && roomy) NowChip(ink)
                }
                if (!roomy) Spacer(Modifier.height(6.dp))
                Text(
                    entry.label(),
                    color = ink,
                    style = (if (roomy) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
                        .copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // What releasing picks, above (or below) the grid.
    val focus = highlighted?.let(entries::get)
    if (focus?.theme != null) {
        val above = layout.top - with(density) { 28.dp.toPx() }
        val labelY = if (above > topInset + with(density) { 12.dp.toPx() }) above else layout.top + layout.height + with(density) { 28.dp.toPx() }
        Text(
            phaseMeta(focus.theme),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .placeAt(alignX = 0.5f, x = { width / 2f }, y = { labelY })
                .graphicsLayer { this.alpha = alpha }
                .width(with(density) { min(width - 32.dp.toPx(), 360.dp.toPx()).toDp() })
                .padding(horizontal = 8.dp),
        )
    }
}
