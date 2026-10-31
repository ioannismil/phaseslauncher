package com.themelauncher.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
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
data class PackageEvent(val packageName: String, val user: UserHandle, val removed: Boolean = false)

/**
 * Source of truth for installed launchable apps and user profiles, backed by [LauncherApps].
 * Reloads automatically whenever the system reports a package or profile change.
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

    /** The main profile first, then any work profile or private space. */
    private val _profiles = MutableStateFlow<List<ProfileState>>(emptyList())
    val profiles: StateFlow<List<ProfileState>> = _profiles.asStateFlow()

    private val _packageEvents = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 64)
    val packageEvents: SharedFlow<PackageEvent> = _packageEvents.asSharedFlow()

    /** Kept so icons can be loaded later without another system query. */
    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()

    private val reloadRequests = MutableStateFlow(0)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName, user)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName, user, removed = true)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName, user)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { changed(it, user) }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { changed(it, user) }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { changed(it, user) }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { changed(it, user) }

        private fun changed(packageName: String, user: UserHandle, removed: Boolean = false) {
            _packageEvents.tryEmit(PackageEvent(packageName, user, removed))
            reload()
        }
    }

    /** Work profile paused/resumed, private space locked/unlocked, profiles added/removed. */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val profileActions = buildList {
            add(Intent.ACTION_MANAGED_PROFILE_ADDED)
            add(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            add(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            add(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            add(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(Intent.ACTION_PROFILE_ADDED)
                add(Intent.ACTION_PROFILE_REMOVED)
                add(Intent.ACTION_PROFILE_AVAILABLE)
                add(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }
        ContextCompat.registerReceiver(
            context,
            profileReceiver,
            IntentFilter().apply { profileActions.forEach(::addAction) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch(Dispatchers.IO) {
            // collectLatest drops a stale load if another change arrives mid-query.
            reloadRequests.collectLatest { load() }
        }
    }

    fun reload() = reloadRequests.update { it + 1 }

    fun activityInfo(key: String): LauncherActivityInfo? = activityInfos[key]

    /**
     * Whether the app's package is still installed for its user. Disabled, suspended or
     * paused-profile apps count as installed. Blocking; call off the main thread.
     */
    fun isInstalled(key: String): Boolean {
        val (component, serial) = AppInfo.parseKey(key) ?: return false
        return isPackageInstalled(component.packageName, serial)
    }

    /** Like [isInstalled], for a package in the profile with [userSerial] (null for the main profile). */
    fun isPackageInstalled(packageName: String, userSerial: Long?): Boolean {
        val user = if (userSerial == null) Process.myUserHandle() else userManager.getUserForSerialNumber(userSerial)
        if (user == null) return false
        val key = "$packageName#$userSerial"
        return try {
            launcherApps.getApplicationInfo(packageName, 0, user)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            // e.g. a locked profile; keep the app rather than risk deleting the user's setup.
            Log.w(TAG, "Could not check $key", e)
            true
        }
    }

    /** Returns false if the app could not be started (e.g. it was just uninstalled). */
    fun launch(app: AppInfo, sourceBounds: Rect?, options: Bundle? = null): Boolean = try {
        launcherApps.startMainActivity(app.componentName, app.user, sourceBounds, options)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not launch ${app.key}", e)
        reload()
        false
    }

    /** Shows the system uninstall confirmation. Removal from themes follows via the package callback. */
    fun requestUninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not uninstall ${app.key}", it) }
    }

    fun openAppInfo(app: AppInfo, sourceBounds: Rect?) {
        runCatching { launcherApps.startAppDetailsActivity(app.componentName, app.user, sourceBounds, null) }
            .onFailure { Log.w(TAG, "Could not open app info for ${app.key}", it) }
    }

    /**
     * Pauses/resumes the work profile or locks/unlocks the private space. Unlocking may show the
     * system's credential prompt. Requires being the default launcher.
     */
    fun setProfileQuiet(user: UserHandle, quiet: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching { userManager.requestQuietModeEnabled(quiet, user) }
            .onFailure { Log.w(TAG, "Could not change quiet mode", it) }
        reload()
    }

    private fun load() {
        val myUser = Process.myUserHandle()
        // LauncherApps (unlike UserManager) also returns the private space to the default launcher.
        val profiles = launcherApps.profiles
            .map { user -> ProfileState(user, profileType(user, myUser), user != myUser && isQuiet(user)) }
            .sortedBy { it.type.ordinal }
        _profiles.value = profiles
        _apps.value = queryApps(profiles, myUser)
    }

    private fun queryApps(profiles: List<ProfileState>, myUser: UserHandle): List<AppInfo> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val infos = HashMap<String, LauncherActivityInfo>()

        val apps = profiles
            // A locked private space must not reveal its apps.
            .filterNot { it.type == ProfileType.Private && it.isQuiet }
            .flatMap { profile ->
                val user = profile.user
                val isMain = user == myUser
                val serial = userManager.getSerialNumberForUser(user)
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                    .filterNot { it.componentName.packageName == context.packageName }
                    .map { info ->
                        val key = AppInfo.keyFor(info.componentName, serial, isMain)
                        infos[key] = info
                        val flags = info.applicationInfo.flags
                        val isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                        AppInfo(
                            key = key,
                            label = info.label.toString().trim(),
                            componentName = info.componentName,
                            user = user,
                            canUninstall = !isSystem,
                            category = info.applicationInfo.category,
                            profile = profile.type,
                            installTime = info.firstInstallTime,
                        )
                    }
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }

        activityInfos.keys.retainAll(infos.keys)
        activityInfos.putAll(infos)
        return apps
    }

    private fun profileType(user: UserHandle, myUser: UserHandle): ProfileType {
        if (user == myUser) return ProfileType.Personal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val userType = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            if (userType == UserManager.USER_TYPE_PROFILE_PRIVATE) return ProfileType.Private
        }
        return ProfileType.Work
    }

    private fun isQuiet(user: UserHandle): Boolean =
        runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)

    private companion object {
        const val TAG = "AppsRepository"
    }
}
