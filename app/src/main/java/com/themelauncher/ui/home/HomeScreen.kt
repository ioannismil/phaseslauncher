package com.themelauncher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.themelauncher.ui.theme.onWallpaper

/** Placeholder home for phase 1; the clock, theme switcher and dock arrive in phase 3. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Text(
            text = "Swipe up for apps",
            style = MaterialTheme.typography.labelLarge.onWallpaper(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        )
    }
}
