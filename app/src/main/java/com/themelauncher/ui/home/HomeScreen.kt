package com.themelauncher.ui.home

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.themelauncher.HomeState
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.settings.ClockPosition
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.components.AppAction

/**
 * Clock and active theme, the theme's grid, and the favorites dock, with the theme switcher
 * bar on the [switcherSide] edge.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    arcState: ThemeArcState,
    switcherSide: SwitcherSide,
    onArcRelease: (Int?) -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onCreateTheme: () -> Unit,
    onAddApps: (Theme) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    gridAppActions: (AppInfo) -> List<AppAction>,
    dockAppActions: (AppInfo) -> List<AppAction>,
    onReorderThemeApps: (Theme, List<String>) -> Unit,
    onReorderFavorites: (List<String>) -> Unit,
    onEmptyLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeUpConnection = rememberVerticalSwipeConnection(onSwipeUp, onSwipeDown)
    val barRight = switcherSide == SwitcherSide.Right
    // Keep app icons clear of the bar's touch area.
    val gridPadding = if (barRight) PaddingValues(end = 12.dp) else PaddingValues(start = 12.dp)

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val layout = LocalHomeLayout.current
            // The clock block and the theme label under it share the chosen position.
            val headerAlignment = when (layout.clockPosition) {
                ClockPosition.Left -> Alignment.Start
                ClockPosition.Center -> Alignment.CenterHorizontally
                ClockPosition.Right -> Alignment.End
            }
            if (layout.showHeader) {
                ClockHeader(
                    showTime = layout.showTime,
                    showDate = layout.showDate,
                    dateFormat = layout.dateFormat,
                    showBattery = layout.showBattery,
                    alignment = headerAlignment,
                    onLongPress = onEmptyLongPress,
                    modifier = Modifier
                        .align(headerAlignment)
                        .padding(start = 24.dp, end = 24.dp, top = if (layout.showTime) 24.dp else 16.dp),
                )
                Spacer(Modifier.height(14.dp))
            } else if (layout.showThemeLabel) {
                // Nothing to show: the theme label tucks under the status bar and apps get the room.
                Spacer(Modifier.height(12.dp))
            }
            if (layout.showThemeLabel) {
                ActiveThemeLabel(
                    theme = state.activeTheme,
                    onClick = arcState::openForTap,
                    modifier = Modifier
                        .align(headerAlignment)
                        .padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
            } else if (!layout.showHeader) {
                // Neither clock nor label: just clear the status bar.
                Spacer(Modifier.height(8.dp))
            }
            ThemeGrid(
                state = state,
                swipeUpConnection = swipeUpConnection,
                onLaunch = onLaunch,
                appActions = gridAppActions,
                onCreateTheme = onCreateTheme,
                onAddApps = onAddApps,
                onReorder = onReorderThemeApps,
                onEmptyLongPress = onEmptyLongPress,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(gridPadding),
            )
            FavoritesDock(
                favorites = state.favorites,
                onLaunch = onLaunch,
                appActions = dockAppActions,
                onReorder = onReorderFavorites,
                onEmptyLongPress = onEmptyLongPress,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }

        if (state.themes.isNotEmpty()) {
            ThemeEdgeBar(
                state = arcState,
                side = switcherSide,
                accent = state.activeTheme?.let { Color(it.accentColor) } ?: Color.White,
                onRelease = onArcRelease,
                modifier = Modifier
                    // Slightly below center: within thumb reach on tall phones.
                    .align(BiasAlignment(horizontalBias = if (barRight) 1f else -1f, verticalBias = 0.15f)),
            )
        }
    }
}

/**
 * Swipe gestures over the app grid: an upward drag left over after the grid has scrolled as far
 * as it can (or when it has nothing to scroll) opens the drawer; a downward pull at the top of
 * the grid pulls down the notification shade. Each fires once per gesture.
 */
@Composable
private fun rememberVerticalSwipeConnection(onSwipeUp: () -> Unit, onSwipeDown: () -> Unit): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val currentOnSwipeUp = rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown = rememberUpdatedState(onSwipeDown)
    return remember(thresholdPx) {
        object : NestedScrollConnection {
            var pulled = 0f
            var fired = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || fired) return Offset.Zero
                if (consumed.y != 0f) {
                    pulled = 0f
                    return Offset.Zero
                }
                // Negative = upward overscroll, positive = downward.
                if (available.y != 0f && (pulled == 0f || (pulled < 0) == (available.y < 0))) {
                    pulled += available.y
                } else {
                    pulled = available.y
                }
                when {
                    pulled < -thresholdPx -> { fired = true; currentOnSwipeUp.value() }
                    pulled > thresholdPx -> { fired = true; currentOnSwipeDown.value() }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                fired = false
                return Velocity.Zero
            }
        }
    }
}
