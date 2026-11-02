package com.phaseslauncher.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.phaseslauncher.R
import com.phaseslauncher.data.widgets.LauncherWidgetHostView
import com.phaseslauncher.data.widgets.ThemeWidget
import com.phaseslauncher.data.widgets.WidgetRepository

/** What home-screen widgets need from the launcher. */
class WidgetHostUi(
    val repository: WidgetRepository,
    val onRemove: (ThemeWidget) -> Unit,
)

val LocalWidgetHost = staticCompositionLocalOf<WidgetHostUi?> { null }

/**
 * A hosted app widget filling the cells it's given. Long-press it for its menu (resize, remove);
 * long-press and drag moves it, which the home grid handles.
 */
@Composable
fun HomeWidget(
    widget: ThemeWidget,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val host = LocalWidgetHost.current ?: return
    val density = LocalDensity.current
    var hostView by remember { mutableStateOf<LauncherWidgetHostView?>(null) }

    Box(modifier.padding(horizontal = 4.dp, vertical = 4.dp).semantics { customActions = accessibilityActions }) {
        AndroidView(
            factory = { context ->
                val info = host.repository.info(widget.appWidgetId)
                (host.repository.host.createView(context, widget.appWidgetId, info) as LauncherWidgetHostView).apply {
                    onLongPress = { onMenuOpenChange(true) }
                    hostView = this
                }
            },
            update = { view -> view.onLongPress = { onMenuOpenChange(true) } },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                // Tell the widget how much room it has so it can pick a fitting layout.
                .onSizeChanged { size ->
                    val view = hostView ?: return@onSizeChanged
                    with(density) { host.repository.updateSize(view, size.width.toDp().value.toInt(), size.height.toDp().value.toInt()) }
                },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }, shape = RoundedCornerShape(16.dp)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.resize)) },
                leadingIcon = { Icon(Icons.Outlined.OpenInFull, contentDescription = null) },
                onClick = { onMenuOpenChange(false); onResize() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_widget)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { onMenuOpenChange(false); host.onRemove(widget) },
            )
        }
    }
}

/** Width and height in grid cells, changed a step at a time; each change applies right away. */
@Composable
fun WidgetResizeDialog(
    width: Int,
    height: Int,
    maxWidth: Int,
    maxHeight: Int,
    onChange: (width: Int, height: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resize_widget)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper(
                    label = stringResource(R.string.width),
                    value = width,
                    range = 1..maxWidth,
                    less = stringResource(R.string.narrower),
                    more = stringResource(R.string.wider),
                    onChange = { onChange(it, height) },
                )
                Stepper(
                    label = stringResource(R.string.height),
                    value = height,
                    range = 1..maxHeight,
                    less = stringResource(R.string.shorter),
                    more = stringResource(R.string.taller),
                    onChange = { onChange(width, it) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, less: String, more: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Rounded.Remove, contentDescription = less)
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Rounded.Add, contentDescription = more)
        }
    }
}
