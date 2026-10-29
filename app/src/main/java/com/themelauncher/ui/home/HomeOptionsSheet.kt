package com.themelauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.unit.dp
import com.themelauncher.ui.onboarding.rememberDefaultHomeState

/** Opened by long-pressing empty space on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeOptionsSheet(
    onNewTheme: () -> Unit,
    onRunSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaultHome = rememberDefaultHomeState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "ThemeLauncher",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Option(Icons.Rounded.Add, "New theme") {
                onDismiss()
                onNewTheme()
            }
            Option(Icons.Outlined.Wallpaper, "Wallpaper") {
                onDismiss()
                try {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: ActivityNotFoundException) {
                }
            }
            if (!defaultHome.isDefault) {
                Option(Icons.Outlined.Home, "Set as default home app") {
                    onDismiss()
                    defaultHome.request()
                }
            }
            Option(Icons.Outlined.Restore, "Run setup again", "Pick favorites and add themes from presets") {
                onDismiss()
                onRunSetup()
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
