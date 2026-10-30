package com.themelauncher.data.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Reads icon packs in the widely used ADW/Nova format: the pack declares a launcher-theme intent
 * filter and ships `appfilter.xml` mapping `ComponentInfo{pkg/cls}` to drawable names.
 */
class IconPackManager(private val context: Context) {

    private val pm = context.packageManager
    private val packs = ConcurrentHashMap<String, LoadedPack>()

    private class LoadedPack(val resources: Resources, val componentToDrawable: Map<String, String>, val allDrawables: List<String>)

    fun installedPacks(): List<IconPackInfo> =
        PACK_INTENTS
            .flatMap { intent -> runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching { IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }

    /** The pack's icon for [component], or null if the pack doesn't theme that app. Blocking. */
    fun iconFor(packPackage: String, component: ComponentName, densityDpi: Int): Drawable? {
        val pack = load(packPackage) ?: return null
        val name = pack.componentToDrawable["ComponentInfo{${component.packageName}/${component.className}}"]
            ?: pack.componentToDrawable["ComponentInfo{${component.flattenToShortString()}}"]
            ?: return null
        return drawable(pack, packPackage, name, densityDpi)
    }

    fun drawable(packPackage: String, name: String, densityDpi: Int): Drawable? {
        val pack = load(packPackage) ?: return null
        return drawable(pack, packPackage, name, densityDpi)
    }

    /** All icon names a pack offers, for picking one by hand. Blocking. */
    fun drawableNames(packPackage: String): List<String> = load(packPackage)?.allDrawables.orEmpty()

    fun clear() = packs.clear()

    @SuppressLint("DiscouragedApi") // Icon packs are addressed by resource name by design.
    private fun drawable(pack: LoadedPack, packPackage: String, name: String, densityDpi: Int): Drawable? {
        val id = pack.resources.getIdentifier(name, "drawable", packPackage)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawableForDensity(pack.resources, id, densityDpi, null) }.getOrNull()
    }

    private fun load(packPackage: String): LoadedPack? = packs[packPackage] ?: runCatching {
        val resources = pm.getResourcesForApplication(packPackage)
        val mapping = LinkedHashMap<String, String>()
        parse(resources, packPackage, "appfilter") { parser ->
            if (parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && !drawable.isNullOrBlank()) mapping[component] = drawable
            }
        }
        val names = LinkedHashSet<String>()
        parse(resources, packPackage, "drawable") { parser ->
            if (parser.name == "item") parser.getAttributeValue(null, "drawable")?.let(names::add)
        }
        if (names.isEmpty()) names += mapping.values
        LoadedPack(resources, mapping, names.toList().sorted()).also { packs[packPackage] = it }
    }.onFailure { Log.w(TAG, "Could not load icon pack $packPackage", it) }.getOrNull()

    /** Reads `res/xml/<name>.xml`, falling back to `assets/<name>.xml`. */
    @SuppressLint("DiscouragedApi")
    private fun parse(resources: Resources, packPackage: String, name: String, onStartTag: (XmlPullParser) -> Unit) {
        val xmlId = resources.getIdentifier(name, "xml", packPackage)
        val parser: XmlPullParser = if (xmlId != 0) {
            resources.getXml(xmlId)
        } else {
            val stream = runCatching { resources.assets.open("$name.xml") }.getOrNull() ?: return
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) onStartTag(parser)
            event = parser.next()
        }
    }

    companion object {
        private const val TAG = "IconPacks"

        /** Intents that icon packs declare so launchers can find them (also listed in <queries>). */
        val PACK_INTENTS = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("com.gau.go.launcherex.theme"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.teslacoilsw.launcher.THEME"),
        )
    }
}
