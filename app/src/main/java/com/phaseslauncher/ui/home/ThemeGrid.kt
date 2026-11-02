package com.phaseslauncher.ui.home

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.phaseslauncher.HomeState
import com.phaseslauncher.data.items.FolderItem
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.widgets.ThemeWidget
import com.phaseslauncher.ui.components.AppAction
import com.phaseslauncher.ui.components.AppMenuState
import com.phaseslauncher.ui.components.HomeDrop
import com.phaseslauncher.ui.components.LocalExternalDrag
import com.phaseslauncher.ui.components.TilePadding
import com.phaseslauncher.ui.components.rememberReorderState
import com.phaseslauncher.ui.components.reorderBlocker
import com.phaseslauncher.ui.components.reorderContainer
import com.phaseslauncher.ui.components.reorderItem
import com.phaseslauncher.ui.theme.LocalWallpaperContent
import com.phaseslauncher.ui.theme.imageVector
import com.phaseslauncher.ui.theme.onWallpaper
import com.phaseslauncher.ui.widgets.HomeWidget

/**
 * The active theme's apps. Switching themes crossfades the grid and slides it toward the
 * side of the newly selected theme, so the direction matches the switcher.
 */
@Composable
fun ThemeGrid(
    state: HomeState,
    swipeUpConnection: NestedScrollConnection,
    onOpenItem: (HomeItem, Rect?) -> Unit,
    itemActions: (HomeItem) -> List<AppAction>,
    onCreateTheme: () -> Unit,
    onAddApps: (Theme) -> Unit,
    onReorder: (Theme, List<String>) -> Unit,
    /** An item dragged onto another's middle: (theme, order, dragged key, target key). */
    onMerge: (Theme, List<String>, String, String) -> Unit,
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
                title = stringResource(R.string.create_your_first_theme),
                body = stringResource(R.string.themes_group_your_apps_by_context_like_gaming_or),
                button = stringResource(R.string.new_theme),
                onClick = onCreateTheme,
                onLongPress = onEmptyLongPress,
            )
            else -> {
                val items = state.itemsFor(theme)
                val widgets = state.widgetsFor(theme)
                if (items.isEmpty() && widgets.isEmpty() && !state.isLoading) {
                    EmptyMessage(
                        title = "No apps in ${theme.name} yet",
                        body = stringResource(R.string.pick_apps_for_this_theme_or_long_press_any_app_i),
                        button = stringResource(R.string.add_apps),
                        onClick = { onAddApps(theme) },
                        onLongPress = onEmptyLongPress,
                        theme = theme,
                    )
                } else {
                    ItemGrid(
                        items = items,
                        widgets = widgets,
                        swipeUpConnection = swipeUpConnection,
                        onOpenItem = onOpenItem,
                        itemActions = itemActions,
                        onReorder = { onReorder(theme, it) },
                        onMerge = { order, dragged, target -> onMerge(theme, order, dragged, target) },
                        onEmptyLongPress = onEmptyLongPress,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemGrid(
    items: List<HomeItem>,
    widgets: List<ThemeWidget>,
    swipeUpConnection: NestedScrollConnection,
    onOpenItem: (HomeItem, Rect?) -> Unit,
    itemActions: (HomeItem) -> List<AppAction>,
    onReorder: (List<String>) -> Unit,
    onMerge: (List<String>, String, String) -> Unit,
    onEmptyLongPress: () -> Unit,
) {
    val wallpaper = LocalWallpaperContent.current
    val labelStyle = MaterialTheme.typography.labelMedium.onWallpaper(wallpaper)
    val menuState = remember { AppMenuState() }
    // Folders don't go into folders; anything else can be dropped onto an app or folder.
    val folderKeys = items.filterIsInstance<FolderItem>().mapTo(HashSet()) { it.key }
    val reorder = rememberReorderState(
        keys = items.map { it.key },
        onCommit = onReorder,
        onMerge = onMerge,
        canMerge = { dragged, _ -> dragged !in folderKeys },
    )
    val density = LocalDensity.current

    // Apps carried from the drawer land between items, or onto one's middle.
    val externalDrag = LocalExternalDrag.current
    val currentKeys by rememberUpdatedState(items.map { it.key })
    val gridBounds = remember { arrayOf(ComposeRect.Zero) }
    if (externalDrag != null) {
        DisposableEffect(externalDrag, reorder) {
            val resolver: (Offset) -> HomeDrop? = { window ->
                if (!gridBounds[0].contains(window)) {
                    null
                } else {
                    reorder.keyInMiddleAt(window)?.let(HomeDrop::Into) ?: HomeDrop.At(reorder.dropIndex(window, currentKeys))
                }
            }
            externalDrag.gridDrop = resolver
            onDispose { if (externalDrag.gridDrop === resolver) externalDrag.gridDrop = null }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { gridBounds[0] = it.boundsInWindow() }) {
        val layout = LocalHomeLayout.current
        val fit = if (layout.autoFit) {
            val labelHeight = if (!layout.showLabels) 0f else with(density) {
                val line = if (labelStyle.lineHeight.isSp) labelStyle.lineHeight else labelStyle.fontSize * 1.4f
                TilePadding.labelGap.value + line.toDp().value
            }
            // Widgets keep their height (plus their padding and the row gap); apps share the rest.
            val widgetsHeight = widgets.sumOf { it.heightDp + WIDGET_CHROME_DP }
            fitGrid(
                count = items.size,
                widthDp = maxWidth.value,
                // A little slack so rounding never tips the grid into scrolling.
                heightDp = maxHeight.value - widgetsHeight - 2f,
                metrics = GridMetrics(
                    gridPaddingHorizontal = GRID_PADDING_H * 2f,
                    gridPaddingVertical = GRID_PADDING_V * 2f,
                    rowSpacing = ROW_SPACING,
                    tilePaddingHorizontal = TilePadding.horizontal.value * 2,
                    tilePaddingVertical = TilePadding.vertical.value * 2,
                    labelHeight = labelHeight,
                ),
                maxIconDp = LauncherPrefs.AUTO_FIT_MAX_ICON_DP,
            )
        } else {
            null
        }
        val iconSize = fit?.iconSizeDp?.dp ?: layout.iconSize
        val arrangement = layout.arrangement
        reorder.rowsUpward = arrangement.fromBottom
        reorder.rightToLeft = arrangement.rightToLeft
        // Right-to-left fill is the grid laid out RTL; the tiles inside keep the app's own direction.
        val contentDirection = LocalLayoutDirection.current
        val gridDirection = if (arrangement.rightToLeft) {
            if (contentDirection == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
        } else {
            contentDirection
        }
        CompositionLocalProvider(LocalLayoutDirection provides gridDirection) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(fit?.columns ?: layout.columns),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(swipeUpConnection)
                    .reorderContainer(reorder, onDragStart = menuState::close, onEmptyLongPress = onEmptyLongPress),
                contentPadding = PaddingValues(horizontal = GRID_PADDING_H.dp, vertical = GRID_PADDING_V.dp),
                // From the bottom, the first item sits at the bottom and rows stack upward.
                reverseLayout = arrangement.fromBottom,
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING.dp, if (arrangement.fromBottom) Alignment.Bottom else Alignment.Top),
            ) {
                // The theme's widgets span the full width above its apps. Bottom-up, they come after
                // the apps (and in reverse) so they still read top to bottom above them.
                fun widgetItems() {
                    val indexed = widgets.withIndex().toList()
                    (if (arrangement.fromBottom) indexed.asReversed() else indexed).forEach { (index, widget) ->
                        item(key = "widget-${widget.appWidgetId}", span = { GridItemSpan(maxLineSpan) }) {
                            CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                                HomeWidget(
                                    widget = widget,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < widgets.lastIndex,
                                    modifier = Modifier.animateItem().reorderBlocker(reorder, "widget-${widget.appWidgetId}"),
                                )
                            }
                        }
                    }
                }
                if (!arrangement.fromBottom) widgetItems()
                val ordered = reorder.arrange(items) { it.key }
                items(ordered, key = { it.key }) { item ->
                    CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                        HomeItemTile(
                            // Screen readers can't drag; these move the item one place instead.
                            accessibilityActions = moveActions(ordered.map { it.key }, item.key, onReorder),
                            item = item,
                            labelStyle = labelStyle,
                            onClick = { onOpenItem(item, it) },
                            actions = { itemActions(item) },
                            menuState = menuState,
                            iconSize = iconSize,
                            showLabel = layout.showLabels,
                            // A light tonal tile, like the system launcher's folders, readable on any wallpaper.
                            folderBackground = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                            modifier = Modifier.animateItem().reorderItem(reorder, item.key),
                        )
                    }
                }
                if (arrangement.fromBottom) widgetItems()
            }
        }
    }
}

private fun moveActions(keys: List<String>, key: String, onReorder: (List<String>) -> Unit): List<CustomAccessibilityAction> {
    val index = keys.indexOf(key)
    fun moveTo(target: Int) = CustomAccessibilityAction(if (target < index) "Move earlier" else "Move later") {
        onReorder(keys.toMutableList().apply { add(target, removeAt(index)) })
        true
    }
    return listOfNotNull(
        moveTo(index - 1).takeIf { index > 0 },
        moveTo(index + 1).takeIf { index < keys.lastIndex },
    )
}

private const val GRID_PADDING_H = 12f
private const val GRID_PADDING_V = 8f
private const val ROW_SPACING = 4f

/** [HomeWidget]'s vertical padding plus the grid's row gap. */
private const val WIDGET_CHROME_DP = 8 + 4

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
