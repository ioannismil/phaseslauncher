package com.themelauncher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import com.themelauncher.data.widgets.WidgetRepository

/**
 * Adds a widget the way the system requires: allocate an id, get permission to bind it (asking
 * the user the first time), run the widget's configuration screen if it has one, then place it.
 *
 * Results arrive via the activity's onActivityResult because AppWidgetHost only offers a
 * request-code based API for the configuration screen.
 */
class WidgetAddFlow(
    private val activity: Activity,
    private val repository: WidgetRepository,
    private val onPlaced: (appWidgetId: Int, info: AppWidgetProviderInfo) -> Unit,
    private val onFailed: (String) -> Unit,
) {
    private var pending: Pair<Int, AppWidgetProviderInfo>? = null

    fun start(info: AppWidgetProviderInfo) {
        val id = repository.allocateId()
        pending = id to info
        if (repository.bindIfAllowed(id, info)) {
            configureOrPlace()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_BIND)
        }
    }

    /** Returns true if the result belonged to this flow. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return false
        val (id, _) = pending ?: return true
        if (resultCode != Activity.RESULT_OK) {
            repository.discardId(id)
            pending = null
            if (requestCode == REQUEST_BIND) onFailed("Widget not added: permission wasn't granted")
            return true
        }
        if (requestCode == REQUEST_BIND) configureOrPlace() else place()
        return true
    }

    private fun configureOrPlace() {
        val (id, info) = pending ?: return
        val configurationOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure == null || configurationOptional) {
            place()
            return
        }
        try {
            repository.host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
        } catch (e: Exception) {
            Log.w("WidgetAddFlow", "Could not open widget configuration", e)
            place()
        }
    }

    private fun place() {
        val (id, info) = pending ?: return
        pending = null
        onPlaced(id, info)
    }

    private companion object {
        const val REQUEST_BIND = 0x51D1
        const val REQUEST_CONFIGURE = 0x51D2
    }
}
