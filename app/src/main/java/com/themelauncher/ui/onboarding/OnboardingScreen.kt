package com.themelauncher.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.filterApps
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.data.themes.ThemeIconKey
import com.themelauncher.ui.components.AppIcon
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

private enum class Step { Welcome, Favorites, Themes, DefaultHome }

/** A theme the user may create during setup: a preset (renamable) or a custom one. */
private class ThemeChoice(
    name: String,
    val icon: ThemeIconKey,
    val accentColor: Int,
    val suggested: List<AppInfo>,
    selected: Boolean,
) {
    var name by mutableStateOf(name)
    var selected by mutableStateOf(selected)
}

/**
 * First-launch setup: pick favorites, create the first themes (presets pre-filled with
 * matching installed apps), then ask to become the default home app.
 */
@Composable
fun OnboardingScreen(
    allApps: List<AppInfo>,
    initialFavorites: List<String>,
    onComplete: (favoriteKeys: List<String>, themes: List<ThemeDraft>) -> Unit,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    val favorites = remember { initialFavorites.toMutableStateList() }
    val choices = remember(allApps.isEmpty()) {
        ThemeDefaults.Presets.map { preset ->
            val suggested = preset.suggest(allApps)
            ThemeChoice(preset.name, preset.icon, preset.accentColor, suggested, selected = suggested.isNotEmpty())
        }.toMutableStateList()
    }

    // Back steps backwards; once themes are created there's nothing to undo.
    BackHandler(enabled = step == Step.Favorites || step == Step.Themes) {
        step = Step.entries[step.ordinal - 1]
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            val progress by animateFloatAsState((step.ordinal + 1f) / Step.entries.size, label = "progress")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (fadeIn() + slideInHorizontally { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut() + slideOutHorizontally { if (forward) -it / 6 else it / 6 })
                },
                label = "onboardingStep",
            ) { current ->
                when (current) {
                    Step.Welcome -> WelcomeStep()
                    Step.Favorites -> FavoritesStep(allApps, favorites)
                    Step.Themes -> ThemesStep(choices, onAddCustom = {
                        val color = ThemeDefaults.AccentColors[(choices.size * 3) % ThemeDefaults.AccentColors.size]
                        choices += ThemeChoice("My theme", ThemeIconKey.Star, color, emptyList(), selected = true)
                    })
                    Step.DefaultHome -> DefaultHomeStep()
                }
            }

            // Bottom bar: secondary action on the left, primary on the right.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (step) {
                    Step.Welcome -> TextButton(onClick = onSkip) { Text("Skip setup") }
                    Step.Favorites, Step.Themes -> TextButton(onClick = { step = Step.entries[step.ordinal - 1] }) { Text("Back") }
                    Step.DefaultHome -> Unit
                }
                Spacer(Modifier.weight(1f))
                when (step) {
                    Step.Welcome -> Button(onClick = { step = Step.Favorites }) { Text("Get started") }
                    Step.Favorites -> Button(onClick = { step = Step.Themes }) {
                        Text(if (favorites.isEmpty()) "Skip" else "Next")
                    }
                    Step.Themes -> {
                        val count = choices.count { it.selected && it.name.isNotBlank() }
                        Button(onClick = {
                            val drafts = choices.filter { it.selected && it.name.isNotBlank() }.map {
                                ThemeDraft(it.name.trim(), it.icon, it.accentColor, it.suggested.map { app -> app.key })
                            }
                            onComplete(favorites.toList(), drafts)
                            step = Step.DefaultHome
                        }) {
                            Text(
                                when (count) {
                                    0 -> "Skip"
                                    1 -> "Create theme"
                                    else -> "Create $count themes"
                                },
                            )
                        }
                    }
                    Step.DefaultHome -> Button(onClick = onFinished) { Text("Finish") }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A miniature of the theme switcher: three themes, one active.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeDefaults.Presets.forEachIndexed { index, preset ->
                val active = index == 1
                Box(
                    Modifier
                        .size(if (active) 64.dp else 48.dp)
                        .clip(CircleShape)
                        .background(Color(preset.accentColor).copy(alpha = if (active) 1f else 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        preset.icon.imageVector,
                        contentDescription = null,
                        tint = if (active) contentColorOn(Color(preset.accentColor)) else Color(preset.accentColor),
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Welcome to ThemeLauncher", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Group your apps into themes like Gaming or Work and switch between them with one tap. " +
                "Your favorites stay in the dock on every theme.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FavoritesStep(allApps: List<AppInfo>, favorites: MutableList<String>) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(allApps, query) { filterApps(allApps, query) }
    val byKey = remember(allApps) { allApps.associateBy { it.key } }
    val full = favorites.size >= ThemeDefaults.MAX_FAVORITES

    Column(Modifier.fillMaxSize()) {
        StepHeader(
            title = "Pick your favorites",
            body = "Up to ${ThemeDefaults.MAX_FAVORITES} apps for the dock. They stay put whichever theme you're on.",
        )
        // The dock as it will look, in order. Tap an icon to remove it.
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(ThemeDefaults.MAX_FAVORITES) { slot ->
                val app = favorites.getOrNull(slot)?.let(byKey::get)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (app != null) {
                        AppIcon(app, size = 44.dp, modifier = Modifier.clip(CircleShape).clickable { favorites -= app.key })
                    } else {
                        Box(
                            Modifier
                                .size(40.dp)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                    }
                }
            }
        }
        Text(
            text = "${favorites.size} of ${ThemeDefaults.MAX_FAVORITES}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
        )
        SearchField(query, { query = it }, Modifier.padding(horizontal = 24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(visible, key = { it.key }) { app ->
                val checked = app.key in favorites
                SelectableApp(
                    app = app,
                    checked = checked,
                    enabled = checked || !full,
                    onToggle = { if (checked) favorites -= app.key else if (!full) favorites += app.key },
                )
            }
        }
    }
}

@Composable
private fun ThemesStep(choices: MutableList<ThemeChoice>, onAddCustom: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            StepHeader(
                title = "Create your themes",
                body = "Start from these suggestions; rename them or untick the ones you don't need. " +
                    "You can change everything later by long-pressing a theme.",
            )
        }
        itemsIndexed(choices) { _, choice -> ThemeChoiceCard(choice) }
        item {
            TextButton(onClick = onAddCustom, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Add another theme")
            }
        }
    }
}

@Composable
private fun ThemeChoiceCard(choice: ThemeChoice) {
    val accent = Color(choice.accentColor)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (choice.selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = choice.selected, onCheckedChange = { choice.selected = it })
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(choice.icon.imageVector, contentDescription = null, tint = contentColorOn(accent))
                }
                Spacer(Modifier.size(12.dp))
                TextField(
                    value = choice.name,
                    onValueChange = { choice.name = it.take(24) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            if (choice.suggested.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(start = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(choice.suggested, key = { it.key }) { AppIcon(it, size = 32.dp) }
                }
                Text(
                    text = if (choice.suggested.size == 1) "1 suggested app" else "${choice.suggested.size} suggested apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, top = 6.dp),
                )
            } else {
                Text(
                    text = "No matching apps found — you can add some later",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DefaultHomeStep() {
    val defaultHome = rememberDefaultHomeState()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (defaultHome.isDefault) Icons.Rounded.CheckCircle else Icons.Rounded.Home,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (defaultHome.isDefault) {
            Text("You're all set", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "ThemeLauncher is your home app. Press Home any time to come back here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text("Make it your home screen", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Set ThemeLauncher as your default home app so the Home button brings you here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = defaultHome.request) { Text("Set as default home app") }
            TextButton(onClick = { openHomeSettings(context) }) { Text("Open system settings instead") }
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SelectableApp(app: AppInfo, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app, size = 52.dp, modifier = Modifier.alpha(if (enabled) 1f else 0.38f))
            if (checked) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
