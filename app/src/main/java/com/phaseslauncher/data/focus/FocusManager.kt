package com.phaseslauncher.data.focus

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.themes.Theme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why an app can't simply open. */
sealed interface FocusBlock {
    val app: AppInfo
    val theme: Theme
    val mode: BlockMode

    /** The app is blocked while [theme] is on. */
    data class Blocked(override val app: AppInfo, override val theme: Theme, override val mode: BlockMode) : FocusBlock

    /** The app (or the theme's apps together) used up today's time. */
    data class OverLimit(
        override val app: AppInfo,
        override val theme: Theme,
        override val mode: BlockMode,
        val usedMinutes: Int,
        val limitMinutes: Int,
        /** True when the theme's total limit was reached rather than the app's own. */
        val themeTotal: Boolean,
    ) : FocusBlock
}

/**
 * Applies themes' focus settings to launches from the launcher: blocked apps and daily limits.
 * After the pause screen has been waited out, the app opens freely for the theme's allowance.
 */
class FocusManager(private val context: Context) {

    /** App key → when its allowance ends (ms since epoch). */
    private val allowances = HashMap<String, Long>()

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(from: Context) {
        try {
            from.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /**
     * Whether [app] may open now. [active] is the theme that's on, [themes] all themes (limits can
     * apply all day), [dockKeys] the dock shown with the active theme, and [appsOf] a theme's app
     * keys including those in its folders.
     */
    suspend fun check(
        app: AppInfo,
        active: Theme?,
        themes: List<Theme>,
        dockKeys: Set<String>,
        appsOf: (Theme) -> Set<String>,
    ): FocusBlock? {
        val now = System.currentTimeMillis()
        if ((allowances[app.key] ?: 0L) > now) return null
        if (active != null && active.focus.blocksAnything) {
            if (active.focus.blocks(app.key, appsOf(active) + dockKeys)) return FocusBlock.Blocked(app, active, active.focus.blockMode)
        }
        val limiting = themes.filter { theme ->
            theme.focus.hasLimits && (theme.id == active?.id || theme.focus.limitScope == LimitScope.Always)
        }
        if (limiting.isEmpty() || !hasUsageAccess()) return null
        val usage = todayUsageMinutes()
        for (theme in limiting) {
            val focus = theme.focus
            focus.appLimits[app.key]?.let { limit ->
                val used = usage[app.packageName] ?: 0
                if (used >= limit) return FocusBlock.OverLimit(app, theme, focus.limitMode, used, limit, themeTotal = false)
            }
            val themeApps = appsOf(theme)
            if (focus.themeLimitMinutes > 0 && app.key in themeApps) {
                val used = themeApps.map(AppInfo::packageOf).distinct().sumOf { usage[it] ?: 0 }
                if (used >= focus.themeLimitMinutes) {
                    return FocusBlock.OverLimit(app, theme, focus.limitMode, used, focus.themeLimitMinutes, themeTotal = true)
                }
            }
        }
        return null
    }

    /** The pause screen was waited out: let the app open for the theme's allowance. */
    fun allow(block: FocusBlock) {
        allowances[block.app.key] = System.currentTimeMillis() + block.theme.focus.allowMinutes * 60_000L
    }

    @Volatile private var usageCache: Pair<Long, Map<String, Int>>? = null

    /** Minutes each package spent in the foreground today. Needs usage access. Cached briefly: it's a slow query. */
    suspend fun todayUsageMinutes(): Map<String, Int> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        usageCache?.let { (at, usage) -> if (now - at < USAGE_CACHE_MS) return@withContext usage }
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return@withContext emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            manager.queryAndAggregateUsageStats(start, now)
                .mapValues { (_, stats) -> (stats.totalTimeInForeground / 60_000L).toInt() }
        }.getOrDefault(emptyMap()).also { usageCache = now to it }
    }

    private companion object {
        const val USAGE_CACHE_MS = 30_000L
    }
}
