package com.themelauncher.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.themelauncher.data.settings.SwitcherSide
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.onboarding.rememberDefaultHomeState
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

/** Launcher settings: switcher bar side, theme management, and home-screen options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themes: List<Theme>,
    switcherSide: SwitcherSide,
    onSwitcherSideChange: (SwitcherSide) -> Unit,
    onNewTheme: () -> Unit,
    onEditTheme: (Theme) -> Unit,
    onDeleteTheme: (Theme) -> Unit,
    onReorderThemes: (List<Long>) -> Unit,
    onRunSetup: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val defaultHome = rememberDefaultHomeState()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launcher settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Theme switcher") }
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        "Swipe from the bar on the screen edge toward the middle, then release on a theme. Tap the bar to pick with a tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Bar position", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SwitcherSide.entries.forEachIndexed { index, side ->
                            SegmentedButton(
                                selected = side == switcherSide,
                                onClick = { onSwitcherSideChange(side) },
                                shape = SegmentedButtonDefaults.itemShape(index, SwitcherSide.entries.size),
                            ) { Text(if (side == SwitcherSide.Left) "Left edge" else "Right edge") }
                        }
                    }
                }
            }

            item { SectionHeader("Themes") }
            itemsIndexed(themes, key = { _, theme -> theme.id }) { index, theme ->
                ThemeRow(
                    theme = theme,
                    canMoveUp = index > 0,
                    canMoveDown = index < themes.lastIndex,
                    canDelete = themes.size > 1,
                    onMove = { delta ->
                        val ids = themes.map { it.id }.toMutableList()
                        ids.add(index + delta, ids.removeAt(index))
                        onReorderThemes(ids)
                    },
                    onEdit = { onEditTheme(theme) },
                    onDelete = { onDeleteTheme(theme) },
                    modifier = Modifier.animateItem(),
                )
            }
            item { Option(Icons.Rounded.Add, "New theme", onClick = onNewTheme) }

            item { SectionHeader("Home screen") }
            item {
                Option(Icons.Outlined.Wallpaper, "Wallpaper") {
                    try {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }
            item {
                if (defaultHome.isDefault) {
                    Option(Icons.Rounded.CheckCircle, "Default home app", "ThemeLauncher is your home app", onClick = null)
                } else {
                    Option(Icons.Outlined.Home, "Set as default home app", "So the Home button opens ThemeLauncher", onClick = defaultHome.request)
                }
            }
            item { Option(Icons.Outlined.Restore, "Run setup again", "Pick favorites and add themes from presets", onClick = onRunSetup) }

            item { SectionHeader("About") }
            item { Option(Icons.Outlined.Info, "ThemeLauncher", versionName?.let { "Version $it" }, onClick = null) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun ThemeRow(
    theme: Theme,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(theme.accentColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit, onClickLabel = "Edit ${theme.name}")
            .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(theme.icon.imageVector, contentDescription = null, tint = contentColorOn(accent))
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (theme.appKeys.size == 1) "1 app" else "${theme.appKeys.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${theme.name} up")
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${theme.name} down")
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit ${theme.name}") }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${theme.name}")
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
    )
}
