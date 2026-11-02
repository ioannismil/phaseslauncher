package com.phaseslauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.settings.SwitcherStyle
import com.phaseslauncher.data.themes.SoundProfile
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.AppIcon
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.theme.contentColorOn
import com.phaseslauncher.ui.theme.imageVector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * The phase switcher in the chosen [style]. Items are the themes in order, then the [ArcExtra]
 * items; [onSelect] gets that index. Every style is opened by [ThemeEdgeBar]: dragging highlights
 * the item [ThemeArcState.hitTest] finds under the finger, and a tap opens it for tapping.
 */
@Composable
fun PhaseSwitcherOverlay(
    style: SwitcherStyle,
    state: ThemeArcState,
    themes: List<Theme>,
    apps: Map<String, AppInfo>,
    activeThemeId: Long?,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) {
    val entries = remember(themes) { themes.map { SwitcherEntry(it, null) } + ArcExtra.entries.map { SwitcherEntry(null, it) } }
    val active = themes.indexOfFirst { it.id == activeThemeId }.coerceAtLeast(0)
    if (themes.isEmpty()) {
        ThemeArcOverlay(state, themes, activeThemeId, side, onSelect, themeActions)
        return
    }
    when (style) {
        SwitcherStyle.Arc -> ThemeArcOverlay(state, themes, activeThemeId, side, onSelect, themeActions)
        SwitcherStyle.Rail -> RailSwitcher(state, entries, active, side, onSelect, themeActions)
        SwitcherStyle.Wheel -> WheelSwitcher(state, entries, active, side, onSelect, themeActions)
        SwitcherStyle.Dial -> DialSwitcher(state, entries, active, side, onSelect, themeActions)
        SwitcherStyle.Cards -> CardSwitcher(state, entries, apps, active, side, onSelect, themeActions)
        SwitcherStyle.Bands -> BandSwitcher(state, entries, active, side, onSelect, themeActions)
    }
}

// region Shared pieces

/** A phase, or one of the [ArcExtra] items after them. */
private data class SwitcherEntry(val theme: Theme?, val extra: ArcExtra?) {
    val accent: Color get() = theme?.let { Color(it.accentColor) } ?: Color.White.copy(alpha = 0.18f)
    val icon: ImageVector get() = theme?.icon?.imageVector ?: extra?.icon ?: Icons.Rounded.Add
    val iconTint: Color get() = if (theme != null) contentColorOn(accent) else Color.White

    /** An opaque color to mark it with: the phase's accent, or a light grey for the extras. */
    val swatch: Color get() = if (theme != null) accent else Color(0xFFB8BECC)

    /** An opaque color to fill it with: the phase's accent, or a slate for the extras. */
    val solid: Color get() = if (theme != null) accent else Color(0xFF3A4150)
}

@Composable
private fun SwitcherEntry.label(): String = theme?.name ?: extra?.label.orEmpty()

/** "Silent · 6 apps · 9 blocked". */
@Composable
private fun phaseMeta(theme: Theme): String = buildList {
    if (theme.soundProfile != SoundProfile.Unchanged) add(theme.soundProfile.label)
    add(pluralStringResource(R.plurals.apps_count, theme.appKeys.size, theme.appKeys.size))
    val blocked = theme.focus.blockedApps.size
    if (blocked > 0) add(pluralStringResource(R.plurals.blocked_count, blocked, blocked))
}.joinToString(" · ")

private val SwitcherBackground = Color(0xFF1A1F2B)

/**
 * The scrim and the open/close animation every style shares. In tap mode the scrim closes the
 * switcher on a tap and, if [onScrimDrag] is given, scrolls it on a vertical drag.
 */
@Composable
private fun SwitcherFrame(
    state: ThemeArcState,
    scrimAlpha: Float,
    onScrimDrag: ((Float) -> Unit)? = null,
    onScrimDragEnd: () -> Unit = {},
    content: @Composable BoxWithConstraintsScope.(progress: Float) -> Unit,
) {
    val res = LocalResources.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.isOpen) {
        progress.animateTo(if (state.isOpen) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 520f))
    }
    if (!state.isOpen && progress.value == 0f) return

    val haptics = LocalHapticFeedback.current
    val highlighted = state.highlighted
    LaunchedEffect(highlighted) {
        if (highlighted != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val drag by rememberUpdatedState(onScrimDrag)
    val dragEnd by rememberUpdatedState(onScrimDragEnd)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val p = progress.value
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = scrimAlpha))
                .then(
                    if (state.isOpen && !state.isDragging) {
                        Modifier
                            .pointerInput(Unit) { detectTapGestures { state.close() } }
                            .pointerInput(onScrimDrag != null) {
                                if (onScrimDrag != null) {
                                    detectVerticalDragGestures(onDragEnd = { dragEnd() }, onDragCancel = { dragEnd() }) { change, delta ->
                                        change.consume()
                                        drag?.invoke(delta)
                                    }
                                }
                            }
                            .semantics {
                                contentDescription = res.getString(R.string.close_phase_switcher)
                                onClick { state.close(); true }
                            }
                    } else {
                        Modifier
                    },
                ),
        )
        content(p)
    }
}

/** Places the content with its point ([alignX] of its width, middle of its height) at ([x], [y]) in the parent. */
private fun Modifier.placeAt(alignX: Float = 0f, x: () -> Float, y: () -> Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.width, placeable.height) {
        placeable.place((x() - alignX * placeable.width).roundToInt(), (y() - placeable.height / 2f).roundToInt())
    }
}

/** Tappable in tap mode (long-press: the phase's actions), with the actions menu attached. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryBox(
    entry: SwitcherEntry,
    state: ThemeArcState,
    onClick: () -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
    modifier: Modifier = Modifier,
    /** Name it for screen readers, for content that shows only an icon. */
    describe: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val label = entry.label()
    val clickLabel = if (entry.theme != null) stringResource(R.string.switch_to, label) else label
    Box(
        modifier
            .then(if (describe) Modifier.semantics { contentDescription = label } else Modifier)
            .then(
                if (state.isOpen && !state.isDragging) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = { if (entry.theme != null) menu = true },
                        onClickLabel = clickLabel,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
        val theme = entry.theme
        if (theme != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                themeActions(theme).forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        leadingIcon = { Icon(action.icon, contentDescription = null) },
                        enabled = action.enabled,
                        onClick = {
                            menu = false
                            state.close()
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** The entry's icon on a circle of its color; [onAccent] for a dot drawn on the accent itself. */
@Composable
private fun PhaseDot(entry: SwitcherEntry, size: Dp, ring: Color? = null, onAccent: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (onAccent) Color.White.copy(alpha = 0.35f) else entry.accent)
            .then(if (ring != null) Modifier.border(3.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(entry.icon, contentDescription = null, tint = entry.iconTint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun NowChip(color: Color) {
    Text(
        stringResource(R.string.now),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Linear interpolation through [stops] at fractional index [x]; past the end it keeps going by [tail] per step. */
private fun interp(stops: FloatArray, x: Float, tail: Float = 0f): Float {
    val last = stops.lastIndex
    if (x >= last) return stops[last] + (x - last) * tail
    val i = x.toInt()
    return stops[i] + (stops[i + 1] - stops[i]) * (x - i)
}

/**
 * For styles that scroll through the items (wheel, cards): dragging from the bar moves one item
 * per [slot] pixels from the active one; in tap mode a drag on the scrim scrolls instead.
 */
@Stable
private class PhaseScroller(
    private val state: ThemeArcState,
    private val count: Int,
    private val start: Int,
    private val slot: Float,
    private val deadZone: Float,
) {
    var tapCenter by mutableFloatStateOf(start.toFloat())
        private set

    /** The last position while dragging, so closing doesn't jump back. */
    var lastDrag = Float.NaN

    private fun at(finger: Offset): Float? {
        val origin = state.dragStart ?: return null
        return (start + (finger.y - origin.y) / slot).coerceIn(0f, (count - 1).toFloat())
    }

    fun dragCenter(): Float? = if (state.isDragging) state.finger?.let(::at) else null

    fun hit(finger: Offset): Int? = if (state.movedFromStart(finger, deadZone)) at(finger)?.roundToInt() else null

    fun scrollBy(delta: Float) {
        tapCenter = (tapCenter - delta / slot).coerceIn(0f, (count - 1).toFloat())
    }

    fun settle() {
        tapCenter = tapCenter.roundToInt().toFloat()
    }
}

@Composable
private fun rememberScroller(state: ThemeArcState, count: Int, start: Int, slot: Dp): PhaseScroller {
    val density = LocalDensity.current
    return remember(count, start, density) { with(density) { PhaseScroller(state, count, start, slot.toPx(), 24.dp.toPx()) } }
}

/** The fractional index the scroller is showing now. */
@Composable
private fun PhaseScroller.shownCenter(state: ThemeArcState): Float {
    val tap by animateFloatAsState(tapCenter, label = "scroll")
    val drag = dragCenter()
    return when {
        drag != null -> drag.also { lastDrag = it }
        !state.isOpen && !lastDrag.isNaN() -> lastDrag
        else -> tap
    }
}

// endregion

// region Rail

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
private fun RailSwitcher(
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

private val WheelOffsets = floatArrayOf(0f, 84f, 144f, 192f, 232f, 268f, 300f) // dp from the center, by distance
private val WheelFonts = floatArrayOf(40f, 28f, 22f, 19f, 17f, 16f)
private val WheelAlphas = floatArrayOf(1f, 0.75f, 0.55f, 0.42f, 0.34f, 0.28f)

@Composable
private fun WheelSwitcher(
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
private fun DialSwitcher(
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

private val CardOffsets = floatArrayOf(0f, 150f, 240f, 300f) // dp from the center, by distance
private val CardScales = floatArrayOf(1f, 0.9f, 0.8f, 0.72f)
private val CardAlphas = floatArrayOf(1f, 0.8f, 0.35f, 0f)

@Composable
private fun CardSwitcher(
    state: ThemeArcState,
    entries: List<SwitcherEntry>,
    apps: Map<String, AppInfo>,
    active: Int,
    side: SwitcherSide,
    onSelect: (Int) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
) {
    val scroller = rememberScroller(state, entries.size, active, slot = 72.dp)
    SwitcherFrame(state, scrimAlpha = 0.8f, onScrimDrag = scroller::scrollBy, onScrimDragEnd = scroller::settle) { progress ->
        val density = LocalDensity.current
        val right = side == SwitcherSide.Right
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        SideEffect { state.hitTest = scroller::hit }
        val center = scroller.shownCenter(state)
        val focus = center.roundToInt().coerceIn(0, entries.lastIndex)
        val centerY = state.anchor.y.coerceIn(height * 0.35f, height * 0.5f)
        val alpha = progress.coerceIn(0f, 1f)
        val cardWidth = with(density) { min(300.dp.toPx(), width - 90.dp.toPx()).toDp() }
        val cardX = width / 2f + with(density) { (if (right) -14.dp else 14.dp).toPx() }

        entries.forEachIndexed { index, entry ->
            val theme = entry.theme
            val d = index - center
            val distance = abs(d)
            if (distance <= CardOffsets.lastIndex) {
                val y = centerY + sign(d) * with(density) { interp(CardOffsets, distance).dp.toPx() }
                val cardScale = interp(CardScales, distance)
                val cardAlpha = interp(CardAlphas, distance) * alpha
                val isFocus = index == focus
                val accent = entry.solid
                val ink = if (isFocus) contentColorOn(accent) else Color.White
                EntryBox(
                    entry = entry,
                    state = state,
                    onClick = { onSelect(index) },
                    themeActions = themeActions,
                    modifier = Modifier
                        .zIndex(10f - distance)
                        .placeAt(alignX = 0.5f, x = { cardX }, y = { y + (1f - progress) * 120f })
                        .graphicsLayer {
                            this.alpha = cardAlpha
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                        .width(cardWidth)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (isFocus) accent else SwitcherBackground)
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PhaseDot(entry, size = 44.dp, onAccent = isFocus)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                entry.label(),
                                color = ink,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (index == active && theme != null) NowChip(ink)
                        }
                        if (theme != null) {
                            Text(phaseMeta(theme), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val shown = remember(theme.appKeys, apps) { theme.appKeys.mapNotNull(apps::get).take(5) }
                            if (shown.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    shown.forEach { app -> AppIcon(app, size = 36.dp) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Where in the stack we are.
        Column(
            Modifier
                .placeAt(alignX = if (right) 1f else 0f, x = { if (right) width - with(density) { 14.dp.toPx() } else with(density) { 14.dp.toPx() } }, y = { centerY })
                .graphicsLayer { this.alpha = alpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                val on = index == focus
                Box(
                    Modifier
                        .width(8.dp)
                        .height(if (on) 28.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) entry.swatch else Color.White.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

// endregion

// region Bands

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
private fun BandSwitcher(
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
