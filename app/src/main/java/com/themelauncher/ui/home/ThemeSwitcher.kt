package com.themelauncher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

private val ActiveHeight = 52.dp
private val InactiveSize = 44.dp

data class ThemeAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * One round button per theme. The active theme grows into a filled pill showing its name;
 * the others are outlined circles in their accent color.
 */
@Composable
fun ThemeSwitcher(
    themes: List<Theme>,
    activeThemeId: Long?,
    onSelect: (Theme) -> Unit,
    themeActions: (Theme) -> List<ThemeAction>,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var menuThemeId by remember { mutableStateOf<Long?>(null) }

    // Keep the active theme in view, e.g. after creating one at the end of a long row.
    LaunchedEffect(activeThemeId, themes.size) {
        val index = themes.indexOfFirst { it.id == activeThemeId }
        if (index < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any {
            it.index == index && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset
        }
        if (!fullyVisible) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = modifier.height(ActiveHeight + 8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(themes, key = { it.id }) { theme ->
            Box(Modifier.animateItem()) {
                ThemeButton(
                    theme = theme,
                    active = theme.id == activeThemeId,
                    onClick = { onSelect(theme) },
                    onLongClick = { menuThemeId = theme.id },
                )
                DropdownMenu(
                    expanded = menuThemeId == theme.id,
                    onDismissRequest = { menuThemeId = null },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    themeActions(theme).forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            enabled = action.enabled,
                            onClick = {
                                menuThemeId = null
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
        item(key = "add") {
            AddThemeButton(onClick = onAdd, modifier = Modifier.animateItem())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeButton(
    theme: Theme,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(theme.accentColor)
    val wallpaper = LocalWallpaperContent.current
    val bouncy = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    val height by animateDpAsState(if (active) ActiveHeight else InactiveSize, bouncy, label = "height")
    val background by animateColorAsState(if (active) accent else wallpaper.scrim, label = "bg")
    val content by animateColorAsState(if (active) contentColorOn(accent) else accent, label = "content")

    Row(
        modifier = modifier
            .height(height)
            .widthIn(min = height)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, if (active) Color.Transparent else accent.copy(alpha = 0.9f), CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Tab,
                onLongClickLabel = "Theme options",
            )
            .semantics {
                selected = active
                contentDescription = theme.name
            }
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = if (active) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(if (active) 24.dp else InactiveSize), contentAlignment = Alignment.Center) {
            Icon(theme.icon.imageVector, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        AnimatedVisibility(
            visible = active,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(
                text = theme.name,
                color = content,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 160.dp),
            )
        }
    }
}

@Composable
private fun AddThemeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val wallpaper = LocalWallpaperContent.current
    Box(
        modifier = modifier
            .size(InactiveSize)
            .clip(CircleShape)
            .background(wallpaper.scrim)
            .border(1.5.dp, wallpaper.content.copy(alpha = 0.5f), CircleShape)
            .combinedClickable(onClick = onClick, role = Role.Button, onClickLabel = "New theme"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "New theme", tint = wallpaper.content)
    }
}
