package com.themelauncher.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.themelauncher.data.db.FavoriteEntity
import com.themelauncher.data.db.LauncherDatabase
import com.themelauncher.data.rules.ThemeRule
import com.themelauncher.data.themes.AddFavoriteResult
import com.themelauncher.data.themes.FavoritesRepository
import com.themelauncher.data.themes.SoundProfile
import com.themelauncher.data.themes.ThemeDefaults
import com.themelauncher.data.themes.ThemeDraft
import com.themelauncher.data.themes.ThemeIconKey
import com.themelauncher.data.themes.ThemeRepository
import com.themelauncher.data.themes.UninstalledAppCleaner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeDataTest {

    private lateinit var db: LauncherDatabase
    private lateinit var themes: ThemeRepository
    private lateinit var favorites: FavoritesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java).build()
        themes = ThemeRepository(db)
        favorites = FavoritesRepository(db.favoritesDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createdThemesKeepOrderAndApps() = runTest {
        val gaming = themes.createTheme(" Gaming ", ThemeIconKey.Gaming, 1, listOf("a/.A", "b/.B"))
        val work = themes.createTheme("Work", ThemeIconKey.Work, 2)

        val all = themes.themes.first()
        assertEquals(listOf(gaming, work), all.map { it.id })
        assertEquals("Gaming", all[0].name)
        assertEquals(listOf("a/.A", "b/.B"), all[0].appKeys)
        assertEquals(emptyList<String>(), all[1].appKeys)
    }

    @Test
    fun appCanBelongToSeveralThemesAndAddIsIdempotent() = runTest {
        val t1 = themes.createTheme("One", ThemeIconKey.Star, 1)
        val t2 = themes.createTheme("Two", ThemeIconKey.Star, 1)
        themes.addApp(t1, "a/.A")
        themes.addApp(t1, "b/.B")
        themes.addApp(t1, "a/.A")
        themes.addApp(t2, "a/.A")

        val all = themes.themes.first().associateBy { it.id }
        assertEquals(listOf("a/.A", "b/.B"), all.getValue(t1).appKeys)
        assertEquals(listOf("a/.A"), all.getValue(t2).appKeys)
    }

    @Test
    fun setThemeAppsReorders() = runTest {
        val t = themes.createTheme("T", ThemeIconKey.Star, 1, listOf("a/.A", "b/.B", "c/.C"))
        themes.setThemeApps(t, listOf("c/.C", "a/.A", "b/.B"))
        assertEquals(listOf("c/.C", "a/.A", "b/.B"), themes.themes.first().single().appKeys)
    }

    @Test
    fun lastThemeCannotBeDeletedAndDeleteCascades() = runTest {
        val t1 = themes.createTheme("One", ThemeIconKey.Star, 1, listOf("a/.A"))
        assertFalse(themes.deleteTheme(t1))

        val t2 = themes.createTheme("Two", ThemeIconKey.Star, 1)
        assertTrue(themes.deleteTheme(t1))
        assertEquals(listOf(t2), themes.themes.first().map { it.id })
        assertEquals(emptyList<String>(), db.themeDao().getAllAssignedKeys())
    }

    @Test
    fun reorderThemes() = runTest {
        val a = themes.createTheme("A", ThemeIconKey.Star, 1)
        val b = themes.createTheme("B", ThemeIconKey.Star, 1)
        val c = themes.createTheme("C", ThemeIconKey.Star, 1)
        themes.reorderThemes(listOf(c, a, b))
        assertEquals(listOf(c, a, b), themes.themes.first().map { it.id })
    }

    @Test
    fun favoritesAreCappedAndDeduplicated() = runTest {
        repeat(ThemeDefaults.MAX_FAVORITES) { assertEquals(AddFavoriteResult.Added, favorites.add("p$it/.A")) }
        assertEquals(AddFavoriteResult.AlreadyFavorite, favorites.add("p0/.A"))
        assertEquals(AddFavoriteResult.Full, favorites.add("extra/.A"))

        favorites.remove("p0/.A")
        assertEquals(AddFavoriteResult.Added, favorites.add("extra/.A"))
        assertEquals(listOf("p1/.A", "p2/.A", "p3/.A", "p4/.A", "extra/.A"), favorites.favoriteKeys.first())
    }

    @Test
    fun cleanerRemovesOnlyUninstalledApps() = runTest {
        val t = themes.createTheme("T", ThemeIconKey.Star, 1, listOf("kept/.A", "gone/.A", "gone/.B"))
        favorites.setFavorites(listOf("gone/.A", "kept/.A"))
        val installed = setOf("kept/.A")
        val cleaner = UninstalledAppCleaner({ it in installed }, db)

        assertEquals(emptyList<String>(), cleaner.prune("kept"))
        val removed = cleaner.prune()

        assertEquals(setOf("gone/.A", "gone/.B"), removed.toSet())
        assertEquals(listOf("kept/.A"), themes.themes.first().single { it.id == t }.appKeys)
        assertEquals(listOf("kept/.A"), favorites.favoriteKeys.first())
    }

    @Test
    fun themeWithOwnDockKeepsItSeparateFromTheSharedDock() = runTest {
        val t = themes.createTheme(ThemeDraft("Car", ThemeIconKey.Travel, 1, ownDock = true))
        favorites.setFavorites(listOf("shared/.A"))
        favorites.add("car/.A", dockId = t)

        val docks = favorites.docks.first()
        assertEquals(listOf("shared/.A"), docks[FavoriteEntity.SHARED_DOCK])
        assertEquals(listOf("car/.A"), docks[t])

        // Deleting the theme removes its dock too.
        themes.createTheme("Other", ThemeIconKey.Star, 1)
        themes.deleteTheme(t)
        assertEquals(null, favorites.docks.first()[t])
    }

    @Test
    fun themeExtrasAndRulesRoundTrip() = runTest {
        val rules = listOf(ThemeRule.WORK_HOURS, ThemeRule.Controller, ThemeRule.Bluetooth("AA:BB", "Car"))
        val id = themes.createTheme(
            ThemeDraft("Work", ThemeIconKey.Work, 2, listOf("a/.A"), "/w.img", SoundProfile.DndPriority, ownDock = true, rules = rules),
        )
        val saved = themes.themes.first().single { it.id == id }
        assertEquals(SoundProfile.DndPriority, saved.soundProfile)
        assertEquals("/w.img", saved.wallpaperPath)
        assertTrue(saved.ownDock)
        assertEquals(rules.toSet(), saved.rules.toSet())
    }
}
