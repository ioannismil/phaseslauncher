package com.phaseslauncher.data.rules

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A calendar on the phone, for picking which ones a rule watches. */
data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

/**
 * Keeps the calendar events around now (yesterday to the day after tomorrow) for calendar rules.
 * Re-reads when the calendar changes and every few minutes. Needs the calendar permission.
 */
class CalendarWatcher(private val context: Context) {

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private var scope: CoroutineScope? = null
    private var observing = false
    private var polling: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Reads (and keeps re-reading) events only while some theme has a calendar rule. */
    fun setActive(scope: CoroutineScope, active: Boolean) {
        this.scope = scope
        polling?.cancel()
        polling = if (active) {
            scope.launch(Dispatchers.IO) {
                while (true) {
                    refreshNow()
                    delay(REFRESH_MS)
                }
            }
        } else {
            _events.value = emptyList()
            null
        }
    }

    /** Call after the permission is granted, or when a calendar rule is added. */
    fun refresh() {
        if (polling?.isActive == true) scope?.launch(Dispatchers.IO) { refreshNow() }
    }

    fun calendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        return runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)))
                    }
                }
            }.orEmpty().sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }))
        }.onFailure { Log.w(TAG, "Could not list calendars", it) }.getOrDefault(emptyList())
    }

    private fun refreshNow() {
        if (!hasPermission()) {
            _events.value = emptyList()
            return
        }
        observe()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - DAY_MS)
            ContentUris.appendId(it, now + 2 * DAY_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY,
            CalendarContract.Instances.STATUS,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(6) == CalendarContract.Instances.STATUS_CANCELED) continue
                        add(
                            CalendarEvent(
                                calendarId = c.getLong(0),
                                title = c.getString(1).orEmpty(),
                                begin = c.getLong(2),
                                end = c.getLong(3),
                                allDay = c.getInt(4) != 0,
                                busy = c.getInt(5) != CalendarContract.Instances.AVAILABILITY_FREE,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }
            .onSuccess { _events.value = it }
            .onFailure { Log.w(TAG, "Could not read calendar events", it) }
    }

    private fun observe() {
        if (observing) return
        observing = true
        runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = refresh()
                },
            )
        }.onFailure { observing = false }
    }

    private companion object {
        const val TAG = "CalendarWatcher"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val REFRESH_MS = 15 * 60 * 1000L
    }
}
