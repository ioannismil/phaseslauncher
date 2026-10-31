package com.themelauncher.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "com.themelauncher"

/** Home after a start, then the drawer: open it, scroll it, search, and back home. */
fun MacrobenchmarkScope.browseHomeAndDrawer() {
    device.waitForIdle()
    val width = device.displayWidth
    val height = device.displayHeight
    // Swipe up on home opens the drawer.
    device.swipe(width / 2, height * 4 / 5, width / 2, height / 4, 12)
    device.wait(Until.hasObject(androidx.test.uiautomator.By.pkg(PACKAGE)), 2_000)
    device.waitForIdle()
    repeat(3) {
        device.swipe(width / 2, height * 4 / 5, width / 2, height / 5, 8)
        device.waitForIdle()
    }
    repeat(3) {
        device.swipe(width / 2, height / 5, width / 2, height * 4 / 5, 8)
        device.waitForIdle()
    }
    device.findObject(androidx.test.uiautomator.By.clazz("android.widget.EditText"))?.let { search ->
        search.text = "ca"
        device.waitForIdle()
    }
    device.pressBack()
    device.pressBack()
    device.waitForIdle()
    // Scroll home's grid a little, as on a theme with many apps.
    device.findObject(androidx.test.uiautomator.By.scrollable(true))?.scroll(Direction.DOWN, 0.5f)
    device.waitForIdle()
}
