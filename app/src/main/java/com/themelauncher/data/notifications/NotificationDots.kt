package com.themelauncher.data.notifications

import android.app.Notification
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.themelauncher.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The active theme's notification filter: only [allowedPackages] get dots, and with [hide] the
 * others are also kept out of the notification shade.
 */
data class NotificationPolicy(
    val allowedPackages: Set<String>,
    val hide: Boolean,
    /** Calls, alarms and reminders get through regardless. */
    val allowUrgent: Boolean,
)

/**
 * Which apps currently have a notification worth a dot. Fed by [NotificationDotsService],
 * which only runs once the user grants notification access.
 */
class NotificationDotsRepository(private val context: Context) {

    private val _dotKeys = MutableStateFlow<Set<String>>(emptySet())
    /** Keys from [dotKey]. */
    val dotKeys: StateFlow<Set<String>> = _dotKeys.asStateFlow()

    /** The active theme's filter; null lets everything through. Set by the app container. */
    val policy = MutableStateFlow<NotificationPolicy?>(null)

    fun hasAccess(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens the system screen where the user grants notification access to ThemeLauncher. */
    fun openAccessSettings(from: Context) {
        val component = ComponentName(context, NotificationDotsService::class.java)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }
        for (intent in listOfNotNull(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            try {
                from.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    internal fun update(keys: Set<String>) {
        _dotKeys.value = keys
    }

    companion object {
        fun dotKey(packageName: String, user: UserHandle) = "$packageName:${user.hashCode()}"
    }
}

class NotificationDotsService : NotificationListenerService() {

    private val repository get() = applicationContext.appContainer.notificationDots
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var policyJob: Job? = null

    override fun onListenerConnected() {
        // A theme switch changes what's filtered.
        policyJob?.cancel()
        policyJob = scope.launch { repository.policy.collect { refresh() } }
    }

    override fun onListenerDisconnected() {
        policyJob?.cancel()
        repository.update(emptySet())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val rankingMap = runCatching { currentRanking }.getOrNull()
        val ranking = Ranking()
        val policy = repository.policy.value
        val allowed = { sbn: StatusBarNotification ->
            policy == null || sbn.packageName in policy.allowedPackages || sbn.packageName in ALWAYS_ALLOWED ||
                sbn.packageName == packageName || (policy.allowUrgent && sbn.notification.category in URGENT)
        }
        if (policy?.hide == true) {
            // Snoozed in short stretches, so they come back soon after the theme stops filtering them.
            active.filter { !it.isOngoing && !allowed(it) }.forEach { sbn ->
                runCatching { snoozeNotification(sbn.key, SNOOZE_MS) }
            }
        }
        repository.update(
            active.filter { sbn ->
                val notification = sbn.notification
                // Same rules as the stock launcher: no ongoing (music, navigation) or group-summary
                // notifications, and respect channels where the user turned dots off. And the theme's filter.
                allowed(sbn) &&
                    !sbn.isOngoing &&
                    notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    (rankingMap?.getRanking(sbn.key, ranking) != true || ranking.canShowBadge())
            }.mapTo(HashSet()) { NotificationDotsRepository.dotKey(it.packageName, it.user) },
        )
    }

    private companion object {
        const val SNOOZE_MS = 10 * 60 * 1000L
        val URGENT = setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT)
        val ALWAYS_ALLOWED = setOf("android", "com.android.systemui")
    }
}
