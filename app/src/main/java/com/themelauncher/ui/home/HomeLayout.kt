package com.themelauncher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.themelauncher.data.settings.ClockPosition
import com.themelauncher.data.settings.DateFormatOption

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
) {
    val showHeader: Boolean get() = showTime || showDate || showBattery
}

val LocalHomeLayout = staticCompositionLocalOf { HomeLayout() }
