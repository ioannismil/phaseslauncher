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

private val CardOffsets = floatArrayOf(0f, 150f, 240f, 300f) // dp from the center, by distance
private val CardScales = floatArrayOf(1f, 0.9f, 0.8f, 0.72f)
private val CardAlphas = floatArrayOf(1f, 0.8f, 0.35f, 0f)

@Composable
internal fun CardSwitcher(
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
