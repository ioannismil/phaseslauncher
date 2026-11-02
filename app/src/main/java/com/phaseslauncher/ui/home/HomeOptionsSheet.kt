package com.phaseslauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phaseslauncher.R

/** Opened by long-pressing empty space on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeOptionsSheet(
    onOpenDrawer: () -> Unit,
    onNewTheme: () -> Unit,
    onAddWidget: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val res = LocalResources.current
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.phases),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            // Always reachable here, even with swipe up set to something else.
            Option(Icons.Rounded.Apps, stringResource(R.string.all_apps)) {
                onDismiss()
                onOpenDrawer()
            }
            Option(Icons.Rounded.Add, stringResource(R.string.new_theme)) {
                onDismiss()
                onNewTheme()
            }
            Option(Icons.Outlined.Widgets, stringResource(R.string.add_widget), stringResource(R.string.to_the_theme_you_re_on)) {
                onDismiss()
                onAddWidget()
            }
            Option(Icons.Outlined.Wallpaper, stringResource(R.string.wallpaper)) {
                onDismiss()
                try {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), res.getString(R.string.set_wallpaper))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: ActivityNotFoundException) {
                }
            }
            Option(Icons.Outlined.Settings, stringResource(R.string.launcher_settings), stringResource(R.string.theme_switcher_side_themes_setup)) {
                onDismiss()
                onOpenSettings()
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}
