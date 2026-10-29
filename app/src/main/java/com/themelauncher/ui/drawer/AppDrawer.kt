package com.themelauncher.ui.drawer

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.themelauncher.data.apps.ProfileState
import com.themelauncher.data.apps.ProfileType
import com.themelauncher.ui.components.AppAction
import com.themelauncher.ui.components.AppTileWithMenu
import com.themelauncher.ui.components.drawerLabelStyle
import kotlinx.coroutines.delay

private val CloseDragThreshold = 96.dp

@Composable
fun AppDrawer(
    state: DrawerState,
    query: String,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    appActions: (AppInfo) -> List<AppAction>,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectProfile: (ProfileType) -> Unit,
    onSetProfileQuiet: (ProfileState, Boolean) -> Unit,
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
                    onOpenSettings = onOpenSettings,
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { state.apps.firstOrNull()?.let { onLaunch(it, null) } },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (state.profiles.size > 1 && query.isBlank()) {
                    ProfileTabs(state, onSelectProfile)
                }
            }

            state.selectedProfileState?.takeIf { query.isBlank() && it.type != ProfileType.Personal }?.let { profile ->
                ProfileBanner(profile, onSetProfileQuiet)
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.apps.isEmpty() -> Text(
                        text = if (query.isBlank()) "No apps here" else "No apps match “$query”",
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
                            .nestedScroll(pullToClose)
                            // Dim a paused work profile's apps, like the stock launcher.
                            .alpha(if (query.isBlank() && state.selectedProfileState?.isQuiet == true) 0.45f else 1f),
                    ) {
                        items(state.apps, key = { it.key }) { app ->
                            AppTileWithMenu(
                                app = app,
                                labelStyle = drawerLabelStyle(),
                                onClick = { bounds -> onLaunch(app, bounds) },
                                actions = {
                                    keyboard?.hide()
                                    appActions(app)
                                },
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
    onOpenSettings: () -> Unit,
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
            } else {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Launcher settings")
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

@Composable
private fun ProfileTabs(state: DrawerState, onSelect: (ProfileType) -> Unit) {
    val selectedIndex = state.profiles.indexOfFirst { it.type == state.selectedProfile }.coerceAtLeast(0)
    SecondaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        state.profiles.forEach { profile ->
            Tab(
                selected = profile.type == state.selectedProfile,
                onClick = { onSelect(profile.type) },
                text = { Text(profile.type.title) },
                icon = { Icon(profile.type.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
    }
}

/** Work profile paused / private space locked, with the button to change it. */
@Composable
private fun ProfileBanner(profile: ProfileState, onSetQuiet: (ProfileState, Boolean) -> Unit) {
    val (text, action) = when {
        profile.type == ProfileType.Work && profile.isQuiet -> "Work apps are paused" to "Turn on"
        profile.type == ProfileType.Work -> null to "Pause work apps"
        profile.isQuiet -> "Private space is locked" to "Unlock"
        else -> null to "Lock private space"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (text != null) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(start = 16.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (text != null) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        TextButton(onClick = { onSetQuiet(profile, !profile.isQuiet) }) { Text(action) }
    }
}

private val ProfileType.title: String
    get() = when (this) {
        ProfileType.Personal -> "Personal"
        ProfileType.Work -> "Work"
        ProfileType.Private -> "Private"
    }

private val ProfileType.icon: ImageVector
    get() = when (this) {
        ProfileType.Personal -> Icons.Outlined.Person
        ProfileType.Work -> Icons.Outlined.Work
        ProfileType.Private -> Icons.Outlined.Lock
    }
