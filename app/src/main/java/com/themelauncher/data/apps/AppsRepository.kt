package com.themelauncher.data.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** A package was added, changed, removed or became (un)available for [user]. */
data class PackageEvent(val packageName: String, val user: UserHandle)

/**
 * Source of truth for installed launchable apps, backed by [LauncherApps].
 * Reloads automatically whenever the system reports a package change.
 */
class AppsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** null until the first load completes. Sorted alphabetically by label. */
    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private val _packageEvents = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 64)
    val packageEvents: SharedFlow<PackageEvent> = _packageEvents.asSharedFlow()

    /** Kept so icons can be loaded later without another system query. */
    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()

    private val reloadRequests = MutableStateFlow(0)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName, user)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName, user)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { changed(it, user) }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { changed(it, user) }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { changed(it, user) }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { changed(it, user) }

        private fun changed(packageName: String, user: UserHandle) {
            _packageEvents.tryEmit(PackageEvent(packageName, user))
            reload()
        }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch(Dispatchers.IO) {
            // collectLatest drops a stale load if another change arrives mid-query.
            reloadRequests.collectLatest { _apps.value = queryApps() }
        }
    }

    fun reload() = reloadRequests.update { it + 1 }

    fun activityInfo(key: String): LauncherActivityInfo? = activityInfos[key]

    /** Returns false if the app could not be started (e.g. it was just uninstalled). */
    fun launch(app: AppInfo, sourceBounds: Rect?): Boolean = try {
        launcherApps.startMainActivity(app.componentName, app.user, sourceBounds, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not launch ${app.key}", e)
        reload()
        false
    }

    fun openAppInfo(app: AppInfo, sourceBounds: Rect?) {
        runCatching { launcherApps.startAppDetailsActivity(app.componentName, app.user, sourceBounds, null) }
            .onFailure { Log.w(TAG, "Could not open app info for ${app.key}", it) }
    }

    private fun queryApps(): List<AppInfo> {
        val myUser = Process.myUserHandle()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val infos = HashMap<String, LauncherActivityInfo>()

        val apps = userManager.userProfiles.flatMap { user ->
            val isMain = user == myUser
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filterNot { it.componentName.packageName == context.packageName }
                .map { info ->
                    val key = AppInfo.keyFor(info.componentName, serial, isMain)
                    infos[key] = info
                    AppInfo(key = key, label = info.label.toString().trim(), componentName = info.componentName, user = user)
                }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }

        activityInfos.keys.retainAll(infos.keys)
        activityInfos.putAll(infos)
        return apps
    }

    private companion object {
        const val TAG = "AppsRepository"
    }
}
