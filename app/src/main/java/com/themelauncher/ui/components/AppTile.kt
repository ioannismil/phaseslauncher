package com.themelauncher.ui.components

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.AppShortcut
import com.themelauncher.data.apps.IconCache
import com.themelauncher.data.notifications.NotificationDotsRepository

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val iconCache = LocalIconCache.current
    val generation by iconCache.generation.collectAsState()
    val icon by produceState(iconCache.peek(app.key), app.key, generation) {
        value = iconCache.peek(app.key) ?: iconCache.load(app)
    }
    Box(modifier.size(size)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Icon + label. Callbacks receive the tile's bounds in window coordinates so the system
 * can animate the launched app out of the icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    labelStyle: TextStyle,
    onClick: (Rect?) -> Unit,
    onLongClick: ((Rect?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showLabel: Boolean = true,
) {
    val bounds = remember { Rect() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                bounds.set(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            .combinedClickable(
                onClick = { onClick(Rect(bounds)) },
                onLongClick = onLongClick?.let { { it(Rect(bounds)) } },
                onLongClickLabel = onLongClick?.let { "Options" },
            )
            .semantics(mergeDescendants = true) { if (!showLabel) contentDescription = app.label }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hasDot = NotificationDotsRepository.dotKey(app.packageName, app.user) in LocalNotificationDots.current
        Box {
            AppIcon(app, size = iconSize)
            if (hasDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(iconSize * 0.26f)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(1.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "New notifications" },
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

data class AppAction(val label: String, val icon: ImageVector, val onClick: (Rect?) -> Unit)

/** An [AppTile] whose long-press opens a menu of [actions], anchored to the tile. */
@Composable
fun AppTileWithMenu(
    app: AppInfo,
    labelStyle: TextStyle,
    onClick: (Rect?) -> Unit,
    actions: () -> List<AppAction>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showLabel: Boolean = true,
    menuState: AppMenuState = remember { AppMenuState() },
) {
    val expanded = menuState.openKey == app.key
    val shortcutHost = LocalShortcutHost.current
    // App shortcuts load when the menu opens; actions show immediately.
    val shortcuts by produceState(emptyList<AppShortcut>(), expanded, app.key) {
        value = if (expanded && shortcutHost != null) shortcutHost.load(app) else emptyList()
    }
    Box(modifier) {
        AppTile(
            app = app,
            labelStyle = labelStyle,
            onClick = onClick,
            onLongClick = { menuState.open(app.key, it) },
            iconSize = iconSize,
            showLabel = showLabel,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { menuState.close() },
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shortcuts.isNotEmpty() && shortcutHost != null) {
                shortcuts.forEach { shortcut ->
                    DropdownMenuItem(
                        text = { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            shortcut.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                        },
                        onClick = {
                            val bounds = menuState.bounds
                            menuState.close()
                            shortcutHost.launch(shortcut, bounds)
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            actions().forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        val bounds = menuState.bounds
                        menuState.close()
                        action.onClick(bounds)
                    },
                )
            }
        }
    }
}

/** Which tile's long-press menu is open. Shared by a group of tiles so a drag can close it. */
@Stable
class AppMenuState {
    var openKey by mutableStateOf<String?>(null)
        private set
    var bounds: Rect? = null
        private set

    fun open(key: String, bounds: Rect?) {
        this.bounds = bounds
        openKey = key
    }

    fun close() {
        openKey = null
    }
}

/** Loads and launches app shortcuts for long-press menus. */
interface ShortcutHost {
    suspend fun load(app: AppInfo): List<AppShortcut>
    fun launch(shortcut: AppShortcut, sourceBounds: Rect?)
}

val LocalShortcutHost = staticCompositionLocalOf<ShortcutHost?> { null }

/** Dot keys ([NotificationDotsRepository.dotKey]) of apps to badge; empty when dots are off. */
val LocalNotificationDots = compositionLocalOf<Set<String>> { emptySet() }

@Composable
fun drawerLabelStyle(): TextStyle =
    MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
