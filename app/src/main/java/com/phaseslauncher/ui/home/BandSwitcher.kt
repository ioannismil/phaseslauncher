package com.phaseslauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phaseslauncher.R
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.theme.contentColorOn
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The bands' area and sizes, in window pixels: the expanded band is [expanded] tall, the rest [collapsed]. */
internal data class BandLayout(val top: Float, val bottom: Float, val gap: Float, val expanded: Float, val collapsed: Float, val count: Int) {
    private val slot get() = (bottom - top) / count

    /** Top of band [index] while band [open] is expanded. */
    fun topOf(index: Int, open: Int): Float = top + index * (collapsed + gap) + if (index > open) expanded - collapsed else 0f

    /**
     * The band under [y]. The expanded band [current] keeps the finger while it's over it, so the
     * bands growing and shrinking under the finger don't flip the choice back and forth.
     */
    fun indexAt(y: Float, current: Int?): Int {
        if (current != null) {
            val t = topOf(current, current)
            if (y >= t && y <= t + expanded) return current
        }
        return floor((y - top) / slot).toInt().coerceIn(0, count - 1)
    }
}

internal fun bandLayout(count: Int, top: Float, bottom: Float, gap: Float, maxExpanded: Float): BandLayout {
    val total = bottom - top
    if (count <= 1) return BandLayout(top, bottom, gap, total, total, max(count, 1))
    val expanded = min(maxExpanded, total * 0.42f)
    val collapsed = (total - expanded - gap * (count - 1)) / (count - 1)
    return BandLayout(top, bottom, gap, expanded, collapsed, count)
}

@Composable
internal fun BandSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) = SwitcherFrame(state, scrimAlpha = 0.92f) { progress ->
    val density = LocalDensity.current
    val right = side == SwitcherSide.Right
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val insets = WindowInsets.systemBars
    val topInset = insets.getTop(density).toFloat()
    val bottomInset = insets.getBottom(density).toFloat()
    val layout = with(density) {
        remember(entries.size, height, topInset, bottomInset) {
            bandLayout(
                count = entries.size,
                top = topInset + 16.dp.toPx(),
                bottom = height - bottomInset - 16.dp.toPx(),
                gap = 6.dp.toPx(),
                maxExpanded = 232.dp.toPx(),
            )
        }
    }
    // The last band picked, so indexAt can hold on to it.
    val last = remember { intArrayOf(-1) }
    SideEffect {
        val deadZone = with(density) { 24.dp.toPx() }
        state.hitTest = { f ->
            if (state.movedFromStart(f, deadZone)) {
                layout.indexAt(f.y, last[0].takeIf { it >= 0 }).also { last[0] = it }
            } else {
                null
            }
        }
    }
    val highlighted = state.highlighted
    val open = highlighted ?: active
    val alpha = progress.coerceIn(0f, 1f)
    val dir = if (right) 1f else -1f
    val margin = with(density) { 12.dp.toPx() }
    val bandWidth = with(density) { (width - 2 * margin).toDp() }

    entries.forEachIndexed { index, entry ->
        val theme = entry.theme
        val isOpen = index == open
        val top by animateFloatAsState(layout.topOf(index, open), label = "bandTop")
        val bandHeight by animateFloatAsState(if (isOpen) layout.expanded else layout.collapsed, label = "bandHeight")
        val accent = entry.solid
        val ink = if (isOpen) contentColorOn(accent) else Color.White
        val big = layout.expanded >= with(density) { 160.dp.toPx() }
        EntryBox(
            entry = entry,
            state = state,
            onClick = { onSelect(index) },
            themeActions = themeActions,
            modifier = Modifier
                .placeAt(x = { margin + dir * (1f - progress) * width * 0.3f * (1f + index * 0.15f) }, y = { top + bandHeight / 2f })
                .graphicsLayer { this.alpha = alpha }
                .width(bandWidth)
                .height(with(density) { bandHeight.toDp() })
                .clip(RoundedCornerShape(24.dp))
                .background(if (isOpen) accent else lerp(accent, Color(0xFF0B0D13), 0.7f)),
        ) {
            if (isOpen) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PhaseDot(entry, size = if (big) 56.dp else 36.dp, onAccent = true)
                        Spacer(Modifier.weight(1f))
                        if (state.isDragging) {
                            Text(stringResource(R.string.release_to_switch), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        } else if (index == active && theme != null) {
                            NowChip(ink)
                        }
                    }
                    Column {
                        Text(
                            entry.label(),
                            color = ink,
                            fontSize = if (big) 44.sp else 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (theme != null) {
                            Text(phaseMeta(theme), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(entry.icon, contentDescription = null, tint = lerp(entry.swatch, Color.White, 0.25f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        entry.label(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (theme != null) {
                        if (index == active) {
                            NowChip(Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            pluralStringResource(R.plurals.apps_count, theme.appKeys.size, theme.appKeys.size),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// endregion
