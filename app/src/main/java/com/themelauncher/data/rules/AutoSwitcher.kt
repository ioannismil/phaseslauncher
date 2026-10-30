package com.themelauncher.data.rules

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import androidx.core.content.ContextCompat
import com.themelauncher.data.settings.SettingsRepository
import com.themelauncher.data.themes.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** A paired Bluetooth device, for picking a rule's trigger. */
data class BluetoothDeviceInfo(val address: String, val name: String)

/**
 * Switches themes automatically from each theme's rules: a game controller connecting, a
 * Bluetooth device connecting, or a time schedule (that priority order when several apply).
 *
 * It acts only on changes: when a rule starts applying it switches to its theme (remembering the
 * theme you were on), and when it stops it switches back, unless you've moved on meanwhile.
 * Switching manually while a rule applies is respected until the rule changes again.
 */
class AutoSwitcher(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val connectedBluetooth = MutableStateFlow<Set<String>>(emptySet())
    private val controllerConnected = MutableStateFlow(false)

    /** The theme the rules currently want, or null. Exposed for the UI. */
    private val _ruleTheme = MutableStateFlow<Long?>(null)
    val ruleTheme: StateFlow<Long?> = _ruleTheme.asStateFlow()

    private val minuteTicks: Flow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay(((60 - now.second) * 1000L).coerceAtLeast(1000L))
        }
    }

    fun start(scope: CoroutineScope, themes: Flow<List<Theme>>) {
        watchBluetooth()
        watchControllers()
        scope.launch {
            combine(themes, minuteTicks, connectedBluetooth, controllerConnected) { all, now, bluetooth, controller ->
                winner(all, now, bluetooth, controller)
            }
                .distinctUntilChanged()
                .collect { target ->
                    _ruleTheme.value = target
                    onRuleThemeChanged(target)
                }
        }
    }

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked by hasBluetoothPermission().
    fun pairedDevices(): List<BluetoothDeviceInfo> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.map { BluetoothDeviceInfo(it.address, it.name ?: it.address) }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Call after the Bluetooth permission is granted to pick up already-connected devices. */
    fun refreshBluetooth() = scanConnectedBluetooth()

    private fun winner(themes: List<Theme>, now: LocalDateTime, bluetooth: Set<String>, controller: Boolean): Long? {
        fun firstMatching(predicate: (ThemeRule) -> Boolean) = themes.firstOrNull { theme -> theme.rules.any(predicate) }?.id
        return (if (controller) firstMatching { it is ThemeRule.Controller } else null)
            ?: firstMatching { it is ThemeRule.Bluetooth && it.address in bluetooth }
            ?: firstMatching { it is ThemeRule.Schedule && it.isActive(now) }
    }

    private suspend fun onRuleThemeChanged(target: Long?) {
        val current = settings.activeThemeId.first()
        val state = settings.autoSwitchState()
        if (target != null) {
            if (current == target) return
            // Remember where to come back to; if we were already auto-switched, keep the original.
            val returnTo = state?.first ?: current
            if (returnTo != null) settings.setAutoSwitchState(returnTo to target)
            settings.setActiveThemeId(target)
        } else if (state != null) {
            val (returnTo, autoTheme) = state
            if (current == autoTheme) settings.setActiveThemeId(returnTo)
            settings.setAutoSwitchState(null)
        }
    }

    // region Bluetooth

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val address = runCatching { device.address }.getOrNull() ?: return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> connectedBluetooth.update { it + address }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> connectedBluetooth.update { it - address }
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) connectedBluetooth.value = emptySet()
            }
        }
    }

    private fun watchBluetooth() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scanConnectedBluetooth()
    }

    /** Devices already connected when the launcher starts (audio devices, e.g. a car or headphones). */
    @SuppressLint("MissingPermission")
    private fun scanConnectedBluetooth() {
        if (!hasBluetoothPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profile ->
            runCatching {
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        val addresses = runCatching { proxy.connectedDevices.map { it.address } }.getOrDefault(emptyList())
                        connectedBluetooth.update { it + addresses }
                        adapter.closeProfileProxy(p, proxy)
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                }, profile)
            }.onFailure { Log.w(TAG, "Bluetooth scan failed", it) }
        }
    }

    // endregion

    // region Game controllers

    private fun watchControllers() {
        val inputManager = context.getSystemService(InputManager::class.java) ?: return
        val update = { controllerConnected.value = anyController() }
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = update()
            override fun onInputDeviceRemoved(deviceId: Int) = update()
            override fun onInputDeviceChanged(deviceId: Int) = update()
        }, Handler(Looper.getMainLooper()))
        update()
    }

    private fun anyController(): Boolean = InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        !device.isVirtual &&
            (device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    // endregion

    private companion object {
        const val TAG = "AutoSwitcher"
    }
}
