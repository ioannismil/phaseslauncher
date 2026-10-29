package com.themelauncher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Grid settings for the home screen (theme grid and dock), from Launcher settings. */
@Immutable
data class HomeLayout(val columns: Int = 4, val iconSize: Dp = 56.dp, val showLabels: Boolean = true)

val LocalHomeLayout = staticCompositionLocalOf { HomeLayout() }
