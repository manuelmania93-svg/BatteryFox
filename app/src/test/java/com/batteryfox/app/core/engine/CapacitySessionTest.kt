package com.batteryfox.app.core.engine

import org.junit.Assert.*
import org.junit.Test

class CapacitySessionTest {
    private fun sample(minutes: Long, level: Int = 40, counter: Long = 2_000_000) =
        BatteryChargeSample(1L + minutes * 60_000, level, counter, true, 25f, "boot", "session", 1L + minutes * 60_000)
    private fun reason(samples: List<BatteryChargeSample>, reason: CapacityRejection) {
        val result = BatteryCapacityEstimator.analyze(samples)
        assertNull(result.estimate)
        assertTrue(result.rejections.containsKey(reason))
    }
    @Test fun refusesLegacyUnknownContext() = reason(
        listOf(BatteryChargeSample(1, 40, 2_000_000)), CapacityRejection.MISSING_CONTEXT)
    @Test fun separatesChargeAndDischarge() = reason(
        listOf(sample(0), sample(20, 55, 2_750_000).copy(isCharging = false)), CapacityRejection.SESSION_CHANGED)
    @Test fun separatesBoots() = reason(
        listOf(sample(0), sample(20, 55, 2_750_000).copy(bootId = "boot2")), CapacityRejection.SESSION_CHANGED)
    @Test fun separatesCollectorSessions() = reason(
        listOf(sample(0), sample(20, 55, 2_750_000).copy(sessionId = "new")), CapacityRejection.SESSION_CHANGED)
    @Test fun rejectsHotAndNonFiniteTemperatures() {
        for (temp in listOf(46f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            reason(listOf(sample(0).copy(temperatureCelsius = temp)), CapacityRejection.TEMPERATURE)
        }
    }
    @Test fun rejectsCounterReset() = reason(
        listOf(sample(0), sample(20, 55, 500_000)), CapacityRejection.COUNTER_DISCONTINUITY)
    @Test fun detectsJumpEvenWithNoSocMovement() = reason(
        listOf(sample(0), sample(1, 40, 3_000_000)), CapacityRejection.COUNTER_DISCONTINUITY)
    @Test fun rejectsCounterStepTooLargeForSoc() = reason(
        listOf(sample(0), sample(20, 41, 3_000_000)), CapacityRejection.COUNTER_DISCONTINUITY)
    @Test fun rejectsLevelReversal() = reason(
        listOf(sample(0), sample(20, 39, 2_050_000)), CapacityRejection.COUNTER_DISCONTINUITY)
    @Test fun rejectsLongObservationGap() = reason(
        listOf(sample(0), sample(121, 55, 2_750_000)), CapacityRejection.TIME_GAP)
    @Test fun boundsWindowsEvenWithFrequentSamples() = reason(
        (0L..7L).map { sample(it * 60, 40 + it.toInt(), 2_000_000 + it * 50_000) }, CapacityRejection.TIME_GAP)
    @Test fun detectsClockChange() = reason(
        listOf(sample(0), sample(20, 55, 2_750_000).copy(elapsedRealtimeMs = 10 * 60_000L)), CapacityRejection.CLOCK_CHANGED)
    @Test fun detectsMonotonicReset() = reason(
        listOf(sample(20), sample(40, 55, 2_750_000).copy(elapsedRealtimeMs = 1)), CapacityRejection.CLOCK_CHANGED)
    @Test fun reportsSmallWindow() = reason(
        listOf(sample(0), sample(20, 45, 2_250_000)), CapacityRejection.SMALL_WINDOW)
    @Test fun reportsInvalidReading() = reason(
        listOf(sample(0).copy(levelPercent = 101)), CapacityRejection.INVALID_SAMPLE)
    @Test fun estimatesDischargeWithoutChargeWindows() {
        val samples = (0..3).map { sample(it * 20L, 85 - it * 15, 4_250_000L - it * 750_000).copy(isCharging = false) }
        assertEquals(5_000, BatteryCapacityEstimator.estimate(samples)!!.capacityMah)
    }
    @Test fun preservesOutlierExplanation() {
        val samples = listOf(sample(0), sample(20, 55, 2_750_000), sample(40, 70, 4_550_000),
            sample(60, 85, 5_300_000), sample(80, 100, 6_050_000))
        val result = BatteryCapacityEstimator.analyze(samples)
        assertEquals(1, result.rejections[CapacityRejection.OUTLIER])
        assertEquals(3, result.usableWindowCount)
    }
    @Test fun reportsCapacityOutsideRange() = reason(
        listOf(sample(0), sample(20, 90, 2_100_000)), CapacityRejection.CAPACITY_OUT_OF_RANGE)
    @Test fun continuesAfterBadBoundaryWithoutBridgingIt() {
        val samples = listOf(sample(0).copy(bootId = "old")) +
            (0..3).map { sample(20L + it * 20, 40 + it * 15, 2_000_000L + it * 750_000) }
        val result = BatteryCapacityEstimator.analyze(samples)
        assertEquals(3, result.estimate!!.observationCount)
        assertEquals(1, result.rejections[CapacityRejection.SESSION_CHANGED])
    }
    @Test fun invalidMiddleSampleBreaksWindow() {
        val samples = listOf(sample(0), sample(10, 101), sample(20, 55, 2_750_000),
            sample(40, 70, 3_500_000), sample(60, 85, 4_250_000))
        val result = BatteryCapacityEstimator.analyze(samples)
        assertNull(result.estimate)
        assertEquals(2, result.usableWindowCount)
        assertEquals(1, result.rejections[CapacityRejection.INVALID_SAMPLE])
    }
    @Test fun observedDatesExcludeRejectedEndpoints() {
        val samples = listOf(sample(0).copy(bootId = "old")) +
            (0..3).map { sample(20L + it * 20, 40 + it * 15, 2_000_000L + it * 750_000) } +
            sample(100, 100, 5_000_000).copy(temperatureCelsius = 55f)
        val estimate = BatteryCapacityEstimator.estimate(samples)!!
        assertEquals(sample(20).timestamp, estimate.firstObservedAt)
        assertEquals(sample(80).timestamp, estimate.lastObservedAt)
    }

}
