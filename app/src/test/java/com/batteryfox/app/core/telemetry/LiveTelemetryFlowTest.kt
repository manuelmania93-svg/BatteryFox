package com.batteryfox.app.core.telemetry

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class LiveTelemetryFlowTest {
    @Test fun subscribesOnlyWhenCollectedAndReleasesAfterTickerReading() = runBlocking {
        var subscriptions = 0
        var releases = 0
        val flow = liveTelemetryFlow<Int>(10) { emit ->
            subscriptions++
            emit(42)
            val stop: () -> Unit = { releases++ }
            stop
        }
        assertEquals(0, subscriptions)
        val readings = withTimeout(2_000) { flow.take(2).toList() }
        assertEquals(listOf(42, 42), readings)
        assertEquals(1, subscriptions)
        assertEquals(1, releases)
    }
    @Test fun cancellationReleasesSubscriptionAndResubscribeIsFresh() = runBlocking {
        var subscriptions = 0
        var releases = 0
        val started = CompletableDeferred<Unit>()
        val flow = liveTelemetryFlow<Int>(60_000) { emit ->
            subscriptions++
            emit(subscriptions)
            started.complete(Unit)
            val stop: () -> Unit = { releases++ }
            stop
        }
        val job = launch { flow.collect {} }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertEquals(1, releases)
        assertEquals(listOf(2), withTimeout(2_000) { flow.take(1).toList() })
        assertEquals(2, releases)
    }
    @Test fun persistenceAndUiCadencesAreSeparate() {
        assertEquals(5_000L, BatteryMonitoringPolicy.UI_INTERVAL_MS)
        assertEquals(900_000L, BatteryMonitoringPolicy.SAMPLE_INTERVAL_MS)
    }
}
