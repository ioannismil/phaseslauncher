package com.phaseslauncher.ui.home

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.phaseslauncher.R
import com.phaseslauncher.data.items.AppItem
import com.phaseslauncher.data.items.FolderItem
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.data.items.ShortcutItem
import com.phaseslauncher.data.notifications.NotificationDotsRepository
import com.phaseslauncher.ui.components.AppAction
import com.phaseslauncher.ui.components.AppIcon
import com.phaseslauncher.ui.components.AppMenuState
import com.phaseslauncher.ui.components.AppTileWithMenu
import com.phaseslauncher.ui.components.LauncherTile
import com.phaseslauncher.ui.components.LocalNotificationDots
import com.phaseslauncher.ui.components.LocalShortcutHost
import com.phaseslauncher.ui.components.TileWithMenu

/** An app, pinned shortcut or folder tile with its long-press menu. */
@Composable
fun HomeItemTile(
    item: HomeItem,
    labelStyle: TextStyle,
    onClick: (Rect?) -> Unit,
    actions: () -> List<AppAction>,
    menuState: AppMenuState,
    iconSize: Dp,
    showLabel: Boolean,
    folderBackground: Color,
    modifier: Modifier = Modifier,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    when (item) {
        is AppItem -> AppTileWithMenu(
            accessibilityActions = accessibilityActions,
            app = item.app,
            labelStyle = labelStyle,
            onClick = onClick,
            actions = actions,
            menuState = menuState,
            iconSize = iconSize,
            showLabel = showLabel,
            modifier = modifier,
        )

        else -> TileWithMenu(key = item.key, title = item.label, actions = actions, menuState = menuState, modifier = modifier) { onLongClick ->
            val dots = LocalNotificationDots.current
            LauncherTile(
                label = item.label,
                labelStyle = labelStyle,
                onClick = onClick,
                onLongClick = onLongClick,
                iconSize = iconSize,
                showLabel = showLabel,
                description = when (item) {
                    is FolderItem -> stringResource(R.string.folder_description, item.label, pluralStringResource(R.plurals.apps_count, item.items.size, item.items.size))
                    is ShortcutItem -> stringResource(R.string.shortcut_from, item.label, item.app.label)
                },
                accessibilityActions = accessibilityActions,
                // A folder shows a dot when any app inside it has one.
                hasDot = item is FolderItem && item.items.any { inner ->
                    inner is AppItem && NotificationDotsRepository.dotKey(inner.app.packageName, inner.app.user) in dots
                },
            ) { ItemIcon(item, iconSize, folderBackground) }
        }
    }
}

@Composable
fun ItemIcon(item: HomeItem, size: Dp, folderBackground: Color) {
    when (item) {
        is AppItem -> AppIcon(item.app, size = size)
        is ShortcutItem -> ShortcutIcon(item, size)
        is FolderItem -> FolderIcon(item, size, folderBackground)
    }
}

/** The shortcut's own icon with its app's icon as a small badge, like the stock launcher. */
@Composable
fun ShortcutIcon(item: ShortcutItem, size: Dp, badge: Boolean = true) {
    val host = LocalShortcutHost.current
    val icon by produceState<ImageBitmap?>(null, item.key) { value = host?.pinnedIcon(item.shortcut) }
    val bitmap = icon
    if (bitmap == null) {
        // The system only gives shortcuts to the default home app; fall back to the app's icon.
        AppIcon(item.app, size = size)
        return
    }
    Box(Modifier.size(size)) {
        Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        if (badge) AppIcon(item.app, size = size * 0.4f, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** The first four items in a 2×2 grid on a rounded backdrop. */
@Composable
fun FolderIcon(item: FolderItem, size: Dp, background: Color) {
    val inner = size * 0.12f
    val gap = size * 0.06f
    val cell = (size - inner * 2 - gap) / 2
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(background)
            .padding(inner),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            item.items.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { child ->
                        when (child) {
                            is ShortcutItem -> ShortcutIcon(child, cell, badge = false)
                            else -> ItemIcon(child, cell, background)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.size(cell))
                }
            }
        }
    }
}
