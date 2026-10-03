package com.batteryfox.app.core.engine

import kotlin.math.abs
import kotlin.math.max

data class BatteryChargeSample(
    val timestamp: Long,
    val levelPercent: Int,
    val chargeCounterUah: Long
)

data class BatteryCapacityEstimate(
    val capacityMah: Int,
    val lowerBoundMah: Int,
    val upperBoundMah: Int,
    val observationCount: Int,
    val sampleCount: Int,
    val firstObservedAt: Long,
    val lastObservedAt: Long
)

object BatteryCapacityEstimator {

    private const val MIN_SOC_DELTA_PERCENT = 12
    private const val MIN_SAMPLE_INTERVAL_MS = 15 * 60 * 1_000L
    private const val MAX_SAMPLE_GAP_MS = 14 * 24 * 60 * 60 * 1_000L
    private const val MIN_COUNTER_DELTA_UAH = 100_000L
    private const val MIN_CAPACITY_MAH = 500f
    private const val MAX_CAPACITY_MAH = 20_000f
    private const val MIN_OBSERVATIONS = 3

    fun estimate(samples: List<BatteryChargeSample>): BatteryCapacityEstimate? {
        val validSamples = samples
            .filter {
                it.timestamp > 0L &&
                    it.levelPercent in 0..100 &&
                    it.chargeCounterUah in 100_000L..30_000_000L
            }
            .sortedBy { it.timestamp }
            .distinctBy { it.timestamp }
        if (validSamples.size < 2) return null

        val estimates = mutableListOf<Pair<BatteryChargeSample, Float>>()
        var anchor = validSamples.first()
        var direction = 0

        for (sample in validSamples.drop(1)) {
            val elapsed = sample.timestamp - anchor.timestamp
            if (elapsed <= 0L) continue
            if (elapsed > MAX_SAMPLE_GAP_MS) {
                anchor = sample
                direction = 0
                continue
            }

            val socDelta = sample.levelPercent - anchor.levelPercent
            val nextDirection = socDelta.compareTo(0)
            if (nextDirection == 0) continue
            if (direction != 0 && nextDirection != direction) {
                anchor = sample
                direction = 0
                continue
            }
            direction = nextDirection
            if (abs(socDelta) < MIN_SOC_DELTA_PERCENT) continue

            val chargeDeltaUah = sample.chargeCounterUah - anchor.chargeCounterUah
            if (
                elapsed >= MIN_SAMPLE_INTERVAL_MS &&
                chargeDeltaUah.compareTo(0) == direction &&
                abs(chargeDeltaUah) >= MIN_COUNTER_DELTA_UAH
            ) {
                val capacityMah = abs(chargeDeltaUah).toFloat() / 1_000f *
                    (100f / abs(socDelta))
                if (capacityMah in MIN_CAPACITY_MAH..MAX_CAPACITY_MAH) {
                    estimates.add(sample to capacityMah)
                }
            }
            anchor = sample
            direction = 0
        }

        if (estimates.size < MIN_OBSERVATIONS) return null

        val values = estimates.map { it.second }.sorted()
        val median = values[values.size / 2]
        val deviations = values.map { abs(it - median) }.sorted()
        val medianDeviation = deviations[deviations.size / 2]
        val outlierThreshold = max(median * 0.20f, medianDeviation * 3f)
        val inliers = estimates.filter { abs(it.second - median) <= outlierThreshold }
        if (inliers.size < MIN_OBSERVATIONS) return null

        val inlierValues = inliers.map { it.second }.sorted()
        val estimate = inlierValues[inlierValues.size / 2]
        val inlierDeviations = inlierValues.map { abs(it - estimate) }.sorted()
        val spread = max(estimate * 0.05f, inlierDeviations[inlierDeviations.size / 2] * 1.4826f)
        val firstObservedAt = validSamples.first().timestamp
        val lastObservedAt = validSamples.last().timestamp

        return BatteryCapacityEstimate(
            capacityMah = estimate.toInt(),
            lowerBoundMah = (estimate - spread).toInt().coerceAtLeast(MIN_CAPACITY_MAH.toInt()),
            upperBoundMah = (estimate + spread).toInt().coerceAtMost(MAX_CAPACITY_MAH.toInt()),
            observationCount = inliers.size,
            sampleCount = validSamples.size,
            firstObservedAt = firstObservedAt,
            lastObservedAt = lastObservedAt
        )
    }
}
