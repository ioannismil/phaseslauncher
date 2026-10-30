package com.themelauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.themelauncher.data.settings.DateFormatOption
import com.themelauncher.ui.theme.LocalWallpaperContent
import com.themelauncher.ui.theme.onWallpaper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Time, with the date and battery level underneath; each part is optional. Ticks on the
 * minute while visible; tapping opens the clock app.
 */
@Composable
fun ClockHeader(
    showTime: Boolean,
    showDate: Boolean,
    dateFormat: DateFormatOption,
    showBattery: Boolean,
    alignment: Alignment.Horizontal,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    Column(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClick = onLongPress,
        ) {
            try {
                context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        },
        horizontalAlignment = alignment,
    ) {
        if (showTime) {
            Text(
                text = DateFormat.format(timePattern, now).toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 72.sp)
                    .onWallpaper(wallpaperColors),
            )
        }
        if (showDate || showBattery) {
            val lineStyle = MaterialTheme.typography.titleMedium.onWallpaper(wallpaperColors)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDate) Text(formatDate(dateFormat, now), style = lineStyle)
                if (showDate && showBattery) Spacer(Modifier.width(14.dp))
                if (showBattery) {
                    val battery = rememberBatteryState()
                    Icon(
                        if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        contentDescription = if (battery.charging) "Charging" else "Battery",
                        tint = wallpaperColors.content,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("${battery.percent}%", style = lineStyle)
                }
            }
        }
    }
}

/** Today's date in [option]'s format, localized. Also used for previews in settings. */
fun formatDate(option: DateFormatOption, millis: Long = System.currentTimeMillis()): String {
    val locale = Locale.getDefault()
    // SimpleDateFormat, not DateFormat.format: the latter reads a single "y" as a 2-digit year.
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, option.skeleton), locale).format(Date(millis))
}

private data class BatteryState(val percent: Int, val charging: Boolean)

/** The battery level and charging state, updated as they change (no permission needed). */
@Composable
private fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(0, false)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                state = intent.toBatteryState() ?: state
            }
        }
        // ACTION_BATTERY_CHANGED is sticky: registering returns the current state right away.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.toBatteryState()?.let { state = it }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private fun Intent.toBatteryState(): BatteryState? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryState(level * 100 / scale, charging)
}
