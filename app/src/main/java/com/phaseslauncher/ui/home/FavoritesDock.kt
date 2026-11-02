package com.phaseslauncher.ui.home

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.ui.components.AppAction
import com.phaseslauncher.ui.components.AppMenuState
import com.phaseslauncher.ui.components.AppTileWithMenu
import com.phaseslauncher.ui.components.LocalExternalDrag
import com.phaseslauncher.ui.components.rememberReorderState
import com.phaseslauncher.ui.components.reorderContainer
import com.phaseslauncher.ui.components.reorderItem
import com.phaseslauncher.ui.theme.LocalWallpaperContent
import com.phaseslauncher.ui.theme.onWallpaper

/** Favorites shared by every theme, always in the same slots. */
@Composable
fun FavoritesDock(
    favorites: List<AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    appActions: (AppInfo) -> List<AppAction>,
    onReorder: (List<String>) -> Unit,
    onEmptyLongPress: () -> Unit,
    /** Tapping the empty dock: pick apps for it. */
    onAddApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallpaper = LocalWallpaperContent.current
    val menuState = remember { AppMenuState() }
    val reorder = rememberReorderState(favorites.map { it.key }, onReorder)
    val externalDrag = LocalExternalDrag.current
    Box(
        modifier = modifier
            // Apps carried from the drawer can be dropped here.
            .onGloballyPositioned { externalDrag?.dockBounds = it.boundsInWindow() }
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(wallpaper.scrim)
            .then(
                if (favorites.isEmpty()) {
                    Modifier.combinedClickable(
                        onClickLabel = stringResource(R.string.add_apps_to_the_dock),
                        onLongClick = onEmptyLongPress,
                        onClick = onAddApps,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.tap_to_add_apps_to_the_dock),
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
