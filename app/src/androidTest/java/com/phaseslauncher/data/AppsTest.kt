package com.phaseslauncher.data

import android.content.ComponentName
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.apps.filterApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsTest {

    private fun app(label: String, pkg: String = "com.example.${label.lowercase().filter(Char::isLetter)}") =
        AppInfo(key = "$pkg/.Main", label = label, componentName = ComponentName(pkg, "$pkg.Main"), user = Process.myUserHandle())

    @Test
    fun keysRoundTrip() {
        val component = ComponentName("com.example", "com.example.ui.Main")
        val main = AppInfo.keyFor(component, userSerial = 0, isMainProfile = true)
        val work = AppInfo.keyFor(component, userSerial = 10, isMainProfile = false)

        assertEquals(component to null, AppInfo.parseKey(main))
        assertEquals(component to 10L, AppInfo.parseKey(work))
        assertEquals("com.example", AppInfo.packageOf(work))
        assertNull(AppInfo.parseKey("garbage"))
    }

    @Test
    fun searchPutsWordPrefixMatchesFirstAndIgnoresAccents() {
        val apps = listOf(app("Authenticator"), app("Cámara"), app("Google Maps"), app("Mapster"), app("Ελλάδα"))

        assertEquals(listOf("Google Maps", "Mapster"), filterApps(apps, "map").map { it.label })
        assertEquals(listOf("Cámara"), filterApps(apps, "camara").map { it.label })
        assertEquals(listOf("Ελλάδα"), filterApps(apps, "ελλα").map { it.label })
        assertEquals(listOf("Authenticator"), filterApps(apps, "then").map { it.label })
        assertEquals(apps, filterApps(apps, "  "))
    }
}
