package com.themelauncher.ui.drawer

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.themelauncher.DrawerState
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.ui.components.AppTile
import com.themelauncher.ui.components.drawerLabelStyle
import kotlinx.coroutines.delay

private val CloseDragThreshold = 96.dp

@Composable
fun AppDrawer(
    state: DrawerState,
    query: String,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val closeThresholdPx = with(LocalDensity.current) { CloseDragThreshold.toPx() }
    val currentOnClose = rememberUpdatedState(onClose)

    LaunchedEffect(Unit) {
        // Let the open animation start before the keyboard pushes the layout.
        delay(120)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // New query → jump back to the best match at the top.
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    // Pulling down past the top of the list closes the drawer; scrolling hides the keyboard.
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0) keyboard?.hide()
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0 && consumed.y == 0f) {
                    pulled += available.y
                    if (pulled > closeThresholdPx) {
                        pulled = 0f
                        currentOnClose.value()
                    }
                } else {
                    pulled = 0f
                }
                return Offset.Zero
            }
        }
    }

    // The header (handle + search bar) can also be dragged down to close.
    val headerDrag = remember { mutableFloatStateOf(0f) }
    val headerDragState = rememberDraggableState { delta -> headerDrag.floatValue += delta }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = headerDragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { headerDrag.floatValue = 0f },
                        onDragStopped = { velocity ->
                            if (headerDrag.floatValue > closeThresholdPx || velocity > 1500f) onClose()
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                )
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { state.apps.firstOrNull()?.let { onLaunch(it, null) } },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.apps.isEmpty() -> Text(
                        text = "No apps match “$query”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        state = gridState,
                        contentPadding = WindowInsets.navigationBars.union(WindowInsets.ime)
                            .only(WindowInsetsSides.Bottom).asPaddingValues(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .nestedScroll(pullToClose),
                    ) {
                        items(state.apps, key = { it.key }) { app ->
                            AppTile(
                                app = app,
                                labelStyle = drawerLabelStyle(),
                                onClick = { bounds -> onLaunch(app, bounds) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSearch() }),
    )
}
