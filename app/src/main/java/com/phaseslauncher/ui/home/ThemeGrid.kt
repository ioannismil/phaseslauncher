package com.phaseslauncher.ui.home

import android.content.res.Resources
import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.phaseslauncher.HomeState
import com.phaseslauncher.R
import com.phaseslauncher.data.items.GridCell
import com.phaseslauncher.data.items.GridEntry
import com.phaseslauncher.data.items.GridPlacement
import com.phaseslauncher.data.items.HomeGrid
import com.phaseslauncher.data.items.HomeItem
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.widgets.ThemeWidget
import com.phaseslauncher.ui.components.AppAction
import com.phaseslauncher.ui.components.AppMenuState
import com.phaseslauncher.ui.components.HomeDrop
import com.phaseslauncher.ui.components.LocalExternalDrag
import com.phaseslauncher.ui.components.TilePadding
import com.phaseslauncher.ui.theme.LocalWallpaperContent
import com.phaseslauncher.ui.theme.imageVector
import com.phaseslauncher.ui.theme.onWallpaper
import com.phaseslauncher.ui.widgets.HomeWidget
import com.phaseslauncher.ui.widgets.WidgetResizeDialog
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The active theme's apps and widgets. Switching themes crossfades the grid and slides it toward
 * the side of the newly selected theme, so the direction matches the switcher.
 */
@Composable
fun ThemeGrid(
    state: HomeState,
    swipeUpConnection: NestedScrollConnection,
    onOpenItem: (HomeItem, Rect?) -> Unit,
    itemActions: (HomeItem) -> List<AppAction>,
    onCreateTheme: () -> Unit,
    onAddApps: (Theme) -> Unit,
    /** Something was moved or resized: where everything on the theme's grid now is. */
    onLayoutChange: (Theme, List<GridPlacement>) -> Unit,
    /** An item dragged onto another's middle: (theme, layout at the drop, dragged key, target key). */
    onMerge: (Theme, List<GridPlacement>, String, String) -> Unit,
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
                        title = stringResource(R.string.no_apps_in_yet, theme.name),
                        body = stringResource(R.string.pick_apps_for_this_theme_or_long_press_any_app_i),
                        button = stringResource(R.string.add_apps),
                        onClick = { onAddApps(theme) },
                        onLongPress = onEmptyLongPress,
                        theme = theme,
                    )
                } else {
                    ItemGrid(
                        cells = theme.cells,
                        items = items,
                        widgets = widgets,
                        swipeUpConnection = swipeUpConnection,
                        onOpenItem = onOpenItem,
                        itemActions = itemActions,
                        onLayoutChange = { onLayoutChange(theme, it) },
                        onMerge = { layout, dragged, target -> onMerge(theme, layout, dragged, target) },
                        onEmptyLongPress = onEmptyLongPress,
                    )
                }
            }
        }
    }
}

/** Cell size and padding in px, for turning grid cells into positions and back. */
private class GridGeometry(val columns: Int, val cellWidth: Float, val rowHeight: Float, val padX: Float, val padY: Float) {
    fun rectOf(p: GridPlacement) = ComposeRect(
        Offset(padX + p.x * cellWidth, padY + p.y * rowHeight),
        androidx.compose.ui.geometry.Size(p.width * cellWidth, p.height * rowHeight),
    )

    /** The cell whose top-left is nearest [topLeft]. */
    fun nearestCell(topLeft: Offset) = GridCell(
        ((topLeft.x - padX) / cellWidth).roundToInt().coerceIn(0, columns - 1),
        ((topLeft.y - padY) / rowHeight).roundToInt().coerceAtLeast(0),
    )

    fun cellUnder(position: Offset) = GridCell(
        floor((position.x - padX) / cellWidth).toInt().coerceIn(0, columns - 1),
        floor((position.y - padY) / rowHeight).toInt().coerceAtLeast(0),
    )

    /** Over an app's or folder's icon, roughly: dropping there merges into a folder. */
    fun isInMiddle(p: GridPlacement, position: Offset): Boolean {
        val r = rectOf(p)
        // The icon sits in the upper part of a tile, above its label.
        val middle = Offset(r.center.x, r.top + r.height * 0.42f)
        return (position - middle).getDistance() < minOf(r.width, r.height) * MIDDLE_FRACTION
    }
}

/** An item or widget being dragged around the grid. */
@Stable
private class GridDragState {
    var key by mutableStateOf<String?>(null)
        private set

    /** Finger position in the grid's content coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Where everything would be if dropped now. */
    var preview by mutableStateOf<List<GridPlacement>?>(null)
        private set

    /** The item the dragged one would go into (as a folder) if dropped now. */
    var mergeTarget by mutableStateOf<String?>(null)
        private set

    /** A layout just saved, shown until the theme's cells catch up. */
    var pending by mutableStateOf<List<GridPlacement>?>(null)

    private var grab = Offset.Zero
    private var base: List<GridPlacement> = emptyList()
    private var hoverKey: String? = null
    private var hoverSince = 0L
    private var lastCell: GridCell? = null

    val topLeft: Offset get() = pointer - grab

    fun start(placement: GridPlacement, at: Offset, layout: List<GridPlacement>, geometry: GridGeometry) {
        base = layout
        key = placement.key
        pointer = at
        grab = at - geometry.rectOf(placement).topLeft
        preview = layout
        lastCell = GridCell(placement.x, placement.y)
    }

    /** Follows the finger; says whether the landing spot changed or a merge became ready (for haptics). */
    fun moveTo(at: Offset, geometry: GridGeometry, now: Long): DragEvent {
        pointer = at
        val dragging = key ?: return DragEvent.None
        val over = mergeCandidate(at, geometry)
        if (over != null) {
            // Aiming at an item's middle: hold still and, after a moment, offer to merge.
            if (over.key != hoverKey) {
                hoverKey = over.key
                hoverSince = now
                mergeTarget = null
            }
            return tick(now)
        }
        hoverKey = null
        mergeTarget = null
        val cell = geometry.nearestCell(topLeft)
        if (cell == lastCell) return DragEvent.None
        lastCell = cell
        preview = HomeGrid.move(base, dragging, cell, geometry.columns)
        return DragEvent.Moved
    }

    /** Re-checks a resting finger: the merge happens once it has stayed on an item's middle. */
    fun tick(now: Long): DragEvent {
        val target = hoverKey ?: return DragEvent.None
        if (mergeTarget != target && now - hoverSince >= MERGE_DWELL_MS) {
            mergeTarget = target
            preview = base
            return DragEvent.MergeReady
        }
        return DragEvent.None
    }

    /** Ends the drag; returns the merge (dragged, target) or the new layout, or null if nothing changed. */
    fun end(geometry: GridGeometry): DragResult? {
        val dragging = key ?: return null
        val merge = mergeTarget
        val result = when {
            merge != null -> DragResult.Merge(base, dragging, merge)

            else -> HomeGrid.move(base, dragging, geometry.nearestCell(topLeft), geometry.columns)
                .takeIf { it != base }
                ?.let(DragResult::Moved)
        }
        pending = when (result) {
            is DragResult.Merge -> base.filter { it.key != dragging }
            is DragResult.Moved -> result.layout
            null -> null
        }
        key = null
        preview = null
        mergeTarget = null
        hoverKey = null
        return result
    }

    private fun mergeCandidate(at: Offset, geometry: GridGeometry): GridPlacement? {
        val dragging = key ?: return null
        // Apps and shortcuts go into folders; folders and widgets don't.
        if (HomeGrid.widgetId(dragging) != null || ItemKeys.folderId(dragging) != null) return null
        return base.firstOrNull { p ->
            p.key != dragging && p.width == 1 && p.height == 1 && HomeGrid.widgetId(p.key) == null &&
                geometry.rectOf(p).contains(at) && geometry.isInMiddle(p, at)
        }
    }
}

private enum class DragEvent { None, Moved, MergeReady }

private sealed interface DragResult {
    data class Moved(val layout: List<GridPlacement>) : DragResult
    data class Merge(val layout: List<GridPlacement>, val dragged: String, val target: String) : DragResult
}

@Composable
private fun ItemGrid(
    cells: Map<String, GridCell>,
    items: List<HomeItem>,
    widgets: List<ThemeWidget>,
    swipeUpConnection: NestedScrollConnection,
    onOpenItem: (HomeItem, Rect?) -> Unit,
    itemActions: (HomeItem) -> List<AppAction>,
    onLayoutChange: (List<GridPlacement>) -> Unit,
    onMerge: (List<GridPlacement>, String, String) -> Unit,
    onEmptyLongPress: () -> Unit,
) {
    val res = LocalResources.current
    val wallpaper = LocalWallpaperContent.current
    val labelStyle = MaterialTheme.typography.labelMedium.onWallpaper(wallpaper)
    val menuState = remember { AppMenuState() }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val layout = LocalHomeLayout.current
    val drag = remember { GridDragState() }
    var openWidgetMenu by remember { mutableStateOf<Int?>(null) }
    var resizing by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val labelHeight = if (!layout.showLabels) {
            0f
        } else {
            with(density) {
                val line = if (labelStyle.lineHeight.isSp) labelStyle.lineHeight else labelStyle.fontSize * 1.4f
                TilePadding.labelGap.value + line.toDp().value
            }
        }
        val metrics = GridMetrics(
            gridPaddingHorizontal = GRID_PADDING_H * 2f,
            gridPaddingVertical = GRID_PADDING_V * 2f,
            rowSpacing = ROW_SPACING,
            tilePaddingHorizontal = TilePadding.horizontal.value * 2,
            tilePaddingVertical = TilePadding.vertical.value * 2,
            labelHeight = labelHeight,
        )
        val columns = layout.columns
        val widthDp = maxWidth.value
        // A little slack so rounding never tips the grid into scrolling.
        val heightDp = maxHeight.value - 2f
        // Rows at the user's icon size: how many fit decides where unplaced items go.
        val naturalRowDp = metrics.rowHeight(layout.iconSize.value)
        val screenRows = max(1, floor((heightDp - metrics.gridPaddingVertical) / naturalRowDp).toInt())

        val entries = remember(cells, items, widgets, columns, naturalRowDp) {
            val itemEntries = items.map { GridEntry(it.key, cells[it.key]) }
            val widgetEntries = widgets.map { w ->
                GridEntry(
                    key = HomeGrid.widgetKey(w.appWidgetId),
                    cell = if (w.cellX != null && w.cellY != null) GridCell(w.cellX, w.cellY) else null,
                    width = w.spanX ?: columns,
                    // Widgets from before the grid had cells were sized in dp.
                    height = w.spanY ?: max(1, ((w.heightDp + WIDGET_CHROME_DP) / naturalRowDp).roundToInt()),
                )
            }
            // Unplaced widgets go above unplaced apps, whichever end the apps fill from.
            if (layout.arrangement.fromBottom) itemEntries + widgetEntries else widgetEntries + itemEntries
        }
        // Right-to-left fill is mirrored again in a right-to-left language.
        val fillRightToLeft = layout.arrangement.rightToLeft != (LocalLayoutDirection.current == LayoutDirection.Rtl)
        val base = remember(entries, columns, screenRows, layout.arrangement, fillRightToLeft) {
            HomeGrid.resolve(entries, columns, screenRows, layout.arrangement.fromBottom, fillRightToLeft)
        }
        // A saved layout stands in until the theme's cells come back from the database.
        LaunchedEffect(entries) { drag.pending = null }
        val settled = drag.pending ?: base
        val shown = drag.preview ?: settled

        // "Fit to the screen" keeps the columns and shrinks icons until the rows in use fit.
        val iconDp = if (layout.autoFit) {
            fitIconSize(max(1, HomeGrid.rowsUsed(settled)), columns, widthDp, heightDp, metrics, maxIconDp = LauncherPrefs.AUTO_FIT_MAX_ICON_DP).toFloat()
        } else {
            layout.iconSize.value
        }
        val rowDp = metrics.rowHeight(iconDp)
        val cellWidthDp = (widthDp - metrics.gridPaddingHorizontal) / columns
        val visibleRows = max(1, floor((heightDp - metrics.gridPaddingVertical) / rowDp).toInt())
        val externalDrag = LocalExternalDrag.current
        // While dragging, a spare row below everything takes drops too.
        val spareRow = if (drag.key != null || externalDrag?.isDragging == true) 1 else 0
        val rows = max(visibleRows, HomeGrid.rowsUsed(shown)) + spareRow
        val geometry = with(density) {
            GridGeometry(columns, cellWidthDp.dp.toPx(), rowDp.dp.toPx(), GRID_PADDING_H.dp.toPx(), GRID_PADDING_V.dp.toPx())
        }
        val current by rememberUpdatedState(settled to geometry)

        // Apps carried from the drawer land in a cell, or onto an item's middle.
        val viewport = remember { arrayOf(ComposeRect.Zero) }
        val contentOrigin = remember { arrayOf(Offset.Zero) }
        if (externalDrag != null) {
            DisposableEffect(externalDrag) {
                val resolver: (String, Offset) -> HomeDrop? = resolver@{ appKey, window ->
                    if (!viewport[0].contains(window)) return@resolver null
                    val (now, g) = current
                    val at = window - contentOrigin[0]
                    val into = now.firstOrNull { p ->
                        p.key != appKey && p.width == 1 && p.height == 1 && HomeGrid.widgetId(p.key) == null &&
                            g.rectOf(p).contains(at) && g.isInMiddle(p, at)
                    }
                    if (into != null) HomeDrop.Into(into.key) else HomeDrop.At(HomeGrid.insert(now, appKey, g.cellUnder(at), g.columns))
                }
                externalDrag.gridDrop = resolver
                onDispose { if (externalDrag.gridDrop === resolver) externalDrag.gridDrop = null }
            }
        }

        val scroll = rememberScrollState()
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewport[0] = it.boundsInWindow() }
                .nestedScroll(swipeUpConnection)
                .verticalScroll(scroll),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((GRID_PADDING_V * 2 + rows * rowDp).dp)
                    .onGloballyPositioned { contentOrigin[0] = it.positionInWindow() }
                    .gridGestures(
                        drag = drag,
                        current = { current },
                        onDragStart = {
                            menuState.close()
                            openWidgetMenu = null
                        },
                        onEvent = { event ->
                            when (event) {
                                DragEvent.Moved -> haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                DragEvent.MergeReady -> haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                DragEvent.None -> Unit
                            }
                        },
                        onDrop = { result ->
                            when (result) {
                                is DragResult.Moved -> onLayoutChange(result.layout)
                                is DragResult.Merge -> onMerge(result.layout, result.dragged, result.target)
                            }
                        },
                        onEmptyLongPress = onEmptyLongPress,
                    ),
            ) {
                // Where the dragged item (or an app carried from the drawer) would land.
                val landing = when {
                    drag.key != null && drag.mergeTarget == null -> shown.firstOrNull { it.key == drag.key }

                    externalDrag?.isDragging == true -> {
                        val at = externalDrag.position - contentOrigin[0]
                        if (!viewport[0].contains(externalDrag.position)) {
                            null
                        } else {
                            val cell = geometry.cellUnder(at)
                            val overMiddle = shown.any { p ->
                                p.width == 1 && p.height == 1 && HomeGrid.widgetId(p.key) == null && geometry.rectOf(p).contains(at) && geometry.isInMiddle(p, at)
                            }
                            if (overMiddle) null else GridPlacement("", cell.x, cell.y)
                        }
                    }

                    else -> null
                }
                if (landing != null) {
                    val r = geometry.rectOf(landing)
                    with(density) {
                        Box(
                            Modifier
                                .absoluteOffset { r.topLeft.round() }
                                .size(r.width.toDp(), r.height.toDp())
                                .padding(4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.18f)),
                        )
                    }
                }

                val itemsByKey = items.associateBy { it.key }
                val widgetsByKey = widgets.associateBy { HomeGrid.widgetKey(it.appWidgetId) }
                shown.forEach { p ->
                    key(p.key) {
                        val rect = geometry.rectOf(p)
                        val slot by animateIntOffsetAsState(rect.topLeft.round(), tween(220, easing = FastOutSlowInEasing), label = "cell")
                        val lifted = drag.key == p.key
                        val merging = drag.mergeTarget != null
                        val cellModifier = with(density) {
                            Modifier
                                .absoluteOffset { if (lifted) drag.topLeft.round() else slot }
                                .size(rect.width.toDp(), rect.height.toDp())
                                .zIndex(if (lifted) 1f else 0f)
                                .graphicsLayer {
                                    // The merge target swells to take the dragged item, which shrinks toward it.
                                    val scale = when {
                                        lifted && merging -> 0.85f
                                        lifted -> 1.08f
                                        drag.mergeTarget == p.key -> 1.18f
                                        else -> 1f
                                    }
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = if (lifted) 0.92f else 1f
                                }
                        }
                        // Screen readers can't drag; these move the item one cell instead.
                        val moves = moveActions(res, p, columns) { to -> onLayoutChange(HomeGrid.move(settled, p.key, to, columns)) }
                        val item = itemsByKey[p.key]
                        val widget = widgetsByKey[p.key]
                        when {
                            item != null -> Box(cellModifier, contentAlignment = Alignment.Center) {
                                HomeItemTile(
                                    accessibilityActions = moves,
                                    item = item,
                                    labelStyle = labelStyle,
                                    onClick = { onOpenItem(item, it) },
                                    actions = { itemActions(item) },
                                    menuState = menuState,
                                    iconSize = iconDp.dp,
                                    showLabel = layout.showLabels,
                                    // A light tonal tile, like the system launcher's folders, readable on any wallpaper.
                                    folderBackground = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            widget != null -> HomeWidget(
                                widget = widget,
                                menuOpen = openWidgetMenu == widget.appWidgetId,
                                onMenuOpenChange = { open -> openWidgetMenu = if (open) widget.appWidgetId else null },
                                onResize = { resizing = p.key },
                                accessibilityActions = moves,
                                modifier = cellModifier,
                            )
                        }
                    }
                }
            }
        }

        val resizingPlacement = resizing?.let { k -> settled.firstOrNull { it.key == k } }
        if (resizingPlacement != null) {
            WidgetResizeDialog(
                width = resizingPlacement.width,
                height = resizingPlacement.height,
                maxWidth = columns,
                maxHeight = MAX_WIDGET_ROWS,
                onChange = { w, h ->
                    val resized = HomeGrid.resize(settled, resizingPlacement.key, w, h, columns)
                    drag.pending = resized
                    onLayoutChange(resized)
                },
                onDismiss = { resizing = null },
            )
        }
    }
}

/**
 * Long-press then drag moves an item or widget; a long press without moving still opens its own
 * menu. Long-pressing empty space calls [onEmptyLongPress]. Watches in the Initial pass and
 * consumes nothing until a drag starts, so taps, menus and scrolling behave as without it.
 */
private fun Modifier.gridGestures(
    drag: GridDragState,
    current: () -> Pair<List<GridPlacement>, GridGeometry>,
    onDragStart: () -> Unit,
    onEvent: (DragEvent) -> Unit,
    onDrop: (DragResult) -> Unit,
    onEmptyLongPress: () -> Unit,
): Modifier = pointerInput(drag) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val (layout, geometry) = current()
        val hit = layout.firstOrNull { geometry.rectOf(it).contains(down.position) }

        val heldStill = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: true
        if (!heldStill) return@awaitEachGesture
        if (hit == null) {
            onEmptyLongPress()
            return@awaitEachGesture
        }

        var dragging = false
        while (true) {
            // Wake up now and then while the finger rests, so the merge dwell can complete.
            val event = withTimeoutOrNull(TICK_MS) { awaitPointerEvent(PointerEventPass.Initial) }
            if (event == null) {
                if (dragging) onEvent(drag.tick(nowMillis()))
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) change.consume()
                break
            }
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                onDragStart()
                drag.start(hit, down.position, layout, geometry)
            }
            if (dragging) {
                change.consume()
                onEvent(drag.moveTo(change.position, current().second, nowMillis()))
            }
        }
        if (dragging) drag.end(current().second)?.let(onDrop)
    }
}

private fun moveActions(res: Resources, p: GridPlacement, columns: Int, moveTo: (GridCell) -> Unit): List<CustomAccessibilityAction> {
    fun action(label: Int, to: GridCell) = CustomAccessibilityAction(res.getString(label)) {
        moveTo(to)
        true
    }
    return listOfNotNull(
        action(R.string.move_left, GridCell(p.x - 1, p.y)).takeIf { p.x > 0 },
        action(R.string.move_right, GridCell(p.x + 1, p.y)).takeIf { p.right < columns },
        action(R.string.move_up, GridCell(p.x, p.y - 1)).takeIf { p.y > 0 },
        action(R.string.move_down, GridCell(p.x, p.y + 1)),
    )
}

/** Monotonic milliseconds. */
private fun nowMillis(): Long = System.nanoTime() / 1_000_000

private const val GRID_PADDING_H = 12f
private const val GRID_PADDING_V = 8f
private const val ROW_SPACING = 4f
private const val MIDDLE_FRACTION = 0.3f
private const val MERGE_DWELL_MS = 350L
private const val TICK_MS = 60L

/** The tallest a widget can be made, in rows. */
private const val MAX_WIDGET_ROWS = 8

/** A widget's padding plus the old grid's row gap, for converting old dp heights to rows. */
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
