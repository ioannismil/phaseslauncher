package com.phaseslauncher.data.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.phaseslauncher.data.db.ThemeWidgetEntity
import com.phaseslauncher.data.db.WidgetDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt

/** A widget placed on a theme. */
data class ThemeWidget(val appWidgetId: Int, val themeId: Long, val heightDp: Int)

/** Height presets offered when resizing a widget. */
enum class WidgetSize(val label: String, val heightDp: Int) {
    Small("Small", 110), Medium("Medium", 180), Large("Large", 260), ExtraLarge("Extra large", 360)
}

/**
 * Hosts app widgets for the home screen and stores which theme each belongs to.
 * The host must listen while the launcher is visible (see MainActivity).
 */
class WidgetRepository(context: Context, private val dao: WidgetDao) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host = LauncherWidgetHost(appContext, HOST_ID)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    /** Theme id → its widgets, top to bottom. */
    val widgets: Flow<Map<Long, List<ThemeWidget>>> = dao.observeAll().map { rows ->
        rows.groupBy({ it.themeId }, { ThemeWidget(it.appWidgetId, it.themeId, it.heightDp) })
    }

    /** Every widget available to add, across personal and work profiles, sorted by app then name. */
    fun availableProviders(): List<AppWidgetProviderInfo> =
        userManager.userProfiles
            .flatMap { user -> runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList()) }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun allocateId(): Int = host.allocateAppWidgetId()

    /** True if bound without asking; false means the system bind dialog must be shown. */
    fun bindIfAllowed(appWidgetId: Int, provider: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(appWidgetId, provider.profile, provider.provider, null) }
            .getOrDefault(false)

    suspend fun add(appWidgetId: Int, themeId: Long, info: AppWidgetProviderInfo, density: Float) {
        val minHeightDp = (info.minHeight / density).roundToInt()
        val height = WidgetSize.entries.firstOrNull { it.heightDp >= minHeightDp }?.heightDp ?: WidgetSize.Large.heightDp
        dao.insert(ThemeWidgetEntity(appWidgetId, themeId, info.provider.flattenToString(), dao.maxPosition(themeId) + 1, height))
    }

    /** Frees an id that was allocated but never placed (e.g. configuration cancelled). */
    fun discardId(appWidgetId: Int) = runCatching { host.deleteAppWidgetId(appWidgetId) }

    suspend fun remove(appWidgetId: Int) {
        discardId(appWidgetId)
        dao.delete(appWidgetId)
    }

    suspend fun removeForTheme(themeId: Long) = dao.getForTheme(themeId).forEach { remove(it.appWidgetId) }

    suspend fun removeAll() = dao.getAll().forEach { remove(it.appWidgetId) }

    suspend fun setHeight(appWidgetId: Int, heightDp: Int) = dao.setHeight(appWidgetId, heightDp)

    suspend fun reorder(orderedIds: List<Int>) = dao.reorder(orderedIds)

    /** Drops widgets whose app was uninstalled (the system no longer knows the id). */
    suspend fun pruneOrphans() {
        dao.getAll().filter { info(it.appWidgetId) == null }.forEach { remove(it.appWidgetId) }
    }

    /** Tells the widget how much room it has so it can pick a layout. */
    fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        runCatching { manager.updateAppWidgetOptions(view.appWidgetId, options) }
            .onFailure { Log.w("WidgetRepository", "Could not update widget size", it) }
    }

    companion object {
        const val HOST_ID = 0x7E11
        fun labelOf(context: Context, info: AppWidgetProviderInfo): String = info.loadLabel(context.packageManager)
        fun ownerLabel(context: Context, info: AppWidgetProviderInfo): String =
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
    }
}

/** Creates [LauncherWidgetHostView]s so widgets can report long-presses to the launcher. */
class LauncherWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetHostView(context)
}

/**
 * Detects a long-press anywhere on the widget (the widget itself still gets taps and scrolls),
 * the way the stock launcher lets you rearrange or remove widgets.
 */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressed = false
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) removeCallbacks(longPressRunnable)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPressRunnable)
        }
        // Once long-pressed, take the rest of the gesture so the widget doesn't also get a click.
        return longPressed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPressRunnable)
        }
        return longPressed || super.onTouchEvent(event)
    }
}
