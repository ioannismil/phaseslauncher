package com.phaseslauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.label
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

private val WheelOffsets = floatArrayOf(0f, 84f, 144f, 192f, 232f, 268f, 300f) // dp from the center, by distance
private val WheelFonts = floatArrayOf(40f, 28f, 22f, 19f, 17f, 16f)
private val WheelAlphas = floatArrayOf(1f, 0.75f, 0.55f, 0.42f, 0.34f, 0.28f)

@Composable
internal fun WheelSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) {
    val scroller = rememberScroller(state, entries.size, active, slot = 56.dp)
    SwitcherFrame(state, scrimAlpha = 0.86f, onScrimDrag = scroller::scrollBy, onScrimDragEnd = scroller::settle) { progress ->
        val density = LocalDensity.current
        val right = side == SwitcherSide.Right
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        SideEffect { state.hitTest = scroller::hit }
        val center = scroller.shownCenter(state)
        val focus = center.roundToInt().coerceIn(0, entries.lastIndex)
        val centerY = state.anchor.y.coerceIn(height * 0.35f, height * 0.6f)
        val alpha = progress.coerceIn(0f, 1f)
        val inset = with(density) { 64.dp.toPx() }
        val focusAccent = entries[focus].swatch

        // The selection band.
        Box(
            Modifier
                .placeAt(x = { with(density) { 16.dp.toPx() } }, y = { centerY })
                .graphicsLayer { this.alpha = alpha }
                .width(with(density) { (width - 32.dp.toPx()).toDp() })
                .height(104.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(focusAccent.copy(alpha = 0.16f))
                .border(1.dp, focusAccent.copy(alpha = 0.45f), RoundedCornerShape(30.dp)),
        )

        entries.forEachIndexed { index, entry ->
            val d = index - center
            val distance = abs(d)
            val y = centerY + sign(d) * with(density) { interp(WheelOffsets, distance, tail = 30f).dp.toPx() }
            val near = (1f - distance * 2f).coerceIn(0f, 1f) // 1 at the center, 0 from half a step away
            val textAlpha = interp(WheelAlphas, distance) * alpha
            if (y > -100f && y < height + 100f) {
                EntryBox(
                    entry = entry,
                    state = state,
                    onClick = { onSelect(index) },
                    themeActions = themeActions,
                    modifier = Modifier
                        .placeAt(alignX = if (right) 1f else 0f, x = { if (right) width - inset else inset }, y = { y })
                        .graphicsLayer { this.alpha = textAlpha },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotSize = (12f + 44f * near).dp
                        val dot: @Composable () -> Unit = {
                            if (near > 0.5f) {
                                PhaseDot(entry, size = dotSize)
                            } else {
                                Box(Modifier.size(dotSize).clip(CircleShape).background(entry.swatch))
                            }
                        }
                        if (!right) {
                            dot()
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(horizontalAlignment = if (right) Alignment.End else Alignment.Start) {
                            Text(
                                entry.label(),
                                color = Color.White,
                                fontSize = interp(WheelFonts, distance).sp,
                                fontWeight = if (near > 0.5f) FontWeight.ExtraBold else FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = with(density) { (width * 0.62f).toDp() }),
                            )
                            if (near > 0.5f && entry.theme != null) {
                                Text(
                                    phaseMeta(entry.theme),
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (right) {
                            Spacer(Modifier.width(14.dp))
                            dot()
                        }
                    }
                }
                // A tick on the edge for each item.
                val tickWidth = if (index == focus) 26.dp else 12.dp
                Box(
                    Modifier
                        .placeAt(alignX = if (right) 1f else 0f, x = { if (right) width - with(density) { 18.dp.toPx() } else with(density) { 18.dp.toPx() } }, y = { y })
                        .graphicsLayer { this.alpha = alpha }
                        .width(tickWidth)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index == focus) entry.swatch else Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

// endregion

// region Dial
