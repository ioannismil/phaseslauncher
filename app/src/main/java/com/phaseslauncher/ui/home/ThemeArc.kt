package com.phaseslauncher.ui.home

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.theme.contentColorOn
import com.phaseslauncher.ui.theme.imageVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class ThemeAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

// region Geometry

/**
 * Where the arc's items sit, in window coordinates. [center] is the point on the screen edge the
 * arc opens from; the items lie on a circle of [radius] around [arcCenter], which is on or beyond
 * that edge. [itemScale] is below 1 when items were shrunk to fit.
 */
data class ArcGeometry(
    val center: Offset,
    val positions: List<Offset>,
    val arcCenter: Offset,
    val radius: Float,
    val itemScale: Float = 1f,
)

private const val ARC_MAX_HALF_SPAN_DEGREES = 70
private const val ARC_MIN_HALF_SPAN_DEGREES = 8

/**
 * Lays out [count] items top-to-bottom on a circular arc around the screen edge at the anchor's
 * height, opening toward the middle of the screen, equally spaced along the arc.
 *
 * Each item's name sits beside its icon, toward the middle, so consecutive items must be at
 * least [pitch] apart in height (also near the ends of the arc, where the arc runs most
 * sideways). The widest arc (up to 140°) that allows this is used, with room for [labelRoom] on
 * the screen. Sparse arcs keep items at most [maxSpacing] apart (but at least [minRadius] from
 * the edge's center point); crowded arcs grow into a larger circle whose center lies beyond the
 * edge, and shrink their items (down to [minScale]) only when that isn't enough.
 */
fun arcGeometry(
    count: Int,
    anchor: Offset,
    side: SwitcherSide,
    width: Float,
    height: Float,
    pitch: Float,
    minRadius: Float,
    maxSpacing: Float,
    labelRoom: Float,
    edgeInset: Float,
    margin: Float,
    minScale: Float = 0.7f,
): ArcGeometry {
    val towardMiddle = if (side == SwitcherSide.Right) -1f else 1f
    val edgeX = if (side == SwitcherSide.Right) width else 0f
    val maxHalfExtent = max(1f, height / 2f - margin)
    // How far the arc may bulge from its ends toward the middle, leaving room for the names.
    val maxBulge = max(1f, width - margin - labelRoom - edgeInset)

    fun centerAt(halfExtent: Float): Offset {
        val lowest = halfExtent + margin
        val highest = height - halfExtent - margin
        return Offset(edgeX, if (lowest <= highest) anchor.y.coerceIn(lowest, highest) else height / 2f)
    }

    if (count <= 1) {
        val center = centerAt(0f)
        val radius = min(minRadius, edgeInset + maxBulge)
        return ArcGeometry(center, List(count) { center + Offset(towardMiddle * radius, 0f) }, center, radius)
    }

    /** Half the arc's height, and the height step between its last two items, for a half span. */
    data class Fit(val halfSpan: Float, val halfExtent: Float, val endStep: Float)

    fun fit(halfSpanDegrees: Int): Fit {
        val a = halfSpanDegrees / 180f * PI.toFloat()
        val step = 2f * a / (count - 1)
        val sparseRadius = max(maxSpacing / step, minRadius)
        val halfExtent = minOf(maxHalfExtent, maxBulge / tan(a / 2f), sparseRadius * sin(a))
        val endStep = halfExtent * (sin(a) - sin(a - step)) / sin(a)
        return Fit(a, halfExtent, endStep)
    }

    val spans = (ARC_MAX_HALF_SPAN_DEGREES downTo ARC_MIN_HALF_SPAN_DEGREES).map(::fit)
    val chosen = spans.firstOrNull { it.endStep >= pitch }
        ?: spans.firstOrNull { it.endStep >= pitch * minScale }
        ?: spans.maxBy { it.endStep }
    val itemScale = (chosen.endStep / pitch).coerceIn(minScale, 1f)

    val a = chosen.halfSpan
    val radius = chosen.halfExtent / sin(a)
    val center = centerAt(chosen.halfExtent)
    // The arc's ends sit edgeInset from the edge, so its circle's center is that much short of
    // (or beyond) the edge.
    val arcCenter = Offset(edgeX + towardMiddle * (edgeInset - radius * cos(a)), center.y)
    val step = 2f * a / (count - 1)
    val positions = List(count) { i ->
        val angle = -a + i * step // negative = above the center line
        arcCenter + Offset(towardMiddle * radius * cos(angle), radius * sin(angle))
    }
    return ArcGeometry(center, positions, arcCenter, radius, itemScale)
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
    /** Theme names in arc order; each becomes a "Switch to …" screen-reader action. */
    themeNames: List<String> = emptyList(),
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
                contentDescription = "Switch phase"
                onClick { state.openForTap(); true }
                customActions = themeNames.mapIndexed { index, name ->
                    CustomAccessibilityAction("Switch to $name") { onRelease(index); true }
                }
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
    val count = themes.size + ArcExtra.entries.size // themes, then "New theme" and "Settings"

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
                    pitch = ArcPitch.toPx(),
                    minRadius = 170.dp.toPx(),
                    maxSpacing = 88.dp.toPx(),
                    labelRoom = (ArcIconSize / 2 + ArcLabelGap + ArcLabelMaxWidth).toPx(),
                    edgeInset = 56.dp.toPx(),
                    margin = 48.dp.toPx(),
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
                        Modifier
                            .pointerInput(Unit) { detectTapGestures { state.close() } }
                            .semantics {
                                contentDescription = "Close phase switcher"
                                onClick { state.close(); true }
                            }
                    } else {
                        Modifier
                    },
                ),
        )

        val iconHalfPx = with(density) { (ArcIconSize / 2).toPx() } * geometry.itemScale
        val rightSide = side == SwitcherSide.Right
        repeat(count) { index ->
            val theme = themes.getOrNull(index)
            val extra = if (theme == null) ArcExtra.entries.getOrNull(index - themes.size) else null
            val target = geometry.positions[index]
            val isHighlighted = highlighted == index
            val scale by animateFloatAsState(if (isHighlighted) 1.3f else 1f, label = "arcScale")
            Box(
                Modifier
                    // The highlighted item draws over its neighbours while it's enlarged.
                    .zIndex(if (isHighlighted) 1f else 0f)
                    // Puts the icon's center on the arc point; the name hangs off toward the middle.
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints())
                        layout(placeable.width, placeable.height) {
                            val p = lerp(geometry.center, target, progress.value)
                            val x = if (rightSide) p.x - (placeable.width - iconHalfPx) else p.x - iconHalfPx
                            placeable.place(x.roundToInt(), (p.y - placeable.height / 2f).roundToInt())
                        }
                    }
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
            ) {
                ArcItem(
                    theme = theme,
                    extra = extra,
                    active = theme != null && theme.id == activeThemeId,
                    highlighted = isHighlighted,
                    labelFirst = rightSide,
                    itemScale = geometry.itemScale,
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

/** Non-theme items at the end of the arc, in order. Their arc index is `themes.size + ordinal`. */
enum class ArcExtra(val label: String, val icon: ImageVector) {
    NewTheme("New phase", Icons.Rounded.Add),
    Settings("Settings", Icons.Outlined.Settings),
}

private val ArcIconSize = 52.dp
private val ArcLabelGap = 10.dp
private val ArcLabelMaxWidth = 128.dp

/** Least height between consecutive items, so a name never touches the next icon. */
private val ArcPitch = 60.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArcItem(
    theme: Theme?,
    extra: ArcExtra?,
    active: Boolean,
    highlighted: Boolean,
    /** Name on the left of the icon (the arc is on the right edge), else on the right. */
    labelFirst: Boolean,
    /** Below 1 when the arc is crowded. */
    itemScale: Float,
    scale: Float,
    interactive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = theme?.let { Color(it.accentColor) } ?: Color.White.copy(alpha = 0.18f)
    val label = theme?.name ?: extra?.label.orEmpty()
    val baseStyle = MaterialTheme.typography.labelLarge
    val name: @Composable () -> Unit = {
        Text(
            text = label,
            color = Color.White,
            style = baseStyle.copy(
                fontWeight = if (highlighted || active) FontWeight.Bold else FontWeight.Medium,
                fontSize = baseStyle.fontSize * max(itemScale, 0.85f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = ArcLabelMaxWidth)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = if (highlighted) 0.6f else 0.35f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    Row(
        Modifier
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (labelFirst) {
            name()
            Spacer(Modifier.width(ArcLabelGap))
        }
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(ArcIconSize * itemScale)
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
                imageVector = theme?.icon?.imageVector ?: extra?.icon ?: Icons.Rounded.Add,
                contentDescription = null,
                tint = if (theme != null) contentColorOn(accent) else Color.White,
            )
        }
        if (!labelFirst) {
            Spacer(Modifier.width(ArcLabelGap))
            name()
        }
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
            .clickable(onClickLabel = stringResource(R.string.switch_theme), onClick = onClick)
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
