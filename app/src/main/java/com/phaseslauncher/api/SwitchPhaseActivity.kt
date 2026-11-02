package com.phaseslauncher.api

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.phaseslauncher.R
import com.phaseslauncher.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Switches phases from a shortcut ("Switch to Work") or a `phases://switch?phase=Work` link, then
 * closes without showing anything. This is the way in for apps that open shortcuts or activities
 * rather than send broadcasts, such as Samsung Modes and Routines and Google Assistant.
 *
 * It doesn't need the setting the broadcast does: Android doesn't let apps in the background start
 * activities, so this only runs from something on screen or from a system automation app.
 */
class SwitchPhaseActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        val request = intent
        val app = applicationContext
        container.appScope.launch {
            val failure = when (val result = PhaseApi.switch(container, request)) {
                is PhaseApi.Result.Switched -> {
                    PhaseShortcuts.reportUsed(app, result.theme)
                    null
                }

                PhaseApi.Result.NoPhaseGiven -> app.getString(R.string.no_phase_named_in_the_request)

                is PhaseApi.Result.NotFound -> when (val target = result.target) {
                    is PhaseApi.Target.Name -> app.getString(R.string.no_phase_called, target.name)
                    is PhaseApi.Target.Id -> app.getString(R.string.that_phase_no_longer_exists)
                }
            }
            if (failure != null) withContext(Dispatchers.Main) { Toast.makeText(app, failure, Toast.LENGTH_SHORT).show() }
        }
        // Theme.NoDisplay: finish before onResume.
        finish()
    }
}
