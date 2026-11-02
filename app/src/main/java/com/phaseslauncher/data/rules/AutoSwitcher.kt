package com.phaseslauncher.data.rules

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
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
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import androidx.core.content.ContextCompat
import com.phaseslauncher.data.settings.SettingsRepository
import com.phaseslauncher.data.themes.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** A paired Bluetooth device, for picking a rule's trigger. */
data class BluetoothDeviceInfo(val address: String, val name: String)

/** A theme that wants to switch in, waiting for the user's OK (the theme's "Ask first" setting). */
data class AutoPrompt(val themeId: Long, val rule: ThemeRule)

/**
 * Switches themes automatically from each theme's rules (see [RuleEngine] for which theme wins
 * when several apply, and each theme's [ThemeAutomation] for how it switches).
 *
 * It acts only on changes: when a theme's conditions start applying it switches to it
 * (remembering the theme you were on), and when they stop it switches back if the theme says so,
 * unless you've moved on meanwhile. Switching manually while a rule applies is respected until
 * the rules change again.
 */
class AutoSwitcher(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val connectedBluetooth = MutableStateFlow<Set<String>>(emptySet())
    private val controllerConnected = MutableStateFlow(false)
    private val wifiSsid = MutableStateFlow<String?>(null)
    private val headphonesConnected = MutableStateFlow(false)
    private val charging = MutableStateFlow(false)

    val calendar = CalendarWatcher(context)
    val location = LocationWatcher(context)

    private val conditions: Flow<Conditions> =
        combine(
            combine(connectedBluetooth, controllerConnected, wifiSsid, headphonesConnected, charging) { bt, controller, ssid, headphones, charge ->
                Conditions(bt, controller, ssid, headphones, charge)
            },
            location.fix,
            calendar.events,
        ) { c, fix, events -> c.copy(location = fix, events = events) }

    /** The theme the rules currently want, or null. Exposed for the UI. */
    private val _ruleTheme = MutableStateFlow<Long?>(null)
    val ruleTheme: StateFlow<Long?> = _ruleTheme.asStateFlow()

    private val _prompt = MutableStateFlow<AutoPrompt?>(null)
    /** A switch waiting for the user's OK; shown on the home screen. */
    val prompt: StateFlow<AutoPrompt?> = _prompt.asStateFlow()

    /** When each theme's conditions started holding, for [ThemeAutomation.delayMinutes]. */
    private val heldSince = HashMap<Long, Long>()

    /** A prompt the user turned down; not asked again until the rules want something else. */
    @Volatile private var declined: Long? = null

    @Volatile private var latestThemes: List<Theme> = emptyList()
    private var scope: CoroutineScope? = null

    private val minuteTicks: Flow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay(((60 - now.second) * 1000L).coerceAtLeast(1000L))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, themes: Flow<List<Theme>>) {
        this.scope = scope
        watchBluetooth()
        watchControllers()
        watchWifi()
        watchHeadphones()
        watchCharging()
        // Location and the calendar are only watched while some theme has such a rule.
        scope.launch(Dispatchers.Main) {
            themes.map { all -> all.any { t -> t.activeRules.any { it is ThemeRule.Location } } }
                .distinctUntilChanged()
                .collect(location::setActive)
        }
        scope.launch {
            themes.map { all -> all.any { t -> t.activeRules.any { it is ThemeRule.Calendar } } }
                .distinctUntilChanged()
                .collect { calendar.setActive(scope, it) }
        }
        val started = System.currentTimeMillis()
        // The clock only ticks while something depends on it: schedules, calendar events or a delay.
        val clock = themes.map { all -> all.any { it.needsClock() } }.distinctUntilChanged().flatMapLatest { needed ->
            if (needed) minuteTicks else flowOf(LocalDateTime.now())
        }
        scope.launch {
            combine(themes, clock, conditions) { all, now, conditions ->
                latestThemes = all
                // The minute ticks stop if Android stops the launcher; an alarm restarts it for the next change.
                scheduleWakeUp(all, now, conditions.events)
                winner(all, now, conditions)
            }
                // Only the theme counts, not which of its rules explains it, so a change of reason doesn't re-trigger.
                .distinctUntilChanged { a, b -> a?.themeId == b?.themeId }
                // "Nothing applies" waits a moment: right after start the connections and the calendar are
                // still being read, and a network or device can drop for a few seconds. Anything that
                // applies again meanwhile cancels the wait.
                .transformLatest { target ->
                    if (target == null) {
                        val sinceStart = System.currentTimeMillis() - started
                        delay(if (sinceStart < STARTUP_GRACE_MS) STARTUP_GRACE_MS - sinceStart else END_GRACE_MS)
                    }
                    emit(target)
                }
                .distinctUntilChanged { a, b -> a?.themeId == b?.themeId }
                .collect { target ->
                    _ruleTheme.value = target?.themeId
                    onRuleThemeChanged(target)
                }
        }
    }

    private fun Theme.needsClock(): Boolean =
        automation.delayMinutes > 0 || activeRules.any { it is ThemeRule.Schedule || it is ThemeRule.Calendar }

    /** The user said yes to [prompt]. */
    fun acceptPrompt() {
        val prompt = _prompt.value ?: return
        _prompt.value = null
        scope?.launch { switchTo(prompt.themeId) }
    }

    /** The user said no to [prompt]; it isn't asked again until the rules change. */
    fun dismissPrompt() {
        declined = _prompt.value?.themeId
        _prompt.value = null
    }

    /** Call after the location permission is granted (place rules, Wi-Fi names). */
    fun onLocationGranted() {
        location.restart()
        if (latestThemes.any { t -> t.activeRules.any { it is ThemeRule.Location } }) location.setActive(true)
        refreshWifi()
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

    private fun winner(themes: List<Theme>, now: LocalDateTime, c: Conditions): ThemeMatch? {
        val nowMillis = System.currentTimeMillis()
        val matching = RuleEngine.matches(themes, now, nowMillis, c)
        // A theme with a delay waits until its conditions have held long enough.
        val ids = matching.mapTo(HashSet()) { it.themeId }
        heldSince.keys.retainAll(ids)
        matching.forEach { heldSince.getOrPut(it.themeId) { nowMillis } }
        val byId = themes.associateBy { it.id }
        val ready = matching.filter { m ->
            val delay = (byId[m.themeId]?.automation?.delayMinutes ?: 0) * 60_000L
            nowMillis - (heldSince[m.themeId] ?: nowMillis) >= delay
        }
        return RuleEngine.pick(themes, ready)
    }

    // region Wake-up for schedules and calendar events

    /** Sets an alarm for the next time any schedule or calendar rule starts or ends (or cancels it). */
    private fun scheduleWakeUp(themes: List<Theme>, now: LocalDateTime, events: List<CalendarEvent>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = RuleEngine.nextChangeMillis(themes, now, System.currentTimeMillis(), events) {
            it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        if (at == null) {
            alarms.cancel(intent)
            return
        }
        // Inexact (no special permission), within a minute; the switch is seen when the phone is next used anyway.
        runCatching { alarms.setWindow(AlarmManager.RTC, at, ALARM_WINDOW_MS, intent) }
            .onFailure { Log.w(TAG, "Could not set the schedule alarm", it) }
    }

    // endregion

    // region Wi-Fi

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The connected Wi-Fi network's name, if known (needs the location permission). */
    fun currentWifiSsid(): String? = wifiSsid.value ?: legacySsid()

    /** Call after the location permission is granted so the network name becomes visible. */
    fun refreshWifi() {
        wifiCallback?.let { runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }
        wifiCallback = null
        watchWifi()
    }

    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    private fun watchWifi() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    wifiSsid.value = (caps.transportInfo as? WifiInfo)?.ssid?.cleanSsid()
                }

                override fun onLost(network: Network) {
                    wifiSsid.value = null
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    wifiSsid.value = legacySsid()
                }

                override fun onLost(network: Network) {
                    wifiSsid.value = null
                }
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { connectivity.registerNetworkCallback(request, callback) }
            .onSuccess { wifiCallback = callback }
            .onFailure { Log.w(TAG, "Could not watch Wi-Fi", it) }
    }

    @Suppress("DEPRECATION")
    private fun legacySsid(): String? =
        runCatching { context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid?.cleanSsid() }.getOrNull()

    /** Android quotes network names and hides them as "<unknown ssid>" without permission. */
    private fun String.cleanSsid(): String? = removeSurrounding("\"").takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }

    // endregion

    // region Headphones and charging

    private fun watchHeadphones() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val update = {
            headphonesConnected.value = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
        }
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = update()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = update()
        }, Handler(Looper.getMainLooper()))
        update()
    }

    private fun watchCharging() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                charging.value = intent.action == Intent.ACTION_POWER_CONNECTED
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The current state comes from the sticky battery broadcast.
        val battery = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        charging.value = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    // endregion

    private suspend fun onRuleThemeChanged(target: ThemeMatch?) {
        // A pending question is about the previous state of things.
        if (_prompt.value?.themeId != target?.themeId) _prompt.value = null
        if (declined != target?.themeId) declined = null
        if (target != null) {
            val theme = latestThemes.firstOrNull { it.id == target.themeId }
            if (settings.activeThemeId.first() == target.themeId) return
            if (theme?.automation?.askFirst == true) {
                if (declined != target.themeId) _prompt.value = AutoPrompt(target.themeId, target.rule)
                return
            }
            switchTo(target.themeId)
        } else {
            endAutoSwitch()
        }
    }

    private suspend fun switchTo(themeId: Long) {
        val current = settings.activeThemeId.first()
        if (current == themeId) return
        // Remember where to come back to; if we were already auto-switched, keep the original.
        val returnTo = settings.autoSwitchState()?.first ?: current
        if (returnTo != null) settings.setAutoSwitchState(returnTo to themeId)
        settings.setActiveThemeId(themeId)
    }

    /** The conditions ended: switch back, unless the theme stays or you've moved on. */
    private suspend fun endAutoSwitch() {
        val (returnTo, autoTheme) = settings.autoSwitchState() ?: return
        val stays = latestThemes.firstOrNull { it.id == autoTheme }?.automation?.onEnd == AutoEndAction.Stay
        if (!stays && settings.activeThemeId.first() == autoTheme) settings.setActiveThemeId(returnTo)
        settings.setAutoSwitchState(null)
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
        const val ALARM_WINDOW_MS = 60_000L

        /** How long after start a "nothing applies" waits for the connections to be read. */
        const val STARTUP_GRACE_MS = 12_000L

        /** How long conditions must stay off before switching back. */
        const val END_GRACE_MS = 20_000L

        val HEADPHONE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
