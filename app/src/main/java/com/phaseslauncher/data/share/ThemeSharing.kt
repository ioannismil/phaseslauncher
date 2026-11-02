package com.phaseslauncher.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.phaseslauncher.R
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.db.FolderEntity
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.data.items.Folder
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeAutomation
import com.phaseslauncher.data.rules.ThemeRule
import com.phaseslauncher.data.themes.SoundProfile
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.themes.ThemeDraft
import com.phaseslauncher.data.themes.ThemeIconKey
import com.phaseslauncher.data.themes.ThemeRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What goes into a shared theme. */
data class ShareOptions(
    val includeRules: Boolean = true,
    /** Wi-Fi networks, Bluetooth devices and places: they say where you live and work, so they're off by default. */
    val includePersonalRules: Boolean = false,
    val includeFocus: Boolean = true,
    /** Only for a file; links and QR codes are too small. */
    val includeWallpaper: Boolean = false,
)

/** One app in a shared theme, as the sender had it. */
data class SharedApp(val key: String, val label: String) {
    val packageName: String get() = AppInfo.packageOf(key)
}

/** An entry on a shared theme's grid. */
sealed interface SharedItem {
    data class App(val app: SharedApp) : SharedItem
    data class Folder(val name: String, val apps: List<SharedApp>) : SharedItem
}

/** A theme received from someone else, before it's added. */
data class SharedTheme(
    val name: String,
    val icon: ThemeIconKey,
    val accentColor: Int,
    val items: List<SharedItem>,
    val ownDock: List<SharedApp>?,
    val soundProfile: SoundProfile,
    val iconPack: String?,
    val iconPackLabel: String?,
    val gridLayout: String?,
    val rules: List<RuleSetting>,
    val automation: ThemeAutomation,
    val focus: ThemeFocus,
    /** Labels for every app key the theme mentions (including focus settings). */
    val labels: Map<String, String>,
    val wallpaper: ByteArray?,
) {
    val allApps: List<SharedApp>
        get() = (items.flatMap { if (it is SharedItem.App) listOf(it.app) else (it as SharedItem.Folder).apps } + ownDock.orEmpty())
            .distinctBy { it.key }
}

/** How the received theme's apps map onto this phone. */
data class Resolution(val keys: Map<String, String>, val missing: List<SharedApp>)

/** What to take from a received theme. */
data class ImportOptions(
    val name: String,
    val includeRules: Boolean = true,
    val includeFocus: Boolean = true,
    val includeWallpaper: Boolean = true,
    val includeSound: Boolean = true,
)

/**
 * Shares single themes as a file, a link or a QR code, and adds received ones. Apps are matched by
 * their component, then by package, so a theme works on a phone with the same apps installed.
 */
class ThemeSharing(
    private val context: Context,
    private val database: LauncherDatabase,
    private val themes: ThemeRepository,
) {

    // region Export

    fun export(
        theme: Theme,
        folders: Map<String, Folder>,
        dock: List<String>,
        labelOf: (String) -> String?,
        iconPackLabel: String?,
        options: ShareOptions,
    ): JSONObject {
        val labels = HashMap<String, String>()
        fun app(key: String): String {
            labelOf(key)?.let { labels[key] = it }
            return key
        }
        val items = JSONArray()
        theme.appKeys.forEach { key ->
            when {
                ItemKeys.isApp(key) -> items.put(app(key))
                ItemKeys.folderId(key) != null -> folders[key]?.let { folder ->
                    val apps = folder.itemKeys.filter(ItemKeys::isApp)
                    if (apps.isNotEmpty()) items.put(JSONObject().put("folder", folder.name).put("apps", JSONArray(apps.map(::app))))
                }
                // Pinned shortcuts belong to the app that made them; they can't be shared.
            }
        }
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("name", theme.name)
            .put("icon", theme.icon.name)
            .put("color", theme.accentColor)
            .put("items", items)
            .put("soundProfile", theme.soundProfile.name)
            .put("gridLayout", theme.gridLayout ?: JSONObject.NULL)
            .put("iconPack", theme.iconPack ?: JSONObject.NULL)
            .put("iconPackLabel", iconPackLabel ?: JSONObject.NULL)
        if (theme.ownDock) json.put("dock", JSONArray(dock.filter(ItemKeys::isApp).map(::app)))
        if (options.includeRules) {
            val rules = theme.rules.mapNotNull { setting ->
                val rule = setting.rule
                when {
                    rule.isPersonal() && !options.includePersonalRules -> null
                    // Calendar ids are this phone's; the shared rule watches every calendar.
                    rule is ThemeRule.Calendar -> setting.copy(rule = rule.copy(calendarIds = emptySet()))
                    else -> setting
                }
            }
            json.put("rules", JSONArray(rules.map {
                JSONObject().put("type", it.rule.type.name).put("config", ThemeRule.configOf(it.rule)).put("enabled", it.enabled)
            }))
            json.put("automation", JSONObject(theme.automation.toJson()))
        }
        if (options.includeFocus && !theme.focus.isDefault) {
            val focus = theme.focus
            (focus.blockedApps + focus.allowedNotificationApps + focus.appLimits.keys).forEach { app(it) }
            json.put("focus", JSONObject(focus.toJson()))
        }
        json.put("labels", JSONObject(labels as Map<*, *>))
        if (options.includeWallpaper) theme.wallpaperPath?.let(::encodeWallpaper)?.let { json.put("wallpaper", it) }
        return json
    }

    /** A link that opens Phases's import screen. Without the wallpaper. */
    fun toLink(json: JSONObject): String {
        val compact = JSONObject(json.toString()).apply { remove("wallpaper") }
        val bytes = deflate(compact.toString().toByteArray())
        return "$LINK_PREFIX${Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"
    }

    /** A QR code for [text], or null if it's too long for one. */
    fun qrCode(text: String, sizePx: Int): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
        )
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** Writes the theme to a shareable file and returns its content URI. */
    suspend fun writeFile(json: JSONObject, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "phase" }
        val file = File(dir, "$safe$FILE_SUFFIX")
        file.writeText(json.toString(2))
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    // endregion

    // region Import

    /** Reads a shared theme from a link, a text containing one, or the file's JSON. */
    fun parse(text: String): SharedTheme {
        val trimmed = text.trim()
        val json = when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> {
                val data = Regex("${Regex.escape(LINK_PREFIX)}([A-Za-z0-9_-]+)").find(trimmed)?.groupValues?.get(1)
                    ?: throw IllegalArgumentException(context.getString(R.string.this_isn_t_a_shared_phase))
                JSONObject(inflate(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)).decodeToString())
            }
        }
        require(json.optString("format") == FORMAT) { context.getString(R.string.this_isn_t_a_shared_phase) }
        require(json.optInt("version") <= VERSION) { context.getString(R.string.this_phase_is_from_a_newer_version_of_phases) }
        return fromJson(json)
    }

    suspend fun read(uri: Uri): SharedTheme = withContext(Dispatchers.IO) { parse(readText(uri)) }

    /** The file's text, refusing anything far bigger than a theme (with its wallpaper) can be. */
    fun readText(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: error(context.getString(R.string.could_not_open_the_file))
        return input.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                require(out.size() <= MAX_FILE_BYTES) { context.getString(R.string.this_file_is_too_large_to_be_a_phase) }
            }
            out.toByteArray().decodeToString()
        }
    }

    /** Matches the theme's apps to installed ones: same component, else the same app (package) on this phone. */
    fun resolve(shared: SharedTheme, installed: List<AppInfo>): Resolution {
        val byKey = installed.associateBy { it.key }
        val byPackage = installed.groupBy { it.packageName }
        val keys = HashMap<String, String>()
        val missing = ArrayList<SharedApp>()
        val mentioned = shared.allApps + (shared.labels.keys - shared.allApps.map { it.key }.toSet()).map { SharedApp(it, shared.labels[it].orEmpty()) }
        mentioned.forEach { app ->
            val match = byKey[app.key] ?: byPackage[app.packageName]?.firstOrNull()
            if (match != null) keys[app.key] = match.key else if (app in shared.allApps) missing += app
        }
        return Resolution(keys, missing)
    }

    /** Adds the theme with its folders and own dock; returns its id. */
    suspend fun install(shared: SharedTheme, resolution: Resolution, options: ImportOptions): Long = withContext(Dispatchers.IO) {
        val map = resolution.keys
        val wallpaper = if (options.includeWallpaper) shared.wallpaper?.let(::saveWallpaper) else null
        database.withTransaction {
            val id = themes.createTheme(
                ThemeDraft(
                    name = options.name.trim().ifBlank { shared.name },
                    icon = shared.icon,
                    accentColor = shared.accentColor,
                    wallpaperPath = wallpaper,
                    soundProfile = if (options.includeSound) shared.soundProfile else SoundProfile.Unchanged,
                    ownDock = shared.ownDock != null,
                    rules = if (options.includeRules) shared.rules else emptyList(),
                    automation = if (options.includeRules) shared.automation else ThemeAutomation(),
                    focus = if (options.includeFocus) shared.focus.mapKeys(map::get) else ThemeFocus(),
                    iconPack = shared.iconPack,
                    gridLayout = shared.gridLayout,
                ),
            )
            val folderDao = database.folderDao()
            val keys = shared.items.mapNotNull { item ->
                when (item) {
                    is SharedItem.App -> map[item.app.key]
                    is SharedItem.Folder -> {
                        val apps = item.apps.mapNotNull { map[it.key] }.distinct()
                        if (apps.isEmpty()) {
                            null
                        } else {
                            val folderId = folderDao.insert(FolderEntity(themeId = id, name = item.name))
                            folderDao.setItems(folderId, apps)
                            ItemKeys.folder(folderId)
                        }
                    }
                }
            }.distinct()
            themes.setThemeApps(id, keys)
            shared.ownDock?.let { dock ->
                database.favoritesDao().setFavorites(dock.mapNotNull { map[it.key] }.distinct().take(ThemeDefaults.MAX_FAVORITES), id)
            }
            id
        }
    }

    // endregion

    private fun fromJson(json: JSONObject): SharedTheme {
        val labels = json.optJSONObject("labels")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty()
        fun app(key: String) = SharedApp(key, labels[key] ?: AppInfo.packageOf(key))
        val items = json.optJSONArray("items")?.let { array ->
            List(array.length()) { i ->
                when (val entry = array.get(i)) {
                    is JSONObject -> SharedItem.Folder(
                        entry.optString("folder"),
                        entry.optJSONArray("apps").strings().map(::app),
                    )
                    else -> SharedItem.App(app(entry.toString()))
                }
            }
        }.orEmpty()
        val rules = json.optJSONArray("rules")?.let { array ->
            List(array.length()) { array.getJSONObject(it) }.mapNotNull { o ->
                ThemeRule.fromJson(o.optString("type"), o.optJSONObject("config")?.toString() ?: o.optString("config"))
                    ?.let { RuleSetting(it, o.optBoolean("enabled", true)) }
            }
        }.orEmpty()
        return SharedTheme(
            name = json.optString("name").ifBlank { context.getString(R.string.shared_phase) }.take(24),
            icon = ThemeIconKey.fromName(json.optString("icon")),
            accentColor = json.optInt("color", ThemeDefaults.AccentColors.first()),
            items = items,
            ownDock = json.optJSONArray("dock")?.strings()?.map(::app),
            soundProfile = SoundProfile.fromName(json.optString("soundProfile")),
            iconPack = json.optNullableString("iconPack"),
            iconPackLabel = json.optNullableString("iconPackLabel"),
            gridLayout = json.optNullableString("gridLayout"),
            rules = rules,
            automation = json.optJSONObject("automation")?.let { ThemeAutomation.fromJson(it.toString()) } ?: ThemeAutomation(),
            focus = json.optJSONObject("focus")?.let { ThemeFocus.fromJson(it.toString()) } ?: ThemeFocus(),
            labels = labels,
            wallpaper = json.optNullableString("wallpaper")?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() },
        )
    }

    /** Scaled down to at most [WALLPAPER_MAX_PX] and JPEG-compressed, so the file stays shareable. */
    private fun encodeWallpaper(path: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > WALLPAPER_MAX_PX) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun saveWallpaper(bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        File(dir, "${UUID.randomUUID()}.img").apply { writeBytes(bytes) }.absolutePath
    }.getOrNull()

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply {
            setInput(bytes)
            finish()
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater().apply { setInput(bytes) }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
            require(out.size() <= MAX_INFLATED_BYTES) { context.getString(R.string.this_phase_is_too_large) }
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }

    private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }

    companion object {
        const val FORMAT = "phases-phase"
        const val VERSION = 1
        const val SCHEME = "phases"
        const val LINK_PREFIX = "$SCHEME://phase?d="
        const val FILE_SUFFIX = ".phases.json"
        const val AUTHORITY_SUFFIX = ".share"
        private const val SHARE_DIR = "shared"
        private const val WALLPAPER_MAX_PX = 2048
        private const val MAX_INFLATED_BYTES = 1 shl 20
        private const val MAX_FILE_BYTES = 16 shl 20

        /** Rules that reveal personal places and devices. */
        fun ThemeRule.isPersonal() = this is ThemeRule.Wifi || this is ThemeRule.Bluetooth || this is ThemeRule.Location
    }
}
