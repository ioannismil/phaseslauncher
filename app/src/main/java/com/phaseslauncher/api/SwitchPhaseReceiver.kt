package com.phaseslauncher.api

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.phaseslauncher.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Switches phases on [PhaseApi.ACTION_SWITCH_PHASE] from Tasker and the like, without anything on
 * screen. Off until the user allows it, since a phase can silence the phone and hold back apps.
 *
 * Android only delivers broadcasts sent to this package (`-p com.phaseslauncher`, Tasker's
 * "Package" field) to manifest receivers. Ordered broadcasts get a result: [Activity.RESULT_OK]
 * with the phase's name, or [RESULT_DISABLED] / [RESULT_NOT_FOUND] with the reason.
 */
class SwitchPhaseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PhaseApi.ACTION_SWITCH_PHASE) return
        val container = context.appContainer
        // Read before goAsync(): afterwards the receiver no longer knows it's ordered.
        val ordered = isOrderedBroadcast
        val pending = goAsync()
        fun result(code: Int, data: String) {
            if (ordered) pending.setResult(code, data, null)
        }
        container.appScope.launch {
            try {
                if (!container.settingsRepository.prefs.first().allowExternalSwitching) {
                    Log.i(TAG, "Ignored: other apps aren't allowed to switch phases (Settings)")
                    result(RESULT_DISABLED, "Turn on \"Other apps can switch phases\" in Phases settings")
                    return@launch
                }
                when (val outcome = PhaseApi.switch(container, intent)) {
                    is PhaseApi.Result.Switched -> result(Activity.RESULT_OK, outcome.theme.name)

                    PhaseApi.Result.NoPhaseGiven ->
                        result(RESULT_NOT_FOUND, "Name the phase in the \"${PhaseApi.EXTRA_PHASE}\" extra")

                    is PhaseApi.Result.NotFound -> {
                        val reason = when (val target = outcome.target) {
                            is PhaseApi.Target.Name -> "No phase called \"${target.name}\""
                            is PhaseApi.Target.Id -> "No phase with id ${target.id}"
                        }
                        Log.i(TAG, reason)
                        result(RESULT_NOT_FOUND, reason)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "SwitchPhaseReceiver"
        const val RESULT_NOT_FOUND = Activity.RESULT_FIRST_USER
        const val RESULT_DISABLED = Activity.RESULT_FIRST_USER + 1
    }
}
