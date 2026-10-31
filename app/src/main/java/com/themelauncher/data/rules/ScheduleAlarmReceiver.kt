package com.themelauncher.data.rules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.themelauncher.appContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fires when a schedule rule starts or ends. Delivering it starts the launcher's process if
 * Android had stopped it, and the auto switcher (started with the app) applies the schedule.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Touching the container creates it (and starts the auto switcher) if the process was cold.
        val container = context.appContainer
        // Keep the process up long enough for the switch to be saved.
        val pending = goAsync()
        container.appScope.launch {
            delay(GRACE_MS)
            pending.finish()
        }
    }

    private companion object {
        const val GRACE_MS = 3_000L
    }
}
