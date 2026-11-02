package com.phaseslauncher.api

import com.phaseslauncher.api.PhaseApi.Target
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.data.themes.ThemeIconKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseApiTest {

    private val work = Theme(1, "Work", ThemeIconKey.Work, 0, emptyList())
    private val workout = Theme(2, " Workout ", ThemeIconKey.Star, 0, emptyList())
    private val bedtime = Theme(3, "Bedtime", ThemeIconKey.Star, 0, emptyList())
    private val themes = listOf(work, workout, bedtime)

    @Test
    fun `names match ignoring case`() {
        assertEquals(bedtime, PhaseApi.resolve(themes, Target.Name("bedtime")))
        assertEquals(work, PhaseApi.resolve(themes, Target.Name("WORK")))
    }

    @Test
    fun `a name matches the whole name, not a prefix`() {
        assertEquals(workout, PhaseApi.resolve(themes, Target.Name("workout")))
        assertNull(PhaseApi.resolve(themes, Target.Name("Wor")))
    }

    @Test
    fun `spaces around a phase's own name don't matter`() {
        assertEquals(workout, PhaseApi.resolve(themes, Target.Name("Workout")))
    }

    @Test
    fun `ids pick the phase even when names repeat`() {
        val twin = Theme(4, "Work", ThemeIconKey.Work, 0, emptyList())
        assertEquals(twin, PhaseApi.resolve(themes + twin, Target.Id(4)))
        assertEquals(work, PhaseApi.resolve(themes + twin, Target.Name("Work")))
    }

    @Test
    fun `unknown phases resolve to nothing`() {
        assertNull(PhaseApi.resolve(themes, Target.Id(99)))
        assertNull(PhaseApi.resolve(themes, Target.Name("Gaming")))
    }
}
