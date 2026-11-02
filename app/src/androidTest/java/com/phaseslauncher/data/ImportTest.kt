package com.phaseslauncher.data

import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.backup.BackupRepository
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.importer.ImportedItem
import com.phaseslauncher.data.importer.LauncherImporter
import com.phaseslauncher.data.items.FolderRepository
import com.phaseslauncher.data.items.GridPlacement
import com.phaseslauncher.data.items.ItemKeys
import com.phaseslauncher.data.settings.SettingsRepository
import com.phaseslauncher.data.share.ImportOptions
import com.phaseslauncher.data.share.ShareOptions
import com.phaseslauncher.data.share.SharedItem
import com.phaseslauncher.data.share.ThemeSharing
import com.phaseslauncher.data.themes.ThemeIconKey
import com.phaseslauncher.data.themes.ThemeRepository
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Phases reads from outside: phases shared as links or files (which any app can hand it),
 * its own backups, and other launchers' backups. Malformed input must be refused, not crash.
 */
@RunWith(AndroidJUnit4::class)
class ImportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LauncherDatabase
    private lateinit var themes: ThemeRepository
    private lateinit var sharing: ThemeSharing
    private val dir by lazy { File(context.cacheDir, "import-test").apply { mkdirs() } }

    private fun app(pkg: String, activity: String = ".Main") =
        AppInfo(key = "$pkg/$activity", label = pkg.substringAfterLast('.'), componentName = ComponentName(pkg, pkg + activity), user = Process.myUserHandle())

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java).build()
        themes = ThemeRepository(db)
        sharing = ThemeSharing(context, db, themes)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    // region Shared phases

    private suspend fun exportWork(): JSONObject {
        val id = themes.createTheme("Work", ThemeIconKey.Work, 0xFF448AFF.toInt(), listOf("com.mail/.Main", "com.chat/.Main"))
        FolderRepository(db).createWith(id, "com.chat/.Main", "Talk")
        val folders = FolderRepository(db).folders.first()
        val work = themes.themes.first().single { it.id == id }
        return sharing.export(work, folders, emptyList(), { it.substringBefore('/') }, null, ShareOptions())
    }

    @Test
    fun aSharedPhaseSurvivesALinkAndAFile() = runTest {
        val json = exportWork()
        val link = sharing.toLink(json)
        for (text in listOf(link, json.toString(), "Try my phase: $link !")) {
            val shared = sharing.parse(text)
            assertEquals("Work", shared.name)
            assertEquals(ThemeIconKey.Work, shared.icon)
            assertEquals(2, shared.items.size)
            assertEquals("com.mail/.Main", (shared.items[0] as SharedItem.App).app.key)
            val folder = shared.items[1] as SharedItem.Folder
            assertEquals("Talk", folder.name)
            assertEquals(listOf("com.chat/.Main"), folder.apps.map { it.key })
        }
    }

    @Test
    fun garbageIsRefused() {
        for (text in listOf("", "hello", "{}", """{"format":"something-else"}""", "phases://phase?d=!!!", "phases://phase?d=AAAA")) {
            assertRefused("'$text'") { sharing.parse(text) }
        }
    }

    @Test
    fun aLinkThatInflatesToSomethingHugeIsRefused() {
        // 4 MB of zeros deflates to a few KB: past the 1 MB cap, so it's refused before it's read.
        val deflater = Deflater().apply {
            setInput(ByteArray(4 shl 20))
            finish()
        }
        val out = ByteArray(64 shl 10)
        val size = deflater.deflate(out)
        val link = ThemeSharing.LINK_PREFIX + Base64.encodeToString(out.copyOf(size), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        assertRefused("zip bomb") { sharing.parse(link) }
    }

    @Test
    fun aReceivedPhaseUsesTheAppsInstalledHere() = runTest {
        val shared = sharing.parse(sharing.toLink(exportWork()))
        // Mail is installed under another activity name; chat isn't installed at all.
        val resolution = sharing.resolve(shared, listOf(app("com.mail", ".Inbox")))
        assertEquals(mapOf("com.mail/.Main" to "com.mail/.Inbox"), resolution.keys)
        assertEquals(listOf("com.chat/.Main"), resolution.missing.map { it.key })

        val id = sharing.install(shared, resolution, ImportOptions(name = "Work from Ana"))
        val installed = themes.themes.first().single { it.id == id }
        assertEquals("Work from Ana", installed.name)
        assertEquals(listOf("com.mail/.Inbox"), installed.appKeys)
    }

    // endregion

    // region Backups

    @Test
    fun aBackupRestoresPhasesFoldersAndTheirPlacesOnTheGrid() = runTest {
        val backups = BackupRepository(context, db, SettingsRepository(context))
        val id = themes.createTheme("Home", ThemeIconKey.Home, 0, listOf("com.mail/.Main", "com.chat/.Main", "com.maps/.Main"))
        val folderKey = ItemKeys.folder(FolderRepository(db).createWith(id, "com.maps/.Main", "Go"))
        themes.saveLayout(id, listOf(GridPlacement("com.mail/.Main", 2, 3), GridPlacement("com.chat/.Main", 0, 0), GridPlacement(folderKey, 3, 5)))

        val file = Uri.fromFile(File(dir, "backup.json"))
        assertEquals(1, backups.exportTo(file).themes)
        db.themeDao().deleteAllThemes()
        assertEquals(1, backups.importFrom(file).themes)

        val restored = themes.themes.first().single()
        assertEquals("Home", restored.name)
        val restoredFolder = restored.appKeys.single { ItemKeys.folderId(it) != null }
        assertEquals(
            mapOf("com.chat/.Main" to (0 to 0), "com.mail/.Main" to (2 to 3), restoredFolder to (3 to 5)),
            restored.cells.mapValues { it.value.x to it.value.y },
        )
        assertEquals(listOf("com.maps/.Main"), FolderRepository(db).folders.first().getValue(restoredFolder).itemKeys)
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() = runTest {
        val backups = BackupRepository(context, db, SettingsRepository(context))
        val file = File(dir, "future.json").apply { writeText(JSONObject().put("version", 999).put("themes", JSONArray()).toString()) }
        try {
            backups.importFrom(Uri.fromFile(file))
            fail("a backup from a newer version was restored")
        } catch (expected: IllegalArgumentException) {
        }
    }

    // endregion

    // region Other launchers

    /** A Launcher3-style database: two apps and a folder on the first page, an app on the second, a dock. */
    private fun launcherDatabase(): File {
        val file = File(dir, "launcher.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY, title TEXT, intent TEXT, container INTEGER, screen INTEGER, cellX INTEGER, cellY INTEGER, itemType INTEGER)")
            fun row(id: Int, title: String, pkg: String?, container: Int, screen: Int, x: Int, y: Int, type: Int) {
                val intent = pkg?.let { "#Intent;action=android.intent.action.MAIN;component=$it/.Main;end" }
                db.execSQL("INSERT INTO favorites VALUES (?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(id, title, intent, container, screen, x, y, type))
            }
            row(1, "Mail", "com.mail", -100, 0, 1, 0, 0)
            row(2, "Chat", "com.chat", -100, 0, 0, 0, 0)
            row(3, "Games", null, -100, 0, 0, 1, 2)
            row(4, "A", "com.game.a", 3, 0, 0, 0, 0)
            row(5, "B", "com.game.b", 3, 0, 1, 0, 0)
            row(6, "Maps", "com.maps", -100, 1, 0, 0, 0)
            row(7, "Phone", "com.phone", -101, 0, 0, 0, 0)
            row(8, "Gone", "com.uninstalled", -100, 1, 1, 0, 0)
        }
        return file
    }

    private val installed by lazy { listOf("com.mail", "com.chat", "com.game.a", "com.game.b", "com.maps", "com.phone").map { app(it) } }

    @Test
    fun anotherLaunchersHomeScreenIsRead() = runTest {
        val layout = LauncherImporter(context, db, themes).read(Uri.fromFile(launcherDatabase()), installed)
        assertEquals(2, layout.pages.size)
        // Row by row: chat (0,0) before mail (1,0), then the folder on the next row.
        assertEquals(
            listOf(
                ImportedItem.App("com.chat/.Main"),
                ImportedItem.App("com.mail/.Main"),
                ImportedItem.Folder("Games", listOf("com.game.a/.Main", "com.game.b/.Main")),
            ),
            layout.pages[0].items,
        )
        assertEquals(listOf(ImportedItem.App("com.maps/.Main")), layout.pages[1].items)
        assertEquals(listOf("com.phone/.Main"), layout.dock)
        assertEquals(1, layout.unmatched)
    }

    @Test
    fun aZippedBackupLikeNovasIsRead() = runTest {
        val zip = File(dir, "backup.novabackup")
        val database = launcherDatabase()
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("launcher.db"))
            database.inputStream().use { it.copyTo(out) }
            out.closeEntry()
        }
        val layout = LauncherImporter(context, db, themes).read(Uri.fromFile(zip), installed)
        assertEquals(2, layout.pages.size)
    }

    @Test
    fun aFileThatIsntALauncherBackupIsRefused() = runTest {
        val file = File(dir, "notes.txt").apply { writeText("not a database") }
        try {
            LauncherImporter(context, db, themes).read(Uri.fromFile(file), installed)
            fail("a text file was read as a launcher backup")
        } catch (expected: IllegalArgumentException) {
        }
    }

    // endregion

    private fun assertRefused(what: String, block: () -> Unit) {
        assertTrue("$what was accepted", runCatching(block).isFailure)
    }
}
