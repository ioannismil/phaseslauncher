package com.themelauncher.ui.home

import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.themelauncher.HomeState
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.components.AppMenuState
import com.themelauncher.ui.components.AppTileWithMenu
import com.themelauncher.ui.components.rememberReorderState
import com.themelauncher.ui.components.reorderContainer
import com.themelauncher.ui.components.reorderItem
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.imageVector
import com.themelauncher.ui.theme.onWallpaper

/**
 * The active theme's apps. Switching themes crossfades the grid and slides it toward the
 * side of the newly selected theme, so the direction matches the switcher.
 */
@Composable
fun ThemeGrid(
    state: HomeState,
    swipeUpConnection: NestedScrollConnection,
    onLaunch: (AppInfo, Rect?) -> Unit,
    appActions: (AppInfo) -> List<AppAction>,
    onCreateTheme: () -> Unit,
    onAddApps: (Theme) -> Unit,
    onReorder: (Theme, List<String>) -> Unit,
    onEmptyLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themes = state.themes
    AnimatedContent(
        targetState = state.activeTheme?.id,
        modifier = modifier,
        transitionSpec = {
            val from = themes.indexOfFirst { it.id == initialState }
            val to = themes.indexOfFirst { it.id == targetState }
            val direction = if (to >= from) 1 else -1
            val enter = fadeIn(tween(220, delayMillis = 40)) +
                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { direction * it / 10 } +
                scaleIn(tween(280), initialScale = 0.97f)
            val exit = fadeOut(tween(120)) +
                slideOutHorizontally(tween(200)) { -direction * it / 14 }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "themeGrid",
    ) { themeId ->
        val theme = themes.firstOrNull { it.id == themeId }
        when {
            theme == null -> EmptyMessage(
                title = "Create your first theme",
                body = "Themes group your apps by context, like Gaming or Work.",
                button = "New theme",
                onClick = onCreateTheme,
                onLongPress = onEmptyLongPress,
            )
            else -> {
                val apps = state.appsFor(theme)
                if (apps.isEmpty() && !state.isLoading) {
                    EmptyMessage(
                        title = "No apps in ${theme.name} yet",
                        body = "Pick apps for this theme, or long-press any app in the drawer.",
                        button = "Add apps",
                        onClick = { onAddApps(theme) },
                        onLongPress = onEmptyLongPress,
                        theme = theme,
                    )
                } else {
                    AppGrid(apps, swipeUpConnection, onLaunch, appActions, onReorder = { onReorder(theme, it) }, onEmptyLongPress = onEmptyLongPress)
                }
            }
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    swipeUpConnection: NestedScrollConnection,
    onLaunch: (AppInfo, Rect?) -> Unit,
    appActions: (AppInfo) -> List<AppAction>,
    onReorder: (List<String>) -> Unit,
    onEmptyLongPress: () -> Unit,
) {
    val wallpaper = LocalWallpaperContent.current
    val labelStyle = MaterialTheme.typography.labelMedium.onWallpaper(wallpaper)
    val menuState = remember { AppMenuState() }
    val reorder = rememberReorderState(apps.map { it.key }, onReorder)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth < 600.dp) GridCells.Fixed(4) else GridCells.Adaptive(96.dp)
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(swipeUpConnection)
                .reorderContainer(reorder, onDragStart = menuState::close, onEmptyLongPress = onEmptyLongPress),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(reorder.arrange(apps) { it.key }, key = { it.key }) { app ->
                AppTileWithMenu(
                    app = app,
                    labelStyle = labelStyle,
                    onClick = { onLaunch(app, it) },
                    actions = { appActions(app) },
                    menuState = menuState,
                    modifier = Modifier.animateItem().reorderItem(reorder, app.key),
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    theme: Theme? = null,
) {
    val wallpaper = LocalWallpaperContent.current
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (theme != null) {
                Icon(
                    theme.icon.imageVector,
                    contentDescription = null,
                    tint = Color(theme.accentColor),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.onWallpaper(wallpaper),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.onWallpaper(wallpaper),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}
