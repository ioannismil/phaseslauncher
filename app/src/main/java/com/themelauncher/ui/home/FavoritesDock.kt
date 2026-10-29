package com.themelauncher.ui.home

import android.graphics.Rect
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.components.AppTileWithMenu
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.onWallpaper

/** Favorites shared by every theme, always in the same slots. */
@Composable
fun FavoritesDock(
    favorites: List<AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    appActions: (AppInfo) -> List<AppAction>,
    modifier: Modifier = Modifier,
) {
    val wallpaper = LocalWallpaperContent.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(wallpaper.scrim),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                favorites.forEach { app ->
                    AppTileWithMenu(
                        app = app,
                        labelStyle = MaterialTheme.typography.labelSmall,
                        onClick = { onLaunch(app, it) },
                        actions = { appActions(app) },
                        showLabel = false,
                    )
                }
            }
        }
    }
}
