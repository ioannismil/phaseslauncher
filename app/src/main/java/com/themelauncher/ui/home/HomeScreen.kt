package com.themelauncher.ui.home

import android.graphics.Rect
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.themelauncher.HomeState
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.components.AppAction

/** Clock, theme switcher, the active theme's grid, and the favorites dock. */
@Composable
fun HomeScreen(
    state: HomeState,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onSelectTheme: (Theme) -> Unit,
    onThemeLongPress: (Theme) -> Unit,
    onCreateTheme: () -> Unit,
    onAddApps: (Theme) -> Unit,
    onSwipeUp: () -> Unit,
    gridAppActions: (AppInfo) -> List<AppAction>,
    dockAppActions: (AppInfo) -> List<AppAction>,
    modifier: Modifier = Modifier,
) {
    val swipeUpConnection = rememberSwipeUpConnection(onSwipeUp)

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ClockHeader(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp))
        Spacer(Modifier.height(20.dp))
        ThemeSwitcher(
            themes = state.themes,
            activeThemeId = state.activeTheme?.id,
            onSelect = onSelectTheme,
            onLongPress = onThemeLongPress,
            onAdd = onCreateTheme,
        )
        Spacer(Modifier.height(8.dp))
        ThemeGrid(
            state = state,
            swipeUpConnection = swipeUpConnection,
            onLaunch = onLaunch,
            appActions = gridAppActions,
            onCreateTheme = onCreateTheme,
            onAddApps = onAddApps,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        FavoritesDock(
            favorites = state.favorites,
            onLaunch = onLaunch,
            appActions = dockAppActions,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/**
 * Opens the drawer when an upward drag is left over after the grid has scrolled as far as it
 * can (or when it has nothing to scroll), so "swipe up" also works over the app grid.
 */
@Composable
private fun rememberSwipeUpConnection(onSwipeUp: () -> Unit): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val currentOnSwipeUp = rememberUpdatedState(onSwipeUp)
    return remember(thresholdPx) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0) {
                    pulled -= available.y
                    if (pulled > thresholdPx) {
                        pulled = 0f
                        currentOnSwipeUp.value()
                    }
                } else if (available.y > 0 || consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}
