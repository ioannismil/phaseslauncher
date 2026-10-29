package com.themelauncher.data.apps

import android.content.ComponentName
import android.os.UserHandle

/**
 * A launchable activity. [key] is stable across restarts and reboots, so it is what
 * themes and favorites store to reference an app.
 */
data class AppInfo(
    val key: String,
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle,
) {
    val packageName: String get() = componentName.packageName

    companion object {
        /** Main-profile apps use the plain component; other profiles get a "#serial" suffix. */
        fun keyFor(componentName: ComponentName, userSerial: Long, isMainProfile: Boolean): String {
            val base = componentName.flattenToShortString()
            return if (isMainProfile) base else "$base#$userSerial"
        }
    }
}
