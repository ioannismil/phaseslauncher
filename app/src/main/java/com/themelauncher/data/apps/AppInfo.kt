package com.themelauncher.data.apps

import android.content.ComponentName
import android.os.UserHandle

/** Which user profile an app belongs to. Private is Android 15's Private Space. */
enum class ProfileType { Personal, Work, Private }

/** A user profile and whether it is paused (work) or locked (private). */
data class ProfileState(val user: UserHandle, val type: ProfileType, val isQuiet: Boolean)

/**
 * A launchable activity. [key] is stable across restarts and reboots, so it is what
 * themes and favorites store to reference an app.
 */
data class AppInfo(
    val key: String,
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle,
    /** False for apps preinstalled in the system image (unless the user installed an update). */
    val canUninstall: Boolean = true,
    /** The app's declared [android.content.pm.ApplicationInfo] category, or -1 if none. */
    val category: Int = -1,
    val profile: ProfileType = ProfileType.Personal,
) {
    val packageName: String get() = componentName.packageName

    companion object {
        /** Main-profile apps use the plain component; other profiles get a "#serial" suffix. */
        fun keyFor(componentName: ComponentName, userSerial: Long, isMainProfile: Boolean): String {
            val base = componentName.flattenToShortString()
            return if (isMainProfile) base else "$base#$userSerial"
        }

        fun packageOf(key: String): String = key.substringBefore('/')

        /** Splits a key into its component and user serial (null for the main profile). */
        fun parseKey(key: String): Pair<ComponentName, Long?>? {
            val component = ComponentName.unflattenFromString(key.substringBefore('#')) ?: return null
            val serial = key.substringAfter('#', missingDelimiterValue = "").toLongOrNull()
            return component to serial
        }
    }
}
