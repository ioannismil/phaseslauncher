package com.phaseslauncher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the baseline profile helps: cold start and drawer frame times without any ahead-of-time
 * compilation versus with the profile. Run with: ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *
 * While Phases is the home app (or its notification listener is on) Android restarts it the
 * moment it's stopped, so a cold start can't be measured; another installed launcher stands in as
 * home and the listener is switched off during the run, and both are restored after.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var previousHome: String? = null
    private var listenerWasOn = false

    @Before
    fun useAnotherHome() {
        previousHome = device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        ).lines().lastOrNull { "/" in it }?.trim()
        val other = device.executeShellCommand("pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map { it.trim() }
            .firstOrNull { "/" in it && !it.startsWith(PACKAGE) && !it.startsWith("com.android.settings") }
        if (other != null) device.executeShellCommand("cmd package set-home-activity $other")
        listenerWasOn = LISTENER in device.executeShellCommand("settings get secure enabled_notification_listeners")
        if (listenerWasOn) device.executeShellCommand("cmd notification disallow_listener $LISTENER")
    }

    @After
    fun restoreHome() {
        previousHome?.let { device.executeShellCommand("cmd package set-home-activity $it") }
        if (listenerWasOn) device.executeShellCommand("cmd notification allow_listener $LISTENER")
    }

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun drawerWithoutProfile() = drawer(CompilationMode.None())

    @Test
    fun drawerWithProfile() = drawer(CompilationMode.Partial(BaselineProfileMode.Require))

    private companion object {
        const val LISTENER = "$PACKAGE/$PACKAGE.data.notifications.NotificationDotsService"
    }

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private fun drawer(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        browseHomeAndDrawer()
    }
}
