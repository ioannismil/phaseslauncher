package com.phaseslauncher.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.theme.contentColorOn
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Where the rail and its rows sit, in window pixels. Rows keep their place while one is highlighted. */
internal data class RailLayout(val top: Float, val height: Float, val rowTops: List<Float>, val rowHeight: Float) {
    fun indexAt(y: Float): Int? = rowTops.indices.minByOrNull { abs(rowTops[it] + rowHeight / 2f - y) }
}

/** [count] rows ([extras] of them last, after a divider), centered on [anchorY] and kept on screen. */
internal fun railLayout(
    count: Int,
    extras: Int,
    anchorY: Float,
    screenHeight: Float,
    row: Float,
    minRow: Float,
    divider: Float,
    padding: Float,
    margin: Float,
): RailLayout {
    val available = screenHeight - 2 * margin
    val fixed = divider + 2 * padding
    val rowHeight = if (count * row + fixed <= available) row else max(minRow, (available - fixed) / count)
    val height = count * rowHeight + fixed
    val top = (anchorY - height / 2f).coerceIn(margin, max(margin, screenHeight - margin - height))
    val firstExtra = count - extras
    val tops = List(count) { i -> top + padding + i * rowHeight + if (i >= firstExtra) divider else 0f }
    return RailLayout(top, height, tops, rowHeight)
}

@Composable
internal fun RailSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) = SwitcherFrame(state, scrimAlpha = 0.45f) { progress ->
    val density = LocalDensity.current
    val right = side == SwitcherSide.Right
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val railWidth = 212.dp
    val layout = with(density) {
        remember(entries.size, state.anchor, height) {
            railLayout(
                count = entries.size,
                extras = ArcExtra.entries.size,
                anchorY = state.anchor.y,
                screenHeight = height,
                row = 56.dp.toPx(),
                minRow = 40.dp.toPx(),
                divider = 13.dp.toPx(),
                padding = 14.dp.toPx(),
                margin = 32.dp.toPx(),
            )
        }
    }
    SideEffect {
        val deadZone = with(density) { 24.dp.toPx() }
        state.hitTest = { f -> if (state.movedFromStart(f, deadZone)) layout.indexAt(f.y) else null }
    }
    val highlighted = state.highlighted
    val railPx = with(density) { railWidth.toPx() }
    val slide = (1f - progress) * railPx
    val rowHeight = with(density) { layout.rowHeight.toDp() }
    val alpha = progress.coerceIn(0f, 1f)

    // The rail itself.
    Box(
        Modifier
            .placeAt(x = { if (right) width - railPx + slide else -slide }, y = { layout.top + layout.height / 2f })
            .graphicsLayer { this.alpha = alpha }
            .width(railWidth)
            .height(with(density) { layout.height.toDp() })
            .clip(if (right) RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp) else RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp))
            .background(SwitcherBackground),
    )
    // The divider before "New phase".
    val firstExtra = entries.size - ArcExtra.entries.size
    Box(
        Modifier
            .placeAt(x = { (if (right) width - railPx + slide else -slide) + with(density) { 16.dp.toPx() } }, y = { layout.rowTops[firstExtra] - with(density) { 6.5.dp.toPx() } })
            .graphicsLayer { this.alpha = alpha }
            .width(railWidth - 32.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.1f)),
    )

    entries.forEachIndexed { index, entry ->
        val isHighlighted = highlighted == index
        val pop by animateDpAsState(if (isHighlighted) 40.dp else 0.dp, label = "railPop")
        val popPx = with(density) { pop.toPx() }
        val accent = entry.accent
        val textColor = if (isHighlighted) contentColorOn(accent) else Color.White
        EntryBox(
            entry = entry,
            state = state,
            onClick = { onSelect(index) },
            themeActions = themeActions,
            modifier = Modifier
                .zIndex(if (isHighlighted) 1f else 0f)
                .placeAt(
                    x = { if (right) width - railPx - popPx + slide else -slide },
                    y = { layout.rowTops[index] + layout.rowHeight / 2f },
                )
                .graphicsLayer { this.alpha = alpha }
                .width(railWidth + pop)
                .height(rowHeight)
                .clip(if (right) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
                .background(if (isHighlighted) accent else Color.Transparent),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = if (right) 12.dp else 16.dp + pop, end = if (right) 16.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry.theme != null) {
                    PhaseDot(entry, size = min(rowHeight.value - 12f, if (isHighlighted) 42f else 36f).dp, onAccent = isHighlighted)
                } else {
                    Box(
                        Modifier
                            .size(min(rowHeight.value - 12f, 36f).dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(entry.icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    entry.label(),
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (index == active && !isHighlighted) NowChip(Color(0xFFB9CBFF))
            }
        }
    }
}

// endregion

// region Wheel
