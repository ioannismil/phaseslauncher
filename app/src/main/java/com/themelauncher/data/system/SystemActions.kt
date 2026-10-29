package com.themelauncher.data.system

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/** System UI actions that have no public API but every launcher relies on. */
object SystemActions {

    /**
     * Pulls down the notification shade, like swiping down on the stock home screen.
     * Uses StatusBarManager.expandNotificationsPanel (needs EXPAND_STATUS_BAR).
     */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context): Boolean = try {
        val statusBar = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
        true
    } catch (e: Exception) {
        Log.w("SystemActions", "Could not expand notifications", e)
        false
    }
}
