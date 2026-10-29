package com.themelauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ThemeAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

// region Geometry

/** Where the arc's items sit, in window coordinates. [center] is on the screen edge. */
data class ArcGeometry(val center: Offset, val positions: List<Offset>, val radius: Float)

private const val ARC_SPAN_DEGREES = 140f

/**
 * Lays out [count] items top-to-bottom on a half circle centered on the screen edge at the
 * anchor's height, opening toward the middle of the screen. The radius grows with the item
 * count so items keep [spacing] apart; when one ring can't fit them on screen they alternate
 * between an outer and an inner ring.
 */
fun arcGeometry(
    count: Int,
    anchor: Offset,
    side: SwitcherSide,
    width: Float,
    height: Float,
    spacing: Float,
    minRadius: Float,
    ringGap: Float,
    margin: Float,
): ArcGeometry {
    val span = ARC_SPAN_DEGREES / 180f * PI.toFloat()
    val maxRadius = max(minRadius, min(height / 2f - margin, width - margin))
    val needed = if (count <= 1) minRadius else (count - 1) * spacing / span
    val twoRings = needed > maxRadius
    val radius = if (twoRings) maxRadius else needed.coerceIn(minRadius, maxRadius)

    val lowest = radius + margin
    val highest = height - radius - margin
    val centerY = if (lowest <= highest) anchor.y.coerceIn(lowest, highest) else height / 2f
    val center = Offset(if (side == SwitcherSide.Right) width else 0f, centerY)
    val towardMiddle = if (side == SwitcherSide.Right) -1f else 1f

    fun point(r: Float, t: Float): Offset {
        val angle = -span / 2f + span * t // negative = above the center line
        return center + Offset(towardMiddle * r * cos(angle), r * sin(angle))
    }

    val positions = if (!twoRings) {
        List(count) { i -> point(radius, if (count == 1) 0.5f else i / (count - 1f)) }
    } else {
        // Reading order still goes top to bottom, alternating outer/inner.
        val outerCount = ceil(count / 2f).toInt()
        val innerCount = count - outerCount
        val innerRadius = radius - ringGap
        List(count) { i ->
            val inRing = i / 2
            if (i % 2 == 0) {
                point(radius, if (outerCount == 1) 0.5f else inRing / (outerCount - 1f))
            } else {
                // Inner ring is staggered half a step so its items sit between the outer ones.
                point(innerRadius, (inRing + 0.5f) / max(innerCount, 1).toFloat().coerceAtLeast(1f))
            }
        }
    }
    return ArcGeometry(center, positions, radius)
}

/** The item nearest the finger, once the finger has left the dead zone around the edge. */
fun nearestItem(geometry: ArcGeometry, finger: Offset, deadZone: Float): Int? {
    if ((finger - geometry.center).getDistance() < deadZone) return null
    return geometry.positions.indices.minByOrNull { (geometry.positions[it] - finger).getDistance() }
}

// endregion

/** Shared between the edge bar (which owns the gesture) and the overlay (which owns the layout). */
@Stable
class ThemeArcState {
    var isOpen by mutableStateOf(false)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var anchor by mutableStateOf(Offset.Zero)
        private set
    var finger by mutableStateOf<Offset?>(null)
        private set

    internal var barAnchor = Offset.Zero
    internal var geometry: ArcGeometry? = null
    internal var deadZone = 0f

    val highlighted: Int?
        get() {
            val f = finger ?: return null
            val g = geometry ?: return null
            return if (isDragging) nearestItem(g, f, deadZone) else null
        }

    fun openForTap() {
        anchor = barAnchor
        isDragging = false
        finger = null
        isOpen = true
    }

    internal fun startDrag(window: Offset) {
        anchor = barAnchor
        finger = window
        isDragging = true
        isOpen = true
    }

    internal fun dragTo(window: Offset) {
        finger = window
    }

    /** Returns the item under the finger (if any) and closes the arc. */
    internal fun endDrag(): Int? = highlighted.also { close() }

    fun close() {
        isOpen = false
        isDragging = false
        finger = null
    }
}

/**
 * The thin handle on the screen edge. Swipe from it toward the middle to fan out the themes
 * and release on one to switch; tap it to open the arc and pick with a tap instead.
 */
@Composable
fun ThemeEdgeBar(
    state: ThemeArcState,
    side: SwitcherSide,
    accent: Color,
    onRelease: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pressed by animateFloatAsState(if (state.isOpen) 1f else 0f, label = "barPressed")

    Box(
        modifier = modifier
            .width(28.dp)
            .height(168.dp)
            // Keep Android's edge back-gesture from stealing swipes that start on the bar.
            .systemGestureExclusion()
            .onGloballyPositioned { coords ->
                origin = coords.positionInWindow()
                val edgeX = if (side == SwitcherSide.Right) origin.x + coords.size.width else origin.x
                state.barAnchor = Offset(edgeX, origin.y + coords.size.height / 2f)
            }
            .pointerInput(state) { detectTapGestures(onTap = { state.openForTap() }) }
            .pointerInput(state, onRelease) {
                detectDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        state.startDrag(origin + it)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        state.dragTo(origin + change.position)
                    },
                    onDragEnd = { onRelease(state.endDrag()) },
                    onDragCancel = { state.close() },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = "Switch theme"
                onClick { state.openForTap(); true }
            },
        contentAlignment = if (side == SwitcherSide.Right) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .width(5.dp + 2.dp * pressed)
                .height(96.dp)
                .graphicsLayer { alpha = 0.85f + 0.15f * pressed }
                .clip(CircleShape)
                .background(accent),
        )
    }
}

/**
 * The themes fanned out on a half circle around the bar. While dragging, the item nearest the
 * finger is enlarged; in tap mode items are tappable and long-press opens [themeActions].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeArcOverlay(
    state: ThemeArcState,
    themes: List<Theme>,
    activeThemeId: Long?,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.isOpen) {
        progress.animateTo(if (state.isOpen) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 520f))
    }
    if (!state.isOpen && progress.value == 0f) return

    val haptics = LocalHapticFeedback.current
    val highlighted = state.highlighted
    LaunchedEffect(highlighted) {
        if (highlighted != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    val count = themes.size + 1 // the last item is "New theme"

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val geometry = with(density) {
            remember(count, state.anchor, side, constraints.maxWidth, constraints.maxHeight) {
                arcGeometry(
                    count = count,
                    anchor = state.anchor,
                    side = side,
                    width = constraints.maxWidth.toFloat(),
                    height = constraints.maxHeight.toFloat(),
                    spacing = 76.dp.toPx(),
                    minRadius = 150.dp.toPx(),
                    ringGap = 92.dp.toPx(),
                    margin = 56.dp.toPx(),
                )
            }
        }
        SideEffect {
            state.geometry = geometry
            state.deadZone = with(density) { 56.dp.toPx() }
        }

        // Scrim. In tap mode it dismisses; while dragging the bar owns the gesture.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.6f))
                .then(
                    if (state.isOpen && !state.isDragging) {
                        Modifier.pointerInput(Unit) { detectTapGestures { state.close() } }
                    } else {
                        Modifier
                    },
                ),
        )

        val itemWidthPx = with(density) { ArcItemWidth.toPx() }
        val iconHalfPx = with(density) { (ArcIconSize / 2).toPx() }
        repeat(count) { index ->
            val theme = themes.getOrNull(index)
            val target = geometry.positions[index]
            val isHighlighted = highlighted == index
            val scale by animateFloatAsState(if (isHighlighted) 1.3f else 1f, label = "arcScale")
            Box(
                Modifier
                    .offset {
                        val p = lerp(geometry.center, target, progress.value)
                        IntOffset((p.x - itemWidthPx / 2).roundToInt(), (p.y - iconHalfPx).roundToInt())
                    }
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
            ) {
                ArcItem(
                    theme = theme,
                    active = theme != null && theme.id == activeThemeId,
                    highlighted = isHighlighted,
                    scale = scale,
                    interactive = state.isOpen && !state.isDragging,
                    onClick = { onSelect(index) },
                    onLongClick = { if (theme != null) menuIndex = index },
                )
                if (theme != null) {
                    DropdownMenu(
                        expanded = menuIndex == index,
                        onDismissRequest = { menuIndex = null },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        themeActions(theme).forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                leadingIcon = { Icon(action.icon, contentDescription = null) },
                                enabled = action.enabled,
                                onClick = {
                                    menuIndex = null
                                    state.close()
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ArcItemWidth = 96.dp
private val ArcIconSize = 52.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArcItem(
    theme: Theme?,
    active: Boolean,
    highlighted: Boolean,
    scale: Float,
    interactive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = theme?.let { Color(it.accentColor) } ?: Color.White.copy(alpha = 0.18f)
    val label = theme?.name ?: "New theme"
    Column(
        Modifier
            .width(ArcItemWidth)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onClickLabel = if (theme != null) "Switch to $label" else label,
                    )
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(ArcIconSize)
                .clip(CircleShape)
                .background(accent)
                .border(
                    width = if (highlighted || active) 3.dp else 0.dp,
                    color = if (highlighted) Color.White else Color.White.copy(alpha = 0.55f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = theme?.icon?.imageVector ?: Icons.Rounded.Add,
                contentDescription = null,
                tint = if (theme != null) contentColorOn(accent) else Color.White,
            )
        }
        Spacer(Modifier.height(if (highlighted) 12.dp else 6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (highlighted || active) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = if (highlighted) 0.55f else 0.35f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Shows which theme is active, under the clock. Tapping it opens the arc. */
@Composable
fun ActiveThemeLabel(theme: Theme?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (theme == null) return
    val accent = Color(theme.accentColor)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.9f))
            .clickable(onClickLabel = "Switch theme", onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(theme.icon.imageVector, contentDescription = null, tint = contentColorOn(accent), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            theme.name,
            color = contentColorOn(accent),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
