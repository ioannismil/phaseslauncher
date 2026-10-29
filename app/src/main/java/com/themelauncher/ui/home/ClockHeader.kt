package com.themelauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.onWallpaper
import kotlinx.coroutines.delay
import java.util.Locale

/** Time and date. Ticks on the minute while visible; tapping opens the clock app. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wallpaperColors = LocalWallpaperContent.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000)
            }
        }
    }

    // Re-read on every tick so 12/24h and locale changes apply without a restart.
    val locale = Locale.getDefault()
    val timePattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
        .replace("a", "").trim()
    val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")

    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            try {
                context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        },
    ) {
        Text(
            text = DateFormat.format(timePattern, now).toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 72.sp)
                .onWallpaper(wallpaperColors),
        )
        Text(
            text = DateFormat.format(datePattern, now).toString(),
            style = MaterialTheme.typography.titleMedium.onWallpaper(wallpaperColors),
        )
    }
}
