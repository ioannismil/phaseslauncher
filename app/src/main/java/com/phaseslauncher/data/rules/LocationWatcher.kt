package com.phaseslauncher.data.rules

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the phone is, for place rules. Listens (at a low rate, on the network/fused provider) only
 * while some theme has a place rule. Without background location, Android only delivers updates
 * while Phases is on screen.
 */
class LocationWatcher(private val context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)

    private val _fix = MutableStateFlow<GeoFix?>(null)
    val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    private var listening = false

    // Every method is overridden: before Android 11 they have no default implementations, and a
    // provider being switched on or off would otherwise crash the launcher.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _fix.value = location.toFix()
        }

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "Allow all the time": place rules also work while another app is open. */
    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Start or stop listening; called when place rules are added or removed, or the permission changes. */
    @SuppressLint("MissingPermission") // Checked by hasPermission().
    fun setActive(active: Boolean) {
        if (manager == null) return
        if (active && hasPermission()) {
            if (listening) return
            runCatching {
                val providers = manager.getProviders(true)
                val main = preferredProvider(providers)
                if (main != null) manager.requestLocationUpdates(main, UPDATE_MS, UPDATE_METERS, listener, Looper.getMainLooper())
                // Free rides on fixes other apps ask for.
                if (LocationManager.PASSIVE_PROVIDER in providers) {
                    manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, PASSIVE_MS, 0f, listener, Looper.getMainLooper())
                }
                lastKnown()?.let { _fix.value = it }
                listening = true
            }.onFailure { Log.w(TAG, "Could not listen for location", it) }
        } else if (!active && listening) {
            runCatching { manager.removeUpdates(listener) }
            listening = false
        }
    }

    /** Re-registers, e.g. after the permission was granted. */
    fun restart() {
        val was = listening
        setActive(false)
        if (was) setActive(true)
    }

    /** A fresh fix for "Use my current location", or the last known one. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): GeoFix? {
        if (manager == null || !hasPermission()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = preferredProvider(manager.getProviders(true)) ?: return lastKnown()
            val fresh = withTimeoutOrNull(CURRENT_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    runCatching { manager.getCurrentLocation(provider, cancel, Executor { it.run() }) { cont.resume(it) } }
                        .onFailure { cont.resume(null) }
                }
            }
            return fresh?.toFix() ?: lastKnown()
        }
        return lastKnown()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): GeoFix? = runCatching {
        manager?.getProviders(true)?.mapNotNull { manager.getLastKnownLocation(it) }?.maxByOrNull { it.time }?.toFix()
    }.getOrNull()

    private fun preferredProvider(enabled: List<String>): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled -> LocationManager.FUSED_PROVIDER
        LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
        LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
        else -> null
    }

    private fun Location.toFix() = GeoFix(latitude, longitude, if (hasAccuracy()) accuracy else 100f)

    private companion object {
        const val TAG = "LocationWatcher"
        const val UPDATE_MS = 2 * 60 * 1000L
        const val UPDATE_METERS = 50f
        const val PASSIVE_MS = 30 * 1000L
        const val CURRENT_TIMEOUT_MS = 15_000L
    }
}
