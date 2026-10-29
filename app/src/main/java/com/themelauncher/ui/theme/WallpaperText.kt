package com.themelauncher.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

/** Colors for text and icons drawn directly on the wallpaper. */
@Immutable
data class WallpaperContentColors(
    val content: Color,
    val shadow: Color,
    /** Translucent fill for chips and the dock so they stand out from the wallpaper. */
    val scrim: Color,
    val isDark: Boolean,
) {
    companion object {
        /** Light content: the safe default on most wallpapers. */
        val Light = WallpaperContentColors(Color.White, Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.22f), isDark = false)
        val Dark = WallpaperContentColors(Color.Black.copy(alpha = 0.87f), Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.35f), isDark = true)
    }
}

val LocalWallpaperContent = staticCompositionLocalOf { WallpaperContentColors.Light }

/** Follows the wallpaper's "supports dark text" hint (Android 12+), so text stays readable. */
@Composable
fun rememberWallpaperContentColors(): WallpaperContentColors {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return WallpaperContentColors.Light

    val context = LocalContext.current
    val wallpaperManager = remember(context) { WallpaperManager.getInstance(context) }
    var colors by remember {
        mutableStateOf(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM).toContentColors())
    }
    DisposableEffect(wallpaperManager) {
        val listener = WallpaperManager.OnColorsChangedListener { newColors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) colors = newColors.toContentColors()
        }
        wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wallpaperManager.removeOnColorsChangedListener(listener) }
    }
    return colors
}

private fun WallpaperColors?.toContentColors(): WallpaperContentColors {
    if (this == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return WallpaperContentColors.Light
    val darkText = colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    return if (darkText) WallpaperContentColors.Dark else WallpaperContentColors.Light
}

fun TextStyle.onWallpaper(colors: WallpaperContentColors): TextStyle = copy(
    color = colors.content,
    shadow = Shadow(color = colors.shadow, blurRadius = 6f),
)
