package com.batteryfox.app.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryCapacityEstimatorTest {

    @Test
    fun estimatesCapacityFromThreeConsistentChargeCounterWindows() {
        val samples = listOf(
            sample(0, 40, 2_000_000),
            sample(20, 55, 2_750_000),
            sample(40, 70, 3_500_000),
            sample(60, 85, 4_250_000)
        )

        val estimate = BatteryCapacityEstimator.estimate(samples)!!

        assertEquals(5_000, estimate.capacityMah)
        assertEquals(4_750, estimate.lowerBoundMah)
        assertEquals(5_250, estimate.upperBoundMah)
        assertEquals(3, estimate.observationCount)
        assertEquals(4, estimate.sampleCount)
    }

    @Test
    fun ignoresOneInconsistentWindowWhenEnoughConsistentEvidenceRemains() {
        val samples = listOf(
            sample(0, 40, 2_000_000),
            sample(20, 55, 2_750_000),
            sample(40, 70, 4_550_000),
            sample(60, 85, 5_300_000),
            sample(80, 100, 6_050_000)
        )

        val estimate = BatteryCapacityEstimator.estimate(samples)!!

        assertEquals(5_000, estimate.capacityMah)
        assertEquals(3, estimate.observationCount)
    }

    @Test
    fun requiresThreeValidWindowsAndMeaningfulChargeMovement() {
        val tooFewWindows = listOf(
            sample(0, 40, 2_000_000),
            sample(20, 55, 2_750_000),
            sample(40, 70, 3_500_000)
        )
        val inconsistentCounterDirection = listOf(
            sample(0, 40, 2_000_000),
            sample(20, 55, 1_250_000),
            sample(40, 70, 500_000),
            sample(60, 85, 250_000)
        )

        assertNull(BatteryCapacityEstimator.estimate(tooFewWindows))
        assertNull(BatteryCapacityEstimator.estimate(inconsistentCounterDirection))
    }

    @Test
    fun skipsSamplesAfterLongGapsAndRejectsInvalidValues() {
        val samples = listOf(
            sample(0, 40, 2_000_000),
            sample(20, 55, 2_750_000),
            sample(40, 70, 3_500_000),
            sample(60, 85, 4_250_000),
            BatteryChargeSample(80L * 24L * 60L * 60L * 1_000L, 100, 5_000_000)
        )

        val estimate = BatteryCapacityEstimator.estimate(samples)!!
        assertEquals(5_000, estimate.capacityMah)
        assertEquals(5, estimate.sampleCount)
        assertTrue(estimate.upperBoundMah <= 20_000)
    }

    private fun sample(minutes: Long, percent: Int, chargeUah: Long) =
        BatteryChargeSample(minutes * 60_000L + 1L, percent, chargeUah)
}
