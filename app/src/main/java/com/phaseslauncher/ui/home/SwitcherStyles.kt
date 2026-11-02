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
internal data class SwitcherEntry(val theme: Theme?, val extra: ArcExtra?) {
    val accent: Color get() = theme?.let { Color(it.accentColor) } ?: Color.White.copy(alpha = 0.18f)
    val icon: ImageVector get() = theme?.icon?.imageVector ?: extra?.icon ?: Icons.Rounded.Add
    val iconTint: Color get() = if (theme != null) contentColorOn(accent) else Color.White

    /** An opaque color to mark it with: the phase's accent, or a light grey for the extras. */
    val swatch: Color get() = if (theme != null) accent else Color(0xFFB8BECC)

    /** An opaque color to fill it with: the phase's accent, or a slate for the extras. */
    val solid: Color get() = if (theme != null) accent else Color(0xFF3A4150)
}

@Composable
internal fun SwitcherEntry.label(): String = theme?.name ?: extra?.label.orEmpty()

/** "Silent · 6 apps · 9 blocked". */
@Composable
internal fun phaseMeta(theme: Theme): String = buildList {
    if (theme.soundProfile != SoundProfile.Unchanged) add(theme.soundProfile.label)
    add(pluralStringResource(R.plurals.apps_count, theme.appKeys.size, theme.appKeys.size))
    val blocked = theme.focus.blockedApps.size
    if (blocked > 0) add(pluralStringResource(R.plurals.blocked_count, blocked, blocked))
}.joinToString(" · ")

internal val SwitcherBackground = Color(0xFF1A1F2B)

/**
 * The scrim and the open/close animation every style shares. In tap mode the scrim closes the
 * switcher on a tap and, if [onScrimDrag] is given, scrolls it on a vertical drag.
 */
@Composable
internal fun SwitcherFrame(
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
internal fun Modifier.placeAt(alignX: Float = 0f, x: () -> Float, y: () -> Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.width, placeable.height) {
        placeable.place((x() - alignX * placeable.width).roundToInt(), (y() - placeable.height / 2f).roundToInt())
    }
}

/** Tappable in tap mode (long-press: the phase's actions), with the actions menu attached. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryBox(
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
internal fun PhaseDot(entry: SwitcherEntry, size: Dp, ring: Color? = null, onAccent: Boolean = false) {
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
internal fun NowChip(color: Color) {
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
internal fun interp(stops: FloatArray, x: Float, tail: Float = 0f): Float {
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
internal class PhaseScroller(
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
internal fun rememberScroller(state: ThemeArcState, count: Int, start: Int, slot: Dp): PhaseScroller {
    val density = LocalDensity.current
    return remember(count, start, density) { with(density) { PhaseScroller(state, count, start, slot.toPx(), 24.dp.toPx()) } }
}

/** The fractional index the scroller is showing now. */
@Composable
internal fun PhaseScroller.shownCenter(state: ThemeArcState): Float {
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
