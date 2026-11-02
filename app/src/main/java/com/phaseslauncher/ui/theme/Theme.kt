package com.phaseslauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.phaseslauncher.data.settings.AppearanceMode

private val FallbackLight = lightColorScheme(primary = Color(0xFF5B4FC4))
private val FallbackDark = darkColorScheme(primary = Color(0xFFC6BFFF))

/** Whether the launcher's screens are dark: the system's setting unless the user picked one. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppearanceMode.isDark(): Boolean = when (this) {
    AppearanceMode.System -> isSystemInDarkTheme()
    AppearanceMode.Light -> false
    AppearanceMode.Dark -> true
}

/** Material 3 theme; uses wallpaper-based dynamic colors on Android 12+. */
@Composable
fun PhasesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> FallbackDark

        else -> FallbackLight
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
