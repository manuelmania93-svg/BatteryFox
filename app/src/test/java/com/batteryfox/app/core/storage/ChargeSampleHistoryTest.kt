package com.batteryfox.app.core.storage

import com.batteryfox.app.core.engine.BatteryChargeSample
import org.junit.Assert.*
import org.junit.Test

class ChargeSampleHistoryTest {
    private val now = ChargeSampleHistory.RETENTION_MS + 1_000_000
    private fun sample(time: Long = now) = BatteryChargeSample(time, 50, 2_500_000, true, 25f, "boot", "session", time)
    @Test fun retainsAgeBoundaryAndDropsFutureExpiredAndInvalidSamples() {
        assertEquals(listOf(sample(now - ChargeSampleHistory.RETENTION_MS), sample()),
            ChargeSampleHistory.retain(listOf(sample(now + 1), sample(1), sample(),
                sample(now - ChargeSampleHistory.RETENTION_MS), sample().copy(levelPercent = -1)), now))
    }
    @Test fun capsAndDeduplicatesOnReads() {
        val samples = (0..6_100).map { sample(now - it) }
        val decoded = ChargeSampleHistory.decode(ChargeSampleHistory.encode(samples + samples), now)
        assertEquals(6_000, decoded.size)
        assertEquals(now, decoded.last().timestamp)
    }
    @Test fun roundTripsFullContext() {
        assertEquals(listOf(sample()), ChargeSampleHistory.decode(ChargeSampleHistory.encode(listOf(sample())), now))
    }
    @Test fun legacyRecordsDoNotInventSessionOrState() {
        val decoded = ChargeSampleHistory.decode("""[{"timestamp":$now,"level":50,"chargeUah":2500000}]""", now).single()
        assertNull(decoded.isCharging)
        assertNull(decoded.bootId)
        assertNull(decoded.sessionId)
        assertNull(decoded.temperatureCelsius)
        assertNull(decoded.elapsedRealtimeMs)
    }
    @Test fun malformedHistoryAndRowsFailSafely() {
        assertTrue(ChargeSampleHistory.decode("bad", now).isEmpty())
        assertTrue(ChargeSampleHistory.decode("[null,1,{}]", now).isEmpty())
        assertTrue(ChargeSampleHistory.decode(null, now).isEmpty())
    }
    @Test fun limitsCadenceButKeepsSessionTransitions() {
        val last = sample(now - 1)
        assertFalse(ChargeSampleHistory.shouldRecord(last, sample()))
        assertTrue(ChargeSampleHistory.shouldRecord(last.copy(timestamp = now - ChargeSampleHistory.INTERVAL_MS), sample()))
        assertTrue(ChargeSampleHistory.shouldRecord(last, sample().copy(isCharging = false)))
        assertTrue(ChargeSampleHistory.shouldRecord(last, sample().copy(bootId = "new")))
        assertTrue(ChargeSampleHistory.shouldRecord(last, sample().copy(sessionId = "new")))
        assertTrue(ChargeSampleHistory.shouldRecord(null, sample()))
    }
    @Test fun handlesWallClockRollback() {
        assertTrue(ChargeSampleHistory.shouldRecord(sample(now + 10), sample()))
    }
}
