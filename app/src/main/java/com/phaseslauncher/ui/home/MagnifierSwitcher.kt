package com.phaseslauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.theme.contentColorOn
import kotlin.math.exp
import kotlin.math.min

/**
 * How much an item swells for a focus point [rows] rows away: 1 under it, fading smoothly to 0
 * about three rows out, like a dock under the pointer.
 */
internal fun magnification(rows: Float): Float = exp(-rows * rows / 2f)

@Composable
internal fun MagnifierSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) = SwitcherFrame(state, scrimAlpha = 0.55f) { progress ->
    val density = LocalDensity.current
    val right = side == SwitcherSide.Right
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val layout = with(density) {
        remember(entries.size, state.anchor, height) {
            railLayout(
                count = entries.size,
                extras = ArcExtra.entries.size,
                anchorY = state.anchor.y,
                screenHeight = height,
                row = 54.dp.toPx(),
                minRow = 38.dp.toPx(),
                divider = 14.dp.toPx(),
                padding = 12.dp.toPx(),
                margin = 32.dp.toPx(),
            )
        }
    }
    SideEffect {
        val deadZone = with(density) { 24.dp.toPx() }
        state.hitTest = { f -> if (state.movedFromStart(f, deadZone)) layout.indexAt(f.y) else null }
    }
    val highlighted = state.highlighted
    val alpha = progress.coerceIn(0f, 1f)
    fun rowCenter(index: Int) = layout.rowTops[index] + layout.rowHeight / 2f

    // Swells under the finger while dragging; at rest (or in tap mode) on the active phase.
    val restY by animateFloatAsState(rowCenter(highlighted ?: active), label = "magnifierRest")
    val finger = state.finger
    val focusY = if (state.isDragging && finger != null && highlighted != null) finger.y else restY
    val focusIndex = layout.indexAt(focusY) ?: active

    val baseSize = min(36f, with(density) { layout.rowHeight.toDp().value } - 10f)
    val maxSize = 64f
    val bulge = with(density) { 26.dp.toPx() }
    val edge = with(density) { 14.dp.toPx() }
    val slide = (1f - progress) * with(density) { 96.dp.toPx() }
    fun fromEdge(distance: Float) = if (right) width - distance + slide else distance - slide

    // The dock behind the column.
    val dockWidth = (baseSize + 20f).dp
    Box(
        Modifier
            .placeAt(alignX = if (right) 1f else 0f, x = { fromEdge(edge - with(density) { 10.dp.toPx() }) }, y = { layout.top + layout.height / 2f })
            .graphicsLayer { this.alpha = alpha }
            .width(dockWidth)
            .height(with(density) { layout.height.toDp() })
            .clip(RoundedCornerShape(percent = 50))
            .background(SwitcherBackground.copy(alpha = 0.9f)),
    )

    entries.forEachIndexed { index, entry ->
        val m = magnification((rowCenter(index) - focusY) / layout.rowHeight)
        val size = baseSize + (maxSize - baseSize) * m
        val sizePx = with(density) { size.dp.toPx() }
        val centerFromEdge = edge + with(density) { baseSize.dp.toPx() } / 2f + bulge * m
        val isFocus = index == focusIndex
        EntryBox(
            entry = entry,
            state = state,
            onClick = { onSelect(index) },
            themeActions = themeActions,
            describe = true,
            modifier = Modifier
                .zIndex(m)
                .placeAt(alignX = 0.5f, x = { fromEdge(centerFromEdge) }, y = { rowCenter(index) })
                .graphicsLayer { this.alpha = alpha },
        ) {
            PhaseDot(
                entry,
                size = size.dp,
                ring = when {
                    isFocus && highlighted != null -> Color.White
                    index == active -> Color.White.copy(alpha = 0.55f)
                    else -> null
                },
            )
        }

        // The focused item's name in a pill beside it; in tap mode every item is named.
        val labelGap = with(density) { 12.dp.toPx() }
        val labelX = centerFromEdge + sizePx / 2f + labelGap
        if (isFocus) {
            val accent = entry.solid
            Column(
                Modifier
                    .zIndex(2f)
                    .placeAt(alignX = if (right) 1f else 0f, x = { fromEdge(labelX) }, y = { rowCenter(index) })
                    .graphicsLayer { this.alpha = alpha }
                    .widthIn(max = with(density) { (width * 0.6f).toDp() })
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = if (right) Alignment.End else Alignment.Start,
            ) {
                val ink = contentColorOn(accent)
                Text(
                    entry.label(),
                    color = ink,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.theme != null) {
                    Text(phaseMeta(entry.theme), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else if (!state.isDragging) {
            Text(
                entry.label(),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .placeAt(alignX = if (right) 1f else 0f, x = { fromEdge(labelX) }, y = { rowCenter(index) })
                    .graphicsLayer { this.alpha = alpha }
                    .widthIn(max = with(density) { (width * 0.6f).toDp() }),
            )
        }
    }
}
