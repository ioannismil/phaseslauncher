package com.themelauncher.ui.components

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View

object LaunchAnimation {
    /**
     * Activity options that grow the opened app out of the tapped icon. [bounds] are the tile's
     * screen coordinates; [view] is any view in the launcher's window.
     */
    fun options(view: View, bounds: Rect?): Bundle? {
        if (bounds == null || bounds.isEmpty) return null
        val origin = IntArray(2).also(view::getLocationOnScreen)
        return runCatching {
            ActivityOptions.makeScaleUpAnimation(
                view,
                bounds.left - origin[0],
                bounds.top - origin[1],
                bounds.width(),
                bounds.height(),
            ).toBundle()
        }.getOrNull()
    }
}
