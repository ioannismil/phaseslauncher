package com.phaseslauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phaseslauncher.R
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.label
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The dial's circle and its items, in window pixels. */
internal data class DialGeometry(val center: Offset, val radius: Float, val positions: List<Offset>, val itemScale: Float)

/**
 * [count] items on a full circle, first at the top and going clockwise, beside the [side] edge at
 * the anchor's height. The circle grows for many items (each needs [pitch] of its rim) and shrinks
 * them (to [minScale]) once it can't grow; [outerPad] is the room outside it for the colored ring.
 */
internal fun dialGeometry(
    count: Int,
    anchorY: Float,
    side: SwitcherSide,
    width: Float,
    height: Float,
    pitch: Float,
    minRadius: Float,
    outerPad: Float,
    margin: Float,
    minScale: Float = 0.6f,
): DialGeometry {
    val maxRadius = max(1f, min((width - 2 * margin) / 2f, (height - 2 * margin) / 2f) - outerPad)
    val wanted = max(minRadius, count * pitch / (2 * PI.toFloat()))
    val radius = min(wanted, maxRadius)
    val scale = (2 * PI.toFloat() * radius / count / pitch).coerceIn(minScale, 1f)
    val outer = radius + outerPad
    val cx = (if (side == SwitcherSide.Right) width - margin - outer else margin + outer)
        .let { if (outer + margin <= width - outer - margin) it.coerceIn(outer + margin, width - outer - margin) else width / 2f }
    val cy = if (outer + margin <= height - outer - margin) anchorY.coerceIn(outer + margin, height - outer - margin) else height / 2f
    val center = Offset(cx, cy)
    val positions = List(count) { i ->
        val angle = (-90f + i * 360f / count) / 180f * PI.toFloat()
        center + Offset(radius * cos(angle), radius * sin(angle))
    }
    return DialGeometry(center, radius, positions, scale)
}

@Composable
internal fun DialSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) = SwitcherFrame(state, scrimAlpha = 0.7f) { progress ->
    val density = LocalDensity.current
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val ringPad = 42.dp
    val geometry = with(density) {
        remember(entries.size, state.anchor, side, width, height) {
            dialGeometry(
                count = entries.size,
                anchorY = state.anchor.y,
                side = side,
                width = width,
                height = height,
                pitch = 62.dp.toPx(),
                minRadius = 118.dp.toPx(),
                outerPad = ringPad.toPx(),
                margin = 12.dp.toPx(),
            )
        }
    }
    SideEffect {
        val deadZone = with(density) { 24.dp.toPx() }
        state.hitTest = { f ->
            if (state.movedFromStart(f, deadZone)) geometry.positions.indices.minByOrNull { (geometry.positions[it] - f).getDistance() } else null
        }
    }
    val highlighted = state.highlighted
    val focus = entries[highlighted ?: active]
    val alpha = progress.coerceIn(0f, 1f)
    val itemSize = 50.dp * geometry.itemScale
    val sweep = 360f / entries.size
    val span = min(15f, sweep / 2f - 4f)

    Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        val r = geometry.radius * progress
        drawCircle(Color.White.copy(alpha = 0.12f), radius = r, center = geometry.center, style = Stroke(2.dp.toPx()))
        val ring = r + ringPad.toPx() - 4.dp.toPx()
        entries.forEachIndexed { index, entry ->
            val on = index == highlighted
            val color = if (entry.theme != null) entry.accent else Color.White.copy(alpha = 0.35f)
            val angle = -90f + index * sweep
            val s = if (on) span + 5f else span
            drawArc(
                color = color.copy(alpha = if (on) 1f else 0.45f),
                startAngle = angle - s,
                sweepAngle = 2 * s,
                useCenter = false,
                topLeft = geometry.center - Offset(ring, ring),
                size = Size(2 * ring, 2 * ring),
                style = Stroke(if (on) 8.dp.toPx() else 4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }

    // The middle: what releasing picks.
    val discSize = with(density) { (2 * (geometry.radius - itemSize.toPx() / 2f) - 24.dp.toPx()).toDp() }.coerceIn(80.dp, 150.dp)
    Box(
        Modifier
            .placeAt(alignX = 0.5f, x = { geometry.center.x }, y = { geometry.center.y })
            .graphicsLayer {
                this.alpha = alpha
                scaleX = progress
                scaleY = progress
            }
            .size(discSize)
            .clip(CircleShape)
            .background(Color(0xFF161A24))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                focus.label(),
                color = if (focus.theme != null) lerp(focus.accent, Color.White, 0.35f) else Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (focus.theme != null) {
                Text(phaseMeta(focus.theme), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            if (state.isDragging && highlighted != null) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.release_to_switch), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    entries.forEachIndexed { index, entry ->
        val isHighlighted = highlighted == index
        val scale by animateFloatAsState(if (isHighlighted) 1.3f else 1f, label = "dialScale")
        EntryBox(
            entry = entry,
            state = state,
            onClick = { onSelect(index) },
            themeActions = themeActions,
            describe = true,
            modifier = Modifier
                .zIndex(if (isHighlighted) 1f else 0f)
                .placeAt(alignX = 0.5f, x = { lerp(geometry.center, geometry.positions[index], progress).x }, y = { lerp(geometry.center, geometry.positions[index], progress).y })
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            PhaseDot(
                entry,
                size = itemSize,
                ring = when {
                    isHighlighted -> Color.White
                    index == active -> Color.White.copy(alpha = 0.55f)
                    else -> null
                },
            )
        }
    }
}

// endregion

// region Cards
