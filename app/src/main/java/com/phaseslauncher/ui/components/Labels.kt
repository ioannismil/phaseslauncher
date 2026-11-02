package com.phaseslauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.phaseslauncher.data.Labeled

/** The option's name in the current language. */
val Labeled.label: String
    @Composable @ReadOnlyComposable
    get() = stringResource(labelRes)
