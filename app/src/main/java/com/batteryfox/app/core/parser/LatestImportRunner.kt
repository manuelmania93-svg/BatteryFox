package com.batteryfox.app.core.parser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs at most one import at a time. Starting a new import cancels the previous one, and only
 * the most recently started import may deliver a result, even if an older one cannot be
 * interrupted and finishes later.
 */
class LatestImportRunner<T>(private val scope: CoroutineScope) {
    private val generation = AtomicLong(0L)
    private var activeJob: Job? = null

    /** Must be called from a single thread (the main thread for a ViewModel). */
    fun start(
        work: suspend () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit
    ): Job {
        activeJob?.cancel()
        val token = generation.incrementAndGet()
        val job = scope.launch {
            val outcome = try {
                Result.success(work())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (generation.get() != token) return@launch
            outcome.fold(onSuccess, onFailure)
        }
        activeJob = job
        return job
    }

    fun isCurrent(token: Long): Boolean = generation.get() == token

    fun cancel() {
        generation.incrementAndGet()
        activeJob?.cancel()
        activeJob = null
    }
}
