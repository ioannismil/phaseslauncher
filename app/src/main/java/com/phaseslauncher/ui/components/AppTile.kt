package com.phaseslauncher.ui.components

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.AppShortcut
import com.phaseslauncher.data.apps.IconCache
import com.phaseslauncher.data.items.PinnedShortcut
import com.phaseslauncher.data.notifications.NotificationDotsRepository

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val iconCache = LocalIconCache.current
    val generation by iconCache.generation.collectAsState()
    // Keeps showing the previous icon while a new one renders (e.g. themed icons changing color).
    var icon by remember(app.key) { mutableStateOf(iconCache.peek(app)) }
    LaunchedEffect(app.key, generation) {
        icon = iconCache.peek(app) ?: iconCache.load(app) ?: icon
    }
    Box(modifier.size(size)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Icon + label. Callbacks receive the tile's bounds in screen coordinates so the system
 * can animate the launched app out of the icon.
 */
@Composable
fun AppTile(
    app: AppInfo,
    labelStyle: TextStyle,
    onClick: (Rect?) -> Unit,
    onLongClick: ((Rect?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showLabel: Boolean = true,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    LauncherTile(
        label = app.label,
        accessibilityActions = accessibilityActions,
        labelStyle = labelStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        iconSize = iconSize,
        showLabel = showLabel,
        hasDot = NotificationDotsRepository.dotKey(app.packageName, app.user) in LocalNotificationDots.current,
    ) { AppIcon(app, size = iconSize) }
}

/** The tile layout shared by apps, pinned shortcuts and folders: [icon] (drawn at [iconSize]) over a label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherTile(
    label: String,
    labelStyle: TextStyle,
    onClick: (Rect?) -> Unit,
    onLongClick: ((Rect?) -> Unit)?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showLabel: Boolean = true,
    hasDot: Boolean = false,
    /** What screen readers say instead of the label (e.g. "Folder Games, 4 apps"). */
    description: String? = null,
    /** Screen-reader alternatives to dragging, e.g. "Move earlier". */
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    icon: @Composable () -> Unit,
) {
    val res = LocalResources.current
    val bounds = remember { Rect() }
    val view = LocalView.current
    val origin = remember { IntArray(2) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .onGloballyPositioned { coords ->
                // Screen coordinates, so tiles in a dialog (folders) work like those on home.
                view.getLocationOnScreen(origin)
                val b = coords.boundsInRoot()
                bounds.set(
                    origin[0] + b.left.toInt(),
                    origin[1] + b.top.toInt(),
                    origin[0] + b.right.toInt(),
                    origin[1] + b.bottom.toInt(),
                )
            }
            .combinedClickable(
                onClick = { onClick(Rect(bounds)) },
                onLongClick = onLongClick?.let { { it(Rect(bounds)) } },
                onLongClickLabel = onLongClick?.let { stringResource(R.string.options) },
            )
            .semantics(mergeDescendants = true) {
                when {
                    description != null -> contentDescription = description
                    !showLabel -> contentDescription = label
                }
                if (accessibilityActions.isNotEmpty()) customActions = accessibilityActions
            }
            .padding(vertical = TilePadding.vertical, horizontal = TilePadding.horizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            icon()
            if (hasDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(iconSize * 0.26f)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(1.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = res.getString(R.string.new_notifications) },
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(TilePadding.labelGap))
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Spacing inside a [LauncherTile]; the auto-fitting home grid needs it to size icons. */
object TilePadding {
    val vertical = 8.dp
    val horizontal = 4.dp
    val labelGap = 6.dp
}

data class AppAction(val label: String, val icon: ImageVector, val onClick: (Rect?) -> Unit)

/** An [AppTile] whose long-press opens a menu of the app's shortcuts and [actions], anchored to the tile. */
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
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val expanded = menuState.openKey == app.key
    val shortcutHost = LocalShortcutHost.current
    // App shortcuts load when the menu opens; actions show immediately.
    val shortcuts by produceState(emptyList<AppShortcut>(), expanded, app.key) {
        value = if (expanded && shortcutHost != null) shortcutHost.load(app) else emptyList()
    }
    TileWithMenu(
        key = app.key,
        title = app.label,
        actions = actions,
        menuState = menuState,
        modifier = modifier,
        shortcuts = shortcuts,
        onShortcut = { shortcut, bounds -> shortcutHost?.launch(shortcut, bounds) },
    ) { onLongClick ->
        AppTile(
            app = app,
            labelStyle = labelStyle,
            onClick = onClick,
            onLongClick = onLongClick,
            iconSize = iconSize,
            showLabel = showLabel,
            accessibilityActions = accessibilityActions,
        )
    }
}

/**
 * Any tile whose long-press opens a menu: [shortcuts] (an app's shortcuts) first, then [actions].
 * [tile] gets the long-press handler to put on itself.
 */
@Composable
fun TileWithMenu(
    key: String,
    title: String,
    actions: () -> List<AppAction>,
    menuState: AppMenuState,
    modifier: Modifier = Modifier,
    shortcuts: List<AppShortcut> = emptyList(),
    onShortcut: (AppShortcut, Rect?) -> Unit = { _, _ -> },
    tile: @Composable (onLongClick: (Rect?) -> Unit) -> Unit,
) {
    Box(modifier) {
        tile { menuState.open(key, it) }
        DropdownMenu(
            expanded = menuState.openKey == key,
            onDismissRequest = { menuState.close() },
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shortcuts.isNotEmpty()) {
                shortcuts.forEach { shortcut ->
                    DropdownMenuItem(
                        text = { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            shortcut.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                        },
                        onClick = {
                            val bounds = menuState.bounds
                            menuState.close()
                            onShortcut(shortcut, bounds)
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

/** Loads and launches app shortcuts for long-press menus, and draws pinned shortcuts. */
interface ShortcutHost {
    suspend fun load(app: AppInfo): List<AppShortcut>
    fun launch(shortcut: AppShortcut, sourceBounds: Rect?)
    suspend fun pinnedIcon(shortcut: PinnedShortcut): ImageBitmap?
}

val LocalShortcutHost = staticCompositionLocalOf<ShortcutHost?> { null }

/** Dot keys ([NotificationDotsRepository.dotKey]) of apps to badge; empty when dots are off. */
val LocalNotificationDots = compositionLocalOf<Set<String>> { emptySet() }

@Composable
fun drawerLabelStyle(): TextStyle =
    MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
