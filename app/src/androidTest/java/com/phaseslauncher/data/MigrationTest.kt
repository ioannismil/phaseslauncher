package com.phaseslauncher.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.phaseslauncher.data.db.LauncherDatabase
import com.phaseslauncher.data.themes.ThemeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades the database from older versions, as an update does on people's phones. A failing
 * upgrade means the launcher crashes on start, so every exported schema is covered.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LauncherDatabase::class.java)

    @Test
    fun everyVersionUpgradesToTheLatest() {
        for (from in 1 until LATEST) {
            val name = "migration-from-$from"
            helper.createDatabase(name, from).close()
            // Checks the upgraded schema against the latest exported one.
            helper.runMigrationsAndValidate(name, LATEST, true).close()
        }
    }

    @Test
    fun phasesAndWidgetsSurviveTheUpgradeToFreePlacement() = runTest {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL("INSERT INTO themes (id, name, icon_key, accent_color, position) VALUES (1, 'Work', 'Work', 0, 0)")
            db.execSQL("INSERT INTO theme_apps (theme_id, app_key, position) VALUES (1, 'mail/.Main', 0), (1, 'calendar/.Main', 1)")
            db.execSQL("INSERT INTO theme_widgets (app_widget_id, theme_id, provider, position, height_dp) VALUES (7, 1, 'clock/.Widget', 0, 180)")
        }
        helper.runMigrationsAndValidate(DB, 6, true).use { db ->
            // Existing items keep their order and have no cell yet, so they're placed as before.
            db.query("SELECT app_key, cell_x, cell_y FROM theme_apps ORDER BY position").use { c ->
                assertEquals(2, c.count)
                c.moveToFirst()
                assertEquals("mail/.Main", c.getString(0))
                assertTrue(c.isNull(1) && c.isNull(2))
            }
            db.query("SELECT height_dp, cell_x, span_x FROM theme_widgets").use { c ->
                c.moveToFirst()
                assertEquals(180, c.getInt(0))
                assertTrue(c.isNull(1) && c.isNull(2))
            }
        }

        // And the app opens the upgraded database and reads it.
        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), LauncherDatabase::class.java, DB).build()
        try {
            val work = ThemeRepository(database).themes.first().single()
            assertEquals(listOf("mail/.Main", "calendar/.Main"), work.appKeys)
            assertTrue(work.cells.isEmpty())
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "migration-test"
        const val LATEST = 6
    }
}
