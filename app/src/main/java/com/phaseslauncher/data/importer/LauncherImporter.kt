package com.phaseslauncher.data.importer

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.db.FolderEntity
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.themes.ThemeDefaults
import com.phaseslauncher.data.themes.ThemeDraft
import com.phaseslauncher.data.themes.ThemeIconKey
import com.phaseslauncher.data.themes.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** Something on another launcher's home page, matched to an installed app. */
sealed interface ImportedItem {
    data class App(val key: String) : ImportedItem
    data class Folder(val name: String, val keys: List<String>) : ImportedItem
}

data class ImportedPage(val index: Int, val items: List<ImportedItem>) {
    val appCount: Int get() = items.sumOf { if (it is ImportedItem.Folder) it.keys.size else 1 }
}

/** A home screen layout read from another launcher's backup. */
data class LauncherLayout(
    val pages: List<ImportedPage>,
    val dock: List<String>,
    /** Apps in the backup that aren't installed here (or couldn't be read). */
    val unmatched: Int,
)

enum class ImportMode(val label: String) {
    PagePerTheme("Each home screen page becomes a phase"),
    OneTheme("Everything in one phase"),
}

data class LayoutImportOptions(
    val mode: ImportMode = ImportMode.PagePerTheme,
    /** Page index → theme name; pages not in here are skipped. One-theme mode uses [singleName]. */
    val pageNames: Map<Int, String>,
    val singleName: String = "Imported",
    val importDock: Boolean = true,
    val keepFolders: Boolean = true,
)

/**
 * Reads the home screen from another launcher's backup: Nova Launcher (.novabackup), Lawnchair and
 * other launchers built on Android's Launcher3 keep it in a "favorites" table of a SQLite database,
 * either as the file itself or inside a zip. Widgets and shortcuts aren't carried over: they belong
 * to the other launcher.
 */
class LauncherImporter(
    private val context: Context,
    private val database: LauncherDatabase,
    private val themes: ThemeRepository,
) {

    suspend fun read(uri: Uri, installed: List<AppInfo>): LauncherLayout = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "launcher-import").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val source = File(work, "backup")
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { input.copyTo(it) } }
                ?: error("Could not open the file")
            val databases = if (source.startsWith(ZIP_MAGIC)) unzipDatabases(source, work) else listOf(source)
            val layouts = databases.mapNotNull { file -> runCatching { readLayout(file, installed) }.onFailure { Log.w(TAG, "Not a layout: ${file.name}", it) }.getOrNull() }
            layouts.maxByOrNull { layout -> layout.pages.sumOf { it.appCount } + layout.dock.size }
                ?.takeIf { it.pages.isNotEmpty() || it.dock.isNotEmpty() }
                ?: throw IllegalArgumentException(
                    "Couldn't find a home screen in this file. Phases reads backups from Nova Launcher " +
                        "(.novabackup), Lawnchair and other Launcher3-based launchers.",
                )
        } finally {
            work.deleteRecursively()
        }
    }

    /** Creates the themes (and dock) from [layout]; returns how many themes were added. */
    suspend fun apply(layout: LauncherLayout, options: LayoutImportOptions): Int = withContext(Dispatchers.IO) {
        val chosen = layout.pages.filter { it.index in options.pageNames }
        val groups: List<Pair<String, List<ImportedItem>>> = when (options.mode) {
            ImportMode.PagePerTheme -> chosen.map { page -> options.pageNames.getValue(page.index) to page.items }
            ImportMode.OneTheme -> listOf(options.singleName to chosen.flatMap { it.items })
        }.filter { (_, items) -> items.isNotEmpty() }
        database.withTransaction {
            val used = database.themeDao().getThemes().map { it.accentColor }.toMutableSet()
            groups.forEachIndexed { i, (name, items) ->
                val color = ThemeDefaults.AccentColors.firstOrNull { it !in used } ?: ThemeDefaults.AccentColors[i % ThemeDefaults.AccentColors.size]
                used += color
                val icon = if (i == 0) ThemeIconKey.Home else ThemeIconKey.Star
                val id = themes.createTheme(ThemeDraft(name.trim().ifBlank { "Imported" }.take(24), icon, color))
                val keys = items.flatMap { item ->
                    when (item) {
                        is ImportedItem.App -> listOf(item.key)
                        is ImportedItem.Folder -> if (options.keepFolders) {
                            val folderId = database.folderDao().insert(FolderEntity(themeId = id, name = item.name.ifBlank { "Folder" }))
                            database.folderDao().setItems(folderId, item.keys)
                            listOf(ItemKeys.folder(folderId))
                        } else {
                            item.keys
                        }
                    }
                }.distinct()
                themes.setThemeApps(id, keys)
            }
            if (options.importDock && layout.dock.isNotEmpty()) {
                database.favoritesDao().setFavorites(layout.dock.distinct().take(ThemeDefaults.MAX_FAVORITES))
            }
        }
        groups.size
    }

    private fun unzipDatabases(zip: File, dir: File): List<File> = buildList {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var index = 0
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) {
                    // Only the entry's bytes are used; its name never becomes a path.
                    val out = File(dir, "entry-${index++}")
                    var written = 0L
                    out.outputStream().use { o ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            written += n
                            require(written <= MAX_ENTRY_BYTES) { "This backup is too large" }
                            o.write(buffer, 0, n)
                        }
                    }
                    if (out.startsWith(SQLITE_MAGIC)) add(out) else out.delete()
                }
            }
        }
    }

    private fun readLayout(file: File, installed: List<AppInfo>): LauncherLayout {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            val columns = it.rawQuery("PRAGMA table_info(favorites)", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            require("intent" in columns && "container" in columns && "itemType" in columns) { "No favorites table" }
            fun col(name: String) = if (name in columns) name else "NULL"
            val rows = it.rawQuery(
                "SELECT _id, ${col("title")}, intent, container, ${col("screen")}, ${col("cellX")}, ${col("cellY")}, itemType FROM favorites",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            Row(
                                id = c.getLong(0),
                                title = c.getString(1).orEmpty(),
                                intent = c.getString(2),
                                container = c.getLong(3),
                                screen = if (c.isNull(4)) 0 else c.getLong(4),
                                x = if (c.isNull(5)) 0 else c.getInt(5),
                                y = if (c.isNull(6)) 0 else c.getInt(6),
                                type = c.getInt(7),
                            ),
                        )
                    }
                }
            }
            val screenRank = runCatching {
                it.rawQuery("SELECT _id, screenRank FROM workspaceScreens", null).use { c ->
                    buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) }
                }
            }.getOrDefault(emptyMap())
            return buildLayout(rows, screenRank, installed)
        }
    }

    private fun buildLayout(rows: List<Row>, screenRank: Map<Long, Int>, installed: List<AppInfo>): LauncherLayout {
        val byKey = installed.associateBy { it.key }
        val byPackage = installed.groupBy { it.packageName }
        var unmatched = 0
        fun resolve(row: Row): String? {
            if (row.type != TYPE_APP && row.type != TYPE_SHORTCUT) return null
            val component = row.intent?.let { runCatching { Intent.parseUri(it, 0) }.getOrNull() }?.component
            val match = component?.let { byKey[it.flattenToShortString()] ?: byPackage[it.packageName]?.firstOrNull() }
            // Old-style shortcuts that aren't an app's launch intent (e.g. a web page) are skipped quietly.
            if (match == null && row.type == TYPE_APP) unmatched++
            return match?.key
        }
        val children = rows.filter { it.container > 0 }.groupBy { it.container }
        fun items(rowsHere: List<Row>): List<ImportedItem> = rowsHere.sortedWith(compareBy({ it.y }, { it.x })).mapNotNull { row ->
            if (row.type == TYPE_FOLDER) {
                val keys = children[row.id].orEmpty().sortedWith(compareBy({ it.screen }, { it.y }, { it.x })).mapNotNull(::resolve).distinct()
                when (keys.size) {
                    0 -> null
                    1 -> ImportedItem.App(keys.single())
                    else -> ImportedItem.Folder(row.title, keys)
                }
            } else {
                resolve(row)?.let(ImportedItem::App)
            }
        }
        val desktop = rows.filter { it.container == CONTAINER_DESKTOP }
        val pages = desktop.groupBy { it.screen }
            .toSortedMap(compareBy({ screenRank[it] ?: it.toInt() }, { it }))
            .values
            .map(::items)
            .filter { it.isNotEmpty() }
            .mapIndexed { i, items -> ImportedPage(i, items) }
        val dock = rows.filter { it.container == CONTAINER_HOTSEAT }
            .sortedWith(compareBy({ it.screen }, { it.x }, { it.y }))
            .flatMap { row ->
                if (row.type == TYPE_FOLDER) children[row.id].orEmpty().mapNotNull(::resolve) else listOfNotNull(resolve(row))
            }
            .distinct()
        return LauncherLayout(pages, dock, unmatched)
    }

    private data class Row(
        val id: Long,
        val title: String,
        val intent: String?,
        val container: Long,
        val screen: Long,
        val x: Int,
        val y: Int,
        val type: Int,
    )

    private fun File.startsWith(magic: ByteArray): Boolean = runCatching {
        inputStream().use { input ->
            val head = ByteArray(magic.size)
            input.read(head) == magic.size && head.contentEquals(magic)
        }
    }.getOrDefault(false)

    private companion object {
        const val TAG = "LauncherImporter"
        const val CONTAINER_DESKTOP = -100L
        const val CONTAINER_HOTSEAT = -101L
        const val TYPE_APP = 0
        const val TYPE_SHORTCUT = 1
        const val TYPE_FOLDER = 2
        const val MAX_ENTRY_BYTES = 64L shl 20
        val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray()
    }
}
