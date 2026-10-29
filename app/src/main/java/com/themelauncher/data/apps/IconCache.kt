package com.themelauncher.data.apps

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
 */
class IconCache(context: Context, private val appsRepository: AppsRepository) {

    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val iconSizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
    private val loadDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }

    /** Bumped when icons are invalidated so visible icons re-read the cache. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(app: AppInfo): ImageBitmap? = cache.get(app.key) ?: withContext(loadDispatcher) {
        cache.get(app.key) ?: render(app)?.also { cache.put(app.key, it) }
    }

    suspend fun preload(apps: List<AppInfo>) = coroutineScope {
        apps.filter { cache.get(it.key) == null }.map { async { load(it) } }.awaitAll()
    }

    fun invalidate(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { cache.remove(it) }
        _generation.update { it + 1 }
    }

    private fun render(app: AppInfo): ImageBitmap? {
        val info = appsRepository.activityInfo(app.key) ?: return null
        return runCatching {
            info.getBadgedIcon(densityDpi).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                .also { it.prepareToDraw() }
        }.getOrNull()
    }

    private companion object {
        const val ICON_SIZE_DP = 64 // the largest icon size setting

        fun cacheSizeKb() = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()
    }
}
