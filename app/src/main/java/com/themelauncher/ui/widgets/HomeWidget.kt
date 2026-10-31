package com.themelauncher.ui.widgets

import com.themelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.themelauncher.data.widgets.LauncherWidgetHostView
import com.themelauncher.data.widgets.ThemeWidget
import com.themelauncher.data.widgets.WidgetRepository
import com.themelauncher.data.widgets.WidgetSize

/** What home-screen widgets need from the launcher. */
class WidgetHostUi(
    val repository: WidgetRepository,
    val onRemove: (ThemeWidget) -> Unit,
    val onResize: (ThemeWidget, Int) -> Unit,
    val onMove: (ThemeWidget, Int) -> Unit,
)

val LocalWidgetHost = staticCompositionLocalOf<WidgetHostUi?> { null }

/** A hosted app widget. Long-press it for size, order and removal. */
@Composable
fun HomeWidget(widget: ThemeWidget, canMoveUp: Boolean, canMoveDown: Boolean, modifier: Modifier = Modifier) {
    val host = LocalWidgetHost.current ?: return
    val density = LocalDensity.current
    var menuOpen by remember { mutableStateOf(false) }
    var hostView by remember { mutableStateOf<LauncherWidgetHostView?>(null) }

    Box(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        AndroidView(
            factory = { context ->
                val info = host.repository.info(widget.appWidgetId)
                (host.repository.host.createView(context, widget.appWidgetId, info) as LauncherWidgetHostView).apply {
                    onLongPress = { menuOpen = true }
                    hostView = this
                }
            },
            update = { view -> view.onLongPress = { menuOpen = true } },
            modifier = Modifier
                .fillMaxWidth()
                .height(widget.heightDp.dp)
                .clip(RoundedCornerShape(20.dp))
                // Tell the widget how much room it has so it can pick a fitting layout.
                .onSizeChanged { size ->
                    val view = hostView ?: return@onSizeChanged
                    with(density) { host.repository.updateSize(view, size.width.toDp().value.toInt(), size.height.toDp().value.toInt()) }
                },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(16.dp)) {
            WidgetSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(size.label) },
                    leadingIcon = { if (size.heightDp == widget.heightDp) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.current_size)) },
                    onClick = {
                        menuOpen = false
                        host.onResize(widget, size.heightDp)
                    },
                )
            }
            HorizontalDivider()
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null) },
                    onClick = { menuOpen = false; host.onMove(widget, -1) },
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                    onClick = { menuOpen = false; host.onMove(widget, 1) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_widget)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { menuOpen = false; host.onRemove(widget) },
            )
        }
    }
}
