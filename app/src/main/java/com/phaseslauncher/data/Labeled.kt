package com.phaseslauncher.data

import androidx.annotation.StringRes

/** An option the UI shows by name, such as a setting's choices. Compose reads the text with `label`. */
interface Labeled {
    @get:StringRes
    val labelRes: Int
}
