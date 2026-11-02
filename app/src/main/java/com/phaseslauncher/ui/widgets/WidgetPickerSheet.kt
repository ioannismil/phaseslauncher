package com.phaseslauncher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.normalizedForSearch
import com.phaseslauncher.data.widgets.WidgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class WidgetOption(val info: AppWidgetProviderInfo, val label: String, val owner: String)

/** All installed widgets grouped by app, with previews. Picking one adds it to the active theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    themeName: String,
    loadProviders: () -> List<AppWidgetProviderInfo>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val options by produceState(emptyList<WidgetOption>()) {
        value = withContext(Dispatchers.IO) {
            loadProviders().map { WidgetOption(it, WidgetRepository.labelOf(context, it), WidgetRepository.ownerLabel(context, it)) }
                .sortedWith(compareBy({ it.owner.lowercase() }, { it.label.lowercase() }))
        }
    }
    var query by remember { mutableStateOf("") }
    val grouped = remember(options, query) {
        val q = query.trim().normalizedForSearch()
        options
            .filter { q.isEmpty() || it.owner.normalizedForSearch().contains(q) || it.label.normalizedForSearch().contains(q) }
            .groupBy { it.owner }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight().navigationBarsPadding()) {
            Text(stringResource(R.string.add_a_widget_to, themeName), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_widgets)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                grouped.forEach { (owner, widgets) ->
                    item(key = owner) {
                        Text(
                            owner,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(widgets, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { option ->
                                WidgetCard(option) { onPick(option.info) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(option: WidgetOption, onClick: () -> Unit) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, option.info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val density = context.resources.displayMetrics.densityDpi
                (option.info.loadPreviewImage(context, density) ?: option.info.loadIcon(context, density))
                    ?.let { d ->
                        val w = d.intrinsicWidth.coerceIn(1, 600)
                        val h = d.intrinsicHeight.coerceIn(1, 600)
                        d.toBitmap(w, h).asImageBitmap()
                    }
            }.getOrNull()
        }
    }
    Column(
        Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(8.dp)) }
        }
        Text(
            option.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
    }
}
