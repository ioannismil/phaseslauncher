package com.themelauncher.data.apps

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import com.themelauncher.data.db.AppCustomizationEntity
import com.themelauncher.data.icons.IconPackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Renders app icons off the main thread and keeps them in memory, so scrolling the
 * drawer or switching themes never waits on PackageManager.
 *
 * Icon source, in order: the icon picked for the app by hand, the active icon pack's icon,
 * the app's themed (monochrome) icon tinted with the theme color when themed icons are on, the
 * app's own icon. Pack and themed icons get the work-profile badge like the originals.
 */
class IconCache(
    private val context: Context,
    private val appsRepository: AppsRepository,
    private val iconPacks: IconPackManager,
) {

    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val density = context.resources.displayMetrics.density
    @Volatile private var iconSizePx = (DEFAULT_SIZE_DP * density).roundToInt()
    private val loadDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }

    @Volatile private var iconPack: String? = null
    @Volatile private var customIcons: Map<String, AppCustomizationEntity> = emptyMap()

    /** Accent color for themed icons, or null when they're off. */
    @Volatile private var themedTint: Int? = null
    @Volatile private var darkTheme = false

    /** Bumped when icons are invalidated so visible icons re-read the cache. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun peek(app: AppInfo): ImageBitmap? = cache.get(cacheKey(app.key))

    suspend fun load(app: AppInfo): ImageBitmap? {
        val key = cacheKey(app.key)
        return cache.get(key) ?: withContext(loadDispatcher) {
            cache.get(key) ?: render(app)?.also { cache.put(key, it) }
        }
    }

    suspend fun preload(apps: List<AppInfo>) = coroutineScope {
        apps.filter { peek(it) == null }.map { async { load(it) } }.awaitAll()
    }

    /**
     * Themed icons are cached per color, so switching back to a theme shows its icons at once.
     * Keys still start with the package, which [invalidate] relies on.
     */
    private fun cacheKey(appKey: String): String {
        val pack = iconPack?.let { "|p=$it" }.orEmpty()
        val tint = themedTint?.let { "|t=$it|$darkTheme" }.orEmpty()
        return appKey + pack + tint
    }

    fun invalidate(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { cache.remove(it) }
        _generation.update { it + 1 }
    }

    /** Applies a new icon pack and/or hand-picked icons; re-renders only if something changed. */
    fun setIconSources(iconPack: String?, customizations: Map<String, AppCustomizationEntity>) {
        val customIconsOnly = customizations.filterValues { it.iconPack != null && it.iconName != null }
        if (iconPack == this.iconPack && customIconsOnly == this.customIcons) return
        val packChanged = iconPack != this.iconPack
        val changedKeys = (customIconsOnly.keys + this.customIcons.keys)
            .filter { customIconsOnly[it] != this.customIcons[it] }
        this.iconPack = iconPack
        this.customIcons = customIconsOnly
        // Icons are cached per pack (see cacheKey), so switching packs (e.g. with the theme) keeps both.
        if (!packChanged) changedKeys.forEach { cache.remove(it) }
        _generation.update { it + 1 }
    }

    /** Themed icons in the active theme's [accent], or off with null. Android 13+ only. */
    fun setThemedTint(accent: Int?) {
        val tint = accent.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
        if (tint == themedTint) return
        themedTint = tint
        _generation.update { it + 1 }
    }

    /** Themed icons are light on dark in the dark theme, like the system's. */
    fun setDarkTheme(dark: Boolean) {
        if (dark == darkTheme) return
        darkTheme = dark
        if (themedTint != null) _generation.update { it + 1 }
    }

    /** The largest size icons are shown at, so they're rendered sharp. */
    fun setIconSizeDp(sizeDp: Int) {
        val px = (sizeDp.coerceAtLeast(DEFAULT_SIZE_DP) * density).roundToInt()
        if (px == iconSizePx) return
        iconSizePx = px
        cache.evictAll()
        _generation.update { it + 1 }
    }

    private fun render(app: AppInfo): ImageBitmap? {
        val info = appsRepository.activityInfo(app.key) ?: return null
        return runCatching {
            val custom = customIcons[app.key]
            val themed = when {
                custom != null -> iconPacks.drawable(custom.iconPack!!, custom.iconName!!, densityDpi)
                else -> iconPack?.let { iconPacks.iconFor(it, app.componentName, densityDpi) }
            } ?: themedTint?.let { monochrome(info.getIcon(densityDpi), it) }
            val drawable = when {
                themed == null -> info.getBadgedIcon(densityDpi)
                app.user == Process.myUserHandle() -> themed
                else -> context.packageManager.getUserBadgedIcon(themed, app.user)
            }
            drawable.toBitmap(iconSizePx, iconSizePx).asImageBitmap().also { it.prepareToDraw() }
        }.getOrNull()
    }

    /** The app's monochrome layer on a tinted background, in the system's icon shape; null if it has none. */
    private fun monochrome(icon: Drawable, accent: Int): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || icon !is AdaptiveIconDrawable) return null
        val mono = icon.monochrome ?: return null
        val (background, foreground) = if (darkTheme) {
            ColorUtils.blendARGB(accent, BLACK, 0.78f) to ColorUtils.blendARGB(accent, WHITE, 0.62f)
        } else {
            ColorUtils.blendARGB(accent, WHITE, 0.82f) to ColorUtils.blendARGB(accent, BLACK, 0.5f)
        }
        return AdaptiveIconDrawable(ColorDrawable(background), mono.mutate().apply { setTint(foreground) })
    }

    private companion object {
        const val DEFAULT_SIZE_DP = 64 // the largest icon size setting
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        fun cacheSizeKb() = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()
    }
}
