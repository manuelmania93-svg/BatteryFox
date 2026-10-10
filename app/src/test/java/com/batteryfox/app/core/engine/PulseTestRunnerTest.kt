package com.batteryfox.app.core.engine

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PulseTestRunnerTest {
    private val safe = PulseSafetyState(50, 25f, false, false)
    @Test fun rejectsMissingAndUnsafeStates() {
        for (state in listOf(safe.copy(soc = null), safe.copy(soc = 19), safe.copy(soc = 91),
            safe.copy(temperatureC = null), safe.copy(temperatureC = Float.NaN),
            safe.copy(temperatureC = 46f), safe.copy(plugged = true), safe.copy(plugged = null), safe.copy(throttled = true))) {
            assertNotNull(state.rejection())
        }
        assertNull(safe.rejection())
        assertNull(safe.copy(soc = 20, temperatureC = 10f).rejection())
        assertNull(safe.copy(soc = 90, temperatureC = 45f).rejection())
    }
    @Test fun successSamplesOnceAndAlwaysStopsLoad() = runBlocking {
        var starts = 0; var stops = 0; var checks = 0
        val runner = PulseTestRunner({ checks++; safe }, { starts++; { stops++ } }, {})
        assertEquals(42, runner.pulse { 42 })
        assertEquals(1, starts); assertEquals(1, stops); assertEquals(19, checks)
    }
    @Test fun chargerTemperatureAndThermalChangesAbortAndStopLoad() = runBlocking {
        for (changed in listOf(safe.copy(plugged = true), safe.copy(temperatureC = 46f), safe.copy(throttled = true), safe.copy(soc = 19))) {
            var state = safe; var stops = 0; var sampled = false
            val runner = PulseTestRunner({ state }, { { stops++ } }, { state = changed })
            try { runner.pulse { sampled = true }; fail("Unsafe pulse accepted") } catch (_: IllegalStateException) { }
            assertFalse(sampled); assertEquals(1, stops)
        }
    }
    @Test fun unsafeInitialStateStartsNoLoad() = runBlocking {
        var starts = 0
        val runner = PulseTestRunner({ safe.copy(plugged = true) }, { starts++; {} }, {})
        try { runner.pulse { 1 }; fail() } catch (_: IllegalStateException) { }
        assertEquals(0, starts)
    }
    @Test fun cancellationDuringPulseStopsAndPropagates() = runBlocking {
        var stops = 0
        val loaded = CompletableDeferred<Unit>()
        val runner = PulseTestRunner({ safe }, { loaded.complete(Unit); { stops++ } })
        val job = launch { runner.pulse { fail("Cancelled pulse sampled") } }
        loaded.await(); job.cancelAndJoin()
        assertEquals(1, stops)
        assertTrue(job.isCancelled)
    }
    @Test fun samplingFailureStillStops() = runBlocking {
        var stops = 0
        val runner = PulseTestRunner({ safe }, { { stops++ } }, {})
        try { runner.pulse<Int> { throw IllegalArgumentException("sample") }; fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, stops)
    }
}
