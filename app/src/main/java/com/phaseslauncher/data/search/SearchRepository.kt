package com.phaseslauncher.data.search

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.normalizedForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactResult(val id: Long, val lookupKey: String, val name: String, val photoUri: Uri?)

/** A system settings page that search can jump to. */
data class SettingsResult(val title: String, val action: String)

/** Non-app search: contacts, system settings pages, and the web. */
class SearchRepository(private val context: Context) {

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun searchContacts(query: String, limit: Int = 3): List<ContactResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !hasContactsPermission()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        add(
                            ContactResult(
                                id = c.getLong(0),
                                lookupKey = c.getString(1).orEmpty(),
                                name = c.getString(2).orEmpty(),
                                photoUri = c.getString(3)?.let(Uri::parse),
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    fun searchSettings(query: String, limit: Int = 3): List<SettingsResult> {
        val q = query.trim().normalizedForSearch()
        if (q.length < 2) return emptyList()
        return SETTINGS
            .map { (title, action, keywords) -> Triple(context.getString(title), action, keywords) }
            .filter { (title, _, keywords) -> title.normalizedForSearch().contains(q) || keywords.any { it.startsWith(q) } }
            .take(limit)
            .map { SettingsResult(it.first, it.second) }
    }

    fun openContact(contact: ContactResult) = start(
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
            ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)),
    )

    fun openSettings(result: SettingsResult) = start(Intent(result.action))

    /** The device's web search (usually Google), falling back to the browser. */
    fun searchWeb(query: String) {
        val webSearch = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!start(webSearch)) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        /** Title, settings action, extra keywords (normalized, lowercase, English). */
        val SETTINGS = listOf(
            Triple(R.string.wi_fi, Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "internet", "network")),
            Triple(R.string.bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "pair", "headphones", "car")),
            Triple(R.string.mobile_network, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, listOf("mobile", "data", "sim", "roaming")),
            Triple(R.string.airplane_mode, Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight")),
            Triple(R.string.display, Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark")),
            Triple(R.string.sound_vibration, Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration")),
            Triple(R.string.notifications, "android.settings.NOTIFICATION_SETTINGS", listOf("notifications", "alerts")),
            Triple(R.string.do_not_disturb, Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, listOf("dnd", "do not disturb", "silence")),
            Triple(R.string.battery, Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "power", "charging")),
            Triple(R.string.apps, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, listOf("apps", "applications", "uninstall")),
            Triple(R.string.storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space", "memory")),
            Triple(R.string.location, Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
            Triple(R.string.security, Settings.ACTION_SECURITY_SETTINGS, listOf("security", "lock", "fingerprint", "password")),
            Triple(R.string.privacy, Settings.ACTION_PRIVACY_SETTINGS, listOf("privacy", "permissions")),
            Triple(R.string.accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility", "talkback")),
            Triple(R.string.date_time, Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock", "timezone")),
            Triple(R.string.languages, Settings.ACTION_LOCALE_SETTINGS, listOf("language", "locale", "keyboard")),
            Triple(R.string.accounts, Settings.ACTION_SYNC_SETTINGS, listOf("accounts", "sync", "google")),
            Triple(R.string.default_apps, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, listOf("default", "home", "browser")),
            Triple(R.string.about_phone, Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "phone", "version", "build")),
            Triple(R.string.developer_options, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "debugging", "usb")),
            Triple(R.string.settings, Settings.ACTION_SETTINGS, listOf("settings", "preferences")),
        )
    }
}
