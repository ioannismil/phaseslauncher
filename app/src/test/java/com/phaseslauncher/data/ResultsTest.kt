package com.phaseslauncher.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsTest {

    @Test
    fun failuresAreCaught() {
        val result = runCatchingCancellable { error("broken file") }
        assertEquals("broken file", result.exceptionOrNull()?.message)
    }

    @Test
    fun successIsPassedThrough() {
        assertEquals(3, runCatchingCancellable { 1 + 2 }.getOrNull())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsRethrown() {
        runCatchingCancellable { throw CancellationException("screen closed") }
    }

    @Test
    fun aCancelledImportReportsNothing() = runTest {
        var reported = false
        lateinit var job: Job
        job = launch {
            runCatchingCancellable { delay(1_000) }.onFailure { reported = true }
        }
        testScheduler.advanceTimeBy(10)
        job.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertFalse("a cancellation was reported as a failure", reported)
    }
}
