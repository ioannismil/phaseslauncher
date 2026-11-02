package com.phaseslauncher.api

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.phaseslauncher.R
import com.phaseslauncher.data.themes.Theme

/**
 * One app shortcut per phase, "Switch to Work", kept in step with the phase list. Automation apps
 * that run app shortcuts (Samsung Modes and Routines, Google Assistant, Tasker) can use them, and
 * they show when you long-press Phases in an app list.
 */
object PhaseShortcuts {
    private const val ID_PREFIX = "switch_phase_"
    private const val ICON_PX = 108

    fun update(context: Context, themes: List<Theme>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val shortcuts = themes.take(max).mapIndexed { rank, theme ->
            ShortcutInfoCompat.Builder(context, ID_PREFIX + theme.id)
                .setShortLabel(theme.name)
                .setLongLabel(context.getString(R.string.switch_to, theme.name))
                .setIcon(icon(context, theme.accentColor))
                .setIntent(Intent(Intent.ACTION_VIEW, PhaseApi.link(theme)).setClass(context, SwitchPhaseActivity::class.java))
                .setRank(rank)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        // Shortcuts pinned elsewhere for deleted phases stop working, with a reason. (Ids aren't reused,
        // and a disabled shortcut can't be updated, so only deleted phases are disabled.)
        val ids = themes.mapTo(HashSet()) { ID_PREFIX + it.id }
        val deleted = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .map { it.id }
            .filter { it.startsWith(ID_PREFIX) && it !in ids }
        if (deleted.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, deleted, context.getString(R.string.that_phase_no_longer_exists))
    }

    /** Tells the system the shortcut was used, so it can rank and predict it. */
    fun reportUsed(context: Context, theme: Theme) = ShortcutManagerCompat.reportShortcutUsed(context, ID_PREFIX + theme.id)

    /** The tile's palette in white on a circle of the phase's color. */
    private fun icon(context: Context, accent: Int): IconCompat {
        val bitmap = createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(ICON_PX / 2f, ICON_PX / 2f, ICON_PX / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        ContextCompat.getDrawable(context, R.drawable.ic_tile_theme)?.mutate()?.run {
            setTint(Color.WHITE)
            val inset = ICON_PX / 4
            setBounds(inset, inset, ICON_PX - inset, ICON_PX - inset)
            draw(canvas)
        }
        return IconCompat.createWithBitmap(bitmap)
    }
}
