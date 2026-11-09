package com.phaseslauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phaseslauncher.R
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.theme.contentColorOn
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A track across the screen at height [y], from [start] to [end] in window pixels, split into
 * [count] equal cells that run inward from the bar: from the right edge when [fromRight].
 */
internal data class SliderLayout(val start: Float, val end: Float, val y: Float, val count: Int, val fromRight: Boolean) {
    val cell: Float get() = (end - start) / count

    fun centerX(index: Int): Float = if (fromRight) end - (index + 0.5f) * cell else start + (index + 0.5f) * cell

    /** The cell under [p]; null once the finger strays more than [reach] above or below the track. */
    fun indexAt(p: Offset, reach: Float): Int? {
        if (abs(p.y - y) > reach) return null
        val k = floor((p.x - start) / cell).toInt().coerceIn(0, count - 1)
        return if (fromRight) count - 1 - k else k
    }
}

/** The track at [anchorY], kept [above] from the top and [below] from the bottom so its bubble fits. */
internal fun sliderLayout(count: Int, anchorY: Float, width: Float, height: Float, margin: Float, above: Float, below: Float, fromRight: Boolean) =
    SliderLayout(
        start = margin,
        end = width - margin,
        y = if (above <= height - below) anchorY.coerceIn(above, height - below) else height / 2f,
        count = max(count, 1),
        fromRight = fromRight,
    )

@Composable
internal fun SliderSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) = SwitcherFrame(state, scrimAlpha = 0.6f) { progress ->
    val density = LocalDensity.current
    val right = side == SwitcherSide.Right
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val layout = with(density) {
        remember(entries.size, state.anchor, width, height, right) {
            sliderLayout(
                count = entries.size,
                anchorY = state.anchor.y,
                width = width,
                height = height,
                margin = 10.dp.toPx(),
                above = 170.dp.toPx(),
                below = 80.dp.toPx(),
                fromRight = right,
            )
        }
    }
    SideEffect {
        val deadZone = with(density) { 24.dp.toPx() }
        val reach = with(density) { 150.dp.toPx() }
        state.hitTest = { f -> if (state.movedFromStart(f, deadZone)) layout.indexAt(f, reach) else null }
    }
    val highlighted = state.highlighted
    val alpha = progress.coerceIn(0f, 1f)
    val cellDp = with(density) { layout.cell.toDp() }
    val dotSize = min(44f, cellDp.value - 6f).coerceAtLeast(18f)
    val trackHeight = (dotSize + 20f).dp

    // The track grows out of the bar's edge.
    val trackWidth = with(density) { ((layout.end - layout.start) * progress).toDp() }
    Box(
        Modifier
            .placeAt(alignX = if (right) 1f else 0f, x = { if (right) layout.end else layout.start }, y = { layout.y })
            .graphicsLayer { this.alpha = alpha }
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(SwitcherBackground),
    )

    // The selection: a pill sliding along under the chosen cell.
    val focus = highlighted ?: active
    val focusX by animateFloatAsState(layout.centerX(focus), label = "sliderFocus")
    val focusEntry = entries[focus]
    Box(
        Modifier
            .placeAt(alignX = 0.5f, x = { focusX }, y = { layout.y })
            .graphicsLayer { this.alpha = alpha * if (highlighted != null) 1f else 0.5f }
            .width(min(cellDp.value, dotSize + 16f).dp)
            .height(trackHeight - 8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(focusEntry.swatch.copy(alpha = 0.35f)),
    )

    entries.forEachIndexed { index, entry ->
        val isHighlighted = highlighted == index
        val lift by animateFloatAsState(if (isHighlighted) 1f else 0f, label = "sliderLift")
        val x = layout.centerX(index)
        val start = if (right) layout.end else layout.start
        EntryBox(
            entry = entry,
            state = state,
            onClick = { onSelect(index) },
            themeActions = themeActions,
            describe = true,
            modifier = Modifier
                .zIndex(lift)
                .placeAt(alignX = 0.5f, x = { start + (x - start) * progress }, y = { layout.y - lift * with(density) { 10.dp.toPx() } })
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = 1f + 0.3f * lift
                    scaleY = 1f + 0.3f * lift
                },
        ) {
            PhaseDot(entry, size = dotSize.dp, ring = if (index == active) Color.White.copy(alpha = 0.6f) else null)
        }
    }

    // The bubble over the track names what releasing (or a tap on it) picks.
    val margin = with(density) { 12.dp.toPx() }
    val bubbleY = layout.y - with(density) { (trackHeight / 2 + 58.dp).toPx() }
    val accent = focusEntry.solid
    val ink = contentColorOn(accent)
    Column(
        Modifier
            .zIndex(3f)
            .layout { measurable, _ ->
                // Centered over the focus, but kept on screen.
                val placeable = measurable.measure(Constraints())
                layout(placeable.width, placeable.height) {
                    val x = (focusX - placeable.width / 2f).coerceIn(margin, max(margin, width - margin - placeable.width))
                    placeable.place(x.roundToInt(), (bubbleY - placeable.height / 2f).roundToInt())
                }
            }
            .graphicsLayer { this.alpha = alpha }
            .widthIn(max = with(density) { (width - 2 * margin).toDp() })
            .clip(RoundedCornerShape(22.dp))
            .background(accent)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            focusEntry.label(),
            color = ink,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val theme = focusEntry.theme
        when {
            state.isDragging && highlighted != null -> {
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.release_to_switch), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            }

            theme != null -> Text(phaseMeta(theme), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
