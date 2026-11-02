package com.phaseslauncher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phaseslauncher.data.settings.ClockPosition
import com.phaseslauncher.data.settings.DateFormatOption
import com.phaseslauncher.data.settings.IconArrangement

/** Home screen settings (grid, dock and clock header), from Launcher settings. */
@Immutable
data class HomeLayout(
    val columns: Int = 4,
    val iconSize: Dp = 56.dp,
    val showLabels: Boolean = true,
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.Full,
    val showBattery: Boolean = false,
    val clockPosition: ClockPosition = ClockPosition.Left,
    val showThemeLabel: Boolean = true,
    val showDock: Boolean = true,
    /** Pick the grid's columns and icon size so the theme fits without scrolling. */
    val autoFit: Boolean = false,
    val arrangement: IconArrangement = IconArrangement.TopLeft,
) {
    val showHeader: Boolean get() = showTime || showDate || showBattery
}

val LocalHomeLayout = staticCompositionLocalOf { HomeLayout() }
