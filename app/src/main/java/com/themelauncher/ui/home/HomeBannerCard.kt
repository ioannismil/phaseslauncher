package com.themelauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.themelauncher.HomeBanner
import com.themelauncher.data.themes.Theme
import com.themelauncher.ui.theme.contentColorOn
import com.themelauncher.ui.theme.imageVector

/** A suggestion, or an automatic switch asking first, at the top of the home screen. */
@Composable
fun HomeBannerCard(
    banner: HomeBanner,
    theme: Theme?,
    onAccept: () -> Unit,
    onCustomize: () -> Unit,
    onDismiss: (forever: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (theme != null) {
                    val accent = Color(theme.accentColor)
                    Box(Modifier.size(36.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        Icon(theme.icon.imageVector, contentDescription = null, tint = contentColorOn(accent), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(banner.title, style = MaterialTheme.typography.titleSmall)
                    Text(banner.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!banner.isAutoPrompt) TextButton(onClick = { onDismiss(true) }) { Text("Never") }
                TextButton(onClick = { onDismiss(false) }) { Text("Not now") }
                if (banner.canCustomize) TextButton(onClick = onCustomize) { Text("Customize") }
                FilledTonalButton(onClick = onAccept, modifier = Modifier.padding(start = 4.dp)) { Text(banner.acceptLabel) }
            }
        }
    }
}
