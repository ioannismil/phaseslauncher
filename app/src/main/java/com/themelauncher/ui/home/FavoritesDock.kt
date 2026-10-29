package com.themelauncher.ui.home

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.components.AppMenuState
import com.themelauncher.ui.components.AppTileWithMenu
import com.themelauncher.ui.components.rememberReorderState
import com.themelauncher.ui.components.reorderContainer
import com.themelauncher.ui.components.reorderItem
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.onWallpaper

/** Favorites shared by every theme, always in the same slots. */
@Composable
fun FavoritesDock(
    favorites: List<AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    appActions: (AppInfo) -> List<AppAction>,
    onReorder: (List<String>) -> Unit,
    onEmptyLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallpaper = LocalWallpaperContent.current
    val menuState = remember { AppMenuState() }
    val reorder = rememberReorderState(favorites.map { it.key }, onReorder)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(wallpaper.scrim)
            .pointerInput(favorites.isEmpty()) {
                if (favorites.isEmpty()) detectTapGestures(onLongPress = { onEmptyLongPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (favorites.isEmpty()) {
            Text(
                text = "Your dock is empty. Long-press an app in the drawer to add favorites.",
                style = MaterialTheme.typography.bodySmall.onWallpaper(wallpaper),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .reorderContainer(reorder, onDragStart = menuState::close, onEmptyLongPress = onEmptyLongPress),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reorder.arrange(favorites) { it.key }.forEach { app ->
                    key(app.key) {
                        AppTileWithMenu(
                            app = app,
                            labelStyle = MaterialTheme.typography.labelSmall,
                            onClick = { onLaunch(app, it) },
                            actions = { appActions(app) },
                            showLabel = false,
                            iconSize = LocalHomeLayout.current.iconSize,
                            menuState = menuState,
                            modifier = Modifier.reorderItem(reorder, app.key),
                        )
                    }
                }
            }
        }
    }
}
