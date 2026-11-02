package com.phaseslauncher.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for work that suspends: a cancelled coroutine (e.g. the screen went away mid-import)
 * isn't a failure to report, so its [CancellationException] is rethrown instead of caught.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
