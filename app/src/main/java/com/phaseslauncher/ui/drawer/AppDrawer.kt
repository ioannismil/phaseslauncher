package com.phaseslauncher.ui.drawer

import com.phaseslauncher.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.phaseslauncher.data.search.Calculation
import com.phaseslauncher.data.settings.DrawerSort
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
import androidx.compose.material.icons.outlined.TravelExplore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.phaseslauncher.DrawerState
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.ProfileState
import com.phaseslauncher.data.apps.ProfileType
import com.phaseslauncher.data.search.ContactResult
import com.phaseslauncher.data.search.SettingsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.phaseslauncher.ui.components.AppAction
import com.phaseslauncher.ui.components.AppTileWithMenu
import com.phaseslauncher.ui.components.LocalExternalDrag
import com.phaseslauncher.ui.components.dragToHome
import com.phaseslauncher.ui.components.drawerLabelStyle
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
    searchActions: DrawerSearchActions,
    /** Opened for searching: focus the search bar and show the keyboard. */
    openKeyboard: Boolean,
    onSortChange: (DrawerSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val closeThresholdPx = with(LocalDensity.current) { CloseDragThreshold.toPx() }
    val currentOnClose = rememberUpdatedState(onClose)

    LaunchedEffect(openKeyboard) {
        if (!openKeyboard) return@LaunchedEffect
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
        // Set explicitly: the translucent color matches no scheme color, so it can't be derived.
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    sort = state.sort,
                    onSortChange = onSortChange,
                    query = query,
                    onQueryChange = onQueryChange,
                    // Go: open the top app, or search the web when no app matches.
                    onSearch = {
                        val top = state.apps.firstOrNull()
                        if (top != null) onLaunch(top, null) else if (query.isNotBlank()) searchActions.onSearchWeb(query)
                    },
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
                    state.apps.isEmpty() && query.isBlank() -> Text(
                        text = stringResource(R.string.no_apps_here),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                    )
                    else -> {
                    // The A-Z rail: browsing by name with enough apps to need it.
                    val showRail = query.isBlank() && state.sort == DrawerSort.Name && state.apps.size > RAIL_MIN_APPS
                    val letters = remember(state.apps, showRail) { if (showRail) letterIndex(state.apps) else emptyList() }
                    val scope = rememberCoroutineScope()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        state = gridState,
                        contentPadding = WindowInsets.navigationBars.union(WindowInsets.ime)
                            .only(WindowInsetsSides.Bottom).asPaddingValues(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = if (showRail) RailWidth else 8.dp)
                            .nestedScroll(pullToClose)
                            // Dim a paused work profile's apps, like the stock launcher.
                            .alpha(if (query.isBlank() && state.selectedProfileState?.isQuiet == true) 0.45f else 1f),
                    ) {
                        state.calculation?.let { calculation ->
                            item(key = "calculation", span = { GridItemSpan(maxLineSpan) }) { CalculationCard(calculation) }
                        }
                        if (state.suggestions.isNotEmpty()) {
                            item(key = "suggestedHeader", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.suggested)) }
                            items(state.suggestions, key = { "suggested-" + it.key }) { app ->
                                AppTileWithMenu(
                                    app = app,
                                    labelStyle = drawerLabelStyle(),
                                    onClick = { bounds -> onLaunch(app, bounds) },
                                    actions = { appActions(app) },
                                    modifier = Modifier.animateItem().dragToHome(app, LocalExternalDrag.current),
                                )
                            }
                            item(key = "allHeader", span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel(if (state.sort == DrawerSort.Name) stringResource(R.string.all_apps) else "All apps \u00b7 ${state.sort.label.lowercase()}")
                            }
                        }
                        if (query.isNotBlank() && state.apps.isEmpty() && state.calculation == null) {
                            item(key = "noApps", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("No apps match \u201c$query\u201d") }
                        }
                        items(state.apps, key = { it.key }) { app ->
                            AppTileWithMenu(
                                app = app,
                                labelStyle = drawerLabelStyle(),
                                onClick = { bounds -> onLaunch(app, bounds) },
                                actions = {
                                    keyboard?.hide()
                                    appActions(app)
                                },
                                modifier = Modifier.animateItem().dragToHome(app, LocalExternalDrag.current),
                            )
                        }
                        if (query.isNotBlank()) {
                            searchExtras(state, query, searchActions)
                        }
                    }
                    if (showRail) {
                        // Grid items before the apps: the suggestions and their two headers.
                        val appsStart = if (state.suggestions.isNotEmpty()) state.suggestions.size + 2 else 0
                        AlphabetRail(
                            letters = letters.map { it.first },
                            onLetter = { letter ->
                                val index = letters.firstOrNull { it.first == letter }?.second ?: return@AlphabetRail
                                scope.launch { gridState.scrollToItem(appsStart + index) }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
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
    sort: DrawerSort,
    onSortChange: (DrawerSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_apps_contacts_settings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.drawer_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(16.dp)) {
                        Text(
                            stringResource(R.string.sort_apps_by),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        DrawerSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                                onClick = {
                                    menuOpen = false
                                    onSortChange(option)
                                },
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.launcher_settings)) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            },
                        )
                    }
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
        profile.type == ProfileType.Work && profile.isQuiet -> stringResource(R.string.work_apps_are_paused) to stringResource(R.string.turn_on)
        profile.type == ProfileType.Work -> null to stringResource(R.string.pause_work_apps)
        profile.isQuiet -> stringResource(R.string.private_space_is_locked) to stringResource(R.string.unlock)
        else -> null to stringResource(R.string.lock_private_space)
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

/** Callbacks for the non-app search results. */
class DrawerSearchActions(
    val onOpenContact: (ContactResult) -> Unit,
    val onOpenSettings: (SettingsResult) -> Unit,
    val onSearchWeb: (String) -> Unit,
    val onAllowContacts: () -> Unit,
    val onDeclineContacts: () -> Unit,
)

/** Contacts, settings pages and a web search, below the matching apps. */
private fun LazyGridScope.searchExtras(state: DrawerState, query: String, actions: DrawerSearchActions) {
    if (state.contacts.isNotEmpty()) {
        item(key = "contactsHeader", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.contacts)) }
        items(state.contacts, key = { "contact-" + it.id }, span = { GridItemSpan(maxLineSpan) }) { contact ->
            ResultRow(
                leading = { ContactAvatar(contact) },
                title = contact.name,
                onClick = { actions.onOpenContact(contact) },
            )
        }
    }
    if (state.askContactsPermission) {
        item(key = "contactsPrompt", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_your_contacts_too),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onDeclineContacts) { Text(stringResource(R.string.no_thanks)) }
                TextButton(onClick = actions.onAllowContacts) { Text(stringResource(R.string.allow)) }
            }
        }
    }
    if (state.settingsResults.isNotEmpty()) {
        item(key = "settingsHeader", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.settings)) }
        items(state.settingsResults, key = { "settings-" + it.action }, span = { GridItemSpan(maxLineSpan) }) { result ->
            ResultRow(
                leading = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = result.title,
                onClick = { actions.onOpenSettings(result) },
            )
        }
    }
    item(key = "web", span = { GridItemSpan(maxLineSpan) }) {
        ResultRow(
            leading = { Icon(Icons.Outlined.TravelExplore, contentDescription = null, modifier = Modifier.size(28.dp)) },
            title = "Search the web for \u201c${query.trim()}\u201d",
            onClick = { actions.onSearchWeb(query.trim()) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** "= result" for a search that's arithmetic or a unit conversion. Tap to copy the result. */
@Composable
private fun CalculationCard(calculation: Calculation) {
    val context = LocalContext.current
    Surface(
        onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Result", calculation.result))
            // Android 13+ shows its own confirmation.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Calculate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    calculation.expression,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "= ${calculation.result}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private const val RAIL_MIN_APPS = 20
private val RailWidth = 28.dp

/** Each first letter (# for anything else) and the index of its first app. */
private fun letterIndex(apps: List<AppInfo>): List<Pair<String, Int>> {
    val seen = LinkedHashMap<String, Int>()
    apps.forEachIndexed { index, app ->
        val first = app.label.firstOrNull()
        val letter = if (first != null && first.isLetter()) first.uppercase() else "#"
        seen.putIfAbsent(letter, index)
    }
    return seen.entries.map { it.key to it.value }
}

/**
 * Letters down the drawer's edge: drag along them (or tap one) to jump to the apps starting with
 * that letter. A bubble shows the letter beside the finger. Hidden from screen readers, which
 * scroll the list directly.
 */
@Composable
private fun AlphabetRail(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    if (letters.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var railHeight by remember { mutableIntStateOf(1) }
    val currentOnLetter by rememberUpdatedState(onLetter)

    fun pick(y: Float) {
        fingerY = y
        val letter = letters[(y / railHeight * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != active) {
            active = letter
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            currentOnLetter(letter)
        }
    }

    Box(modifier) {
        Column(
            Modifier
                .width(RailWidth)
                .padding(vertical = 8.dp)
                .onSizeChanged { railHeight = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        active = null
                    }
                }
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        active?.let { letter ->
            val bubble = 56.dp
            Box(
                Modifier
                    // Takes no room in the rail, so the letters don't shift while it shows.
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(bubble.roundToPx(), bubble.roundToPx()))
                        layout(0, 0) {
                            placeable.place(-(bubble + 12.dp).roundToPx(), (fingerY - placeable.height / 2f).roundToInt())
                        }
                    }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ResultRow(leading: @Composable () -> Unit, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ContactAvatar(contact: ContactResult) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, contact.photoUri) {
        value = contact.photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
            }
        }
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(
                contact.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
