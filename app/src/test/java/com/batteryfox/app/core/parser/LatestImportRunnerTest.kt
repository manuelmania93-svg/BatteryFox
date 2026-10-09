package com.batteryfox.app.core.parser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestImportRunnerTest {

    @Test
    fun newerImportCancelsOlderAndOnlyLatestResultIsDelivered() = runBlocking {
        val delivered = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val runner = LatestImportRunner<String>(this)

        val first = runner.start({ gate.await(); "first" }, { delivered.add(it) }, { delivered.add("err") })
        val second = runner.start({ "second" }, { delivered.add(it) }, { delivered.add("err") })
        second.join()
        gate.complete(Unit)
        first.join()

        assertEquals(listOf("second"), delivered)
        assertTrue(first.isCancelled)
    }

    @Test
    fun uninterruptibleOlderImportFinishingLateIsStillIgnored() = runBlocking {
        val delivered = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val runner = LatestImportRunner<String>(this)

        val first = runner.start(
            { withContext(NonCancellable) { gate.await(); "stale" } },
            { delivered.add(it) },
            { delivered.add("err") }
        )
        val second = runner.start({ "fresh" }, { delivered.add(it) }, { delivered.add("err") })
        second.join()
        gate.complete(Unit)
        first.join()

        assertEquals(listOf("fresh"), delivered)
    }

    @Test
    fun olderFailureDoesNotOverwriteNewerResult() = runBlocking {
        val delivered = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val runner = LatestImportRunner<String>(this)

        val first = runner.start(
            { withContext(NonCancellable) { gate.await(); throw IllegalStateException("boom") } },
            { delivered.add(it) },
            { delivered.add("old-error") }
        )
        val second = runner.start({ "ok" }, { delivered.add(it) }, { delivered.add("err") })
        second.join()
        gate.complete(Unit)
        first.join()

        assertEquals(listOf("ok"), delivered)
    }

    @Test
    fun cancellationIsNotReportedAsFailureAndCancelSuppressesResults() = runBlocking {
        val delivered = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val runner = LatestImportRunner<String>(this)

        val job = runner.start(
            { withContext(NonCancellable) { gate.await(); "late" } },
            { delivered.add(it) },
            { delivered.add("err") }
        )
        runner.cancel()
        gate.complete(Unit)
        job.join()

        assertTrue(delivered.isEmpty())
    }

    @Test
    fun failureOfLatestIsDelivered() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val runner = LatestImportRunner<String>(this)
        runner.start({ throw ImportLimitExceededException("x") }, {}, { errors.add(it) }).join()
        assertEquals(1, errors.size)
    }
}
