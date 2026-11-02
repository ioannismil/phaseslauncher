package com.phaseslauncher.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.phaseslauncher.data.themes.ThemeIconKey

val ThemeIconKey.imageVector: ImageVector
    get() = when (this) {
        ThemeIconKey.Gaming -> Icons.Rounded.SportsEsports
        ThemeIconKey.Work -> Icons.Rounded.Work
        ThemeIconKey.Social -> Icons.Rounded.Forum
        ThemeIconKey.Music -> Icons.Rounded.MusicNote
        ThemeIconKey.Home -> Icons.Rounded.Home
        ThemeIconKey.Fitness -> Icons.Rounded.FitnessCenter
        ThemeIconKey.Travel -> Icons.Rounded.Flight
        ThemeIconKey.Study -> Icons.Rounded.School
        ThemeIconKey.Shopping -> Icons.Rounded.ShoppingBag
        ThemeIconKey.Photo -> Icons.Rounded.PhotoCamera
        ThemeIconKey.Code -> Icons.Rounded.Code
        ThemeIconKey.Movie -> Icons.Rounded.Movie
        ThemeIconKey.Food -> Icons.Rounded.Restaurant
        ThemeIconKey.Night -> Icons.Rounded.Bedtime
        ThemeIconKey.Star -> Icons.Rounded.Star
        ThemeIconKey.Heart -> Icons.Rounded.Favorite
    }

/** Black or white, whichever reads better on [background]. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color.Black.copy(alpha = 0.87f) else Color.White
