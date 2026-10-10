package com.batteryfox.app.core.engine

import kotlin.math.abs
import kotlin.math.max

data class BatteryChargeSample(
    val timestamp: Long,
    val levelPercent: Int,
    val chargeCounterUah: Long,
    val isCharging: Boolean? = null,
    val temperatureCelsius: Float? = null,
    val bootId: String? = null,
    val sessionId: String? = null,
    val elapsedRealtimeMs: Long? = null
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

enum class CapacityRejection(val explanation: String) {
    INVALID_SAMPLE("Invalid battery reading"),
    MISSING_CONTEXT("Older samples lack charging, temperature or session context"),
    SESSION_CHANGED("Charging state, boot or collection session changed"),
    TIME_GAP("Observation gap over 2 hours or window over 6 hours"),
    CLOCK_CHANGED("Clock or elapsed-time discontinuity"),
    TEMPERATURE("Temperature outside 0-45 C"),
    COUNTER_DISCONTINUITY("Charge-counter reset, reversal or implausible jump"),
    SMALL_WINDOW("Charge change below 12% or observation shorter than 15 minutes"),
    CAPACITY_OUT_OF_RANGE("Calculated capacity outside 500-20000 mAh"),
    OUTLIER("Window inconsistent with other observations")
}

data class CapacityAnalysis(
    val estimate: BatteryCapacityEstimate?,
    val rejections: Map<CapacityRejection, Int>,
    val usableWindowCount: Int
)

object BatteryCapacityEstimator {

    private const val MIN_SOC_DELTA_PERCENT = 12
    private const val MIN_SAMPLE_INTERVAL_MS = 15 * 60 * 1_000L
    private const val MAX_SAMPLE_GAP_MS = 2 * 60 * 60 * 1_000L
    private const val MAX_WINDOW_MS = 6 * 60 * 60 * 1_000L
    private const val MIN_COUNTER_DELTA_UAH = 100_000L
    private const val MIN_CAPACITY_MAH = 500f
    private const val MAX_CAPACITY_MAH = 20_000f
    private const val MIN_OBSERVATIONS = 3

    fun estimate(samples: List<BatteryChargeSample>): BatteryCapacityEstimate? = analyze(samples).estimate

    fun analyze(samples: List<BatteryChargeSample>): CapacityAnalysis {
        val rejections = linkedMapOf<CapacityRejection, Int>()
        fun reject(reason: CapacityRejection) {
            rejections[reason] = (rejections[reason] ?: 0) + 1
        }
        val orderedSamples = samples.sortedBy { it.timestamp }.distinctBy { it.timestamp }
        val windowStarts = mutableMapOf<Long, Long>()
        val estimates = mutableListOf<Pair<BatteryChargeSample, Float>>()
        var anchor: BatteryChargeSample? = null
        var previous: BatteryChargeSample? = null
        for (sample in orderedSamples) {
            if (sample.timestamp <= 0L || sample.levelPercent !in 0..100 ||
                sample.chargeCounterUah !in 100_000L..30_000_000L) {
                reject(CapacityRejection.INVALID_SAMPLE)
                anchor = null
                previous = null
                continue
            }
            val temperature = sample.temperatureCelsius
            if (sample.isCharging == null || sample.bootId.isNullOrBlank() ||
                sample.sessionId.isNullOrBlank() || sample.elapsedRealtimeMs == null ||
                temperature == null) {
                reject(CapacityRejection.MISSING_CONTEXT)
                anchor = null
                previous = null
                continue
            }
            if (!temperature.isFinite() || temperature !in 0f..45f) {
                reject(CapacityRejection.TEMPERATURE)
                anchor = null
                previous = null
                continue
            }
            val last = previous
            val start = anchor
            if (last == null || start == null) {
                anchor = sample
                previous = sample
                continue
            }
            val elapsed = sample.timestamp - start.timestamp
            val gap = sample.timestamp - last.timestamp
            val monotonicGap = sample.elapsedRealtimeMs - last.elapsedRealtimeMs!!
            val socStep = sample.levelPercent - last.levelPercent
            val counterStep = sample.chargeCounterUah - last.chargeCounterUah
            val direction = if (sample.isCharging) 1 else -1
            val boundary = when {
                sample.bootId != last.bootId || sample.sessionId != last.sessionId ||
                    sample.isCharging != last.isCharging -> CapacityRejection.SESSION_CHANGED
                monotonicGap <= 0L || abs(gap - monotonicGap) > 60_000L -> CapacityRejection.CLOCK_CHANGED
                gap > MAX_SAMPLE_GAP_MS || elapsed > MAX_WINDOW_MS -> CapacityRejection.TIME_GAP
                socStep * direction < 0 || counterStep * direction < -10_000L ||
                    abs(counterStep).toDouble() * 3_600.0 / monotonicGap > 20_000.0 ||
                    (abs(socStep) > 0 && abs(counterStep) > abs(socStep) * 300_000L) ->
                    CapacityRejection.COUNTER_DISCONTINUITY
                else -> null
            }
            previous = sample
            if (boundary != null) {
                reject(boundary)
                anchor = sample
                continue
            }
            val socDelta = sample.levelPercent - start.levelPercent
            if (abs(socDelta) < MIN_SOC_DELTA_PERCENT || elapsed < MIN_SAMPLE_INTERVAL_MS) {
                // These are not independent rejected windows: keep accumulating to the next endpoint.
                continue
            }
            val chargeDeltaUah = sample.chargeCounterUah - start.chargeCounterUah
            if (chargeDeltaUah.compareTo(0) != direction || abs(chargeDeltaUah) < MIN_COUNTER_DELTA_UAH) {
                reject(CapacityRejection.COUNTER_DISCONTINUITY)
            } else {
                val capacityMah = abs(chargeDeltaUah).toFloat() / 1_000f * (100f / abs(socDelta))
                if (capacityMah in MIN_CAPACITY_MAH..MAX_CAPACITY_MAH) {
                    estimates.add(sample to capacityMah)
                    windowStarts[sample.timestamp] = start.timestamp
                } else {
                    reject(CapacityRejection.CAPACITY_OUT_OF_RANGE)
                }
            }
            anchor = sample
        }
        if (anchor != null && previous != null && anchor != previous) reject(CapacityRejection.SMALL_WINDOW)
        if (estimates.size < MIN_OBSERVATIONS) return CapacityAnalysis(null, rejections, estimates.size)

        val values = estimates.map { it.second }.sorted()
        val median = values[values.size / 2]
        val deviations = values.map { abs(it - median) }.sorted()
        val medianDeviation = deviations[deviations.size / 2]
        val outlierThreshold = max(median * 0.20f, medianDeviation * 3f)
        val inliers = estimates.filter { abs(it.second - median) <= outlierThreshold }
        if (inliers.size != estimates.size) {
            rejections[CapacityRejection.OUTLIER] = estimates.size - inliers.size
        }
        if (inliers.size < MIN_OBSERVATIONS) return CapacityAnalysis(null, rejections, inliers.size)

        val inlierValues = inliers.map { it.second }.sorted()
        val estimate = inlierValues[inlierValues.size / 2]
        val inlierDeviations = inlierValues.map { abs(it - estimate) }.sorted()
        val spread = max(estimate * 0.05f, inlierDeviations[inlierDeviations.size / 2] * 1.4826f)
        val firstObservedAt = inliers.minOf { windowStarts.getValue(it.first.timestamp) }
        val lastObservedAt = inliers.maxOf { it.first.timestamp }

        val result = BatteryCapacityEstimate(
            capacityMah = estimate.toInt(),
            lowerBoundMah = (estimate - spread).toInt().coerceAtLeast(MIN_CAPACITY_MAH.toInt()),
            upperBoundMah = (estimate + spread).toInt().coerceAtMost(MAX_CAPACITY_MAH.toInt()),
            observationCount = inliers.size,
            sampleCount = orderedSamples.count { it.timestamp > 0L && it.levelPercent in 0..100 &&
                it.chargeCounterUah in 100_000L..30_000_000L },
            firstObservedAt = firstObservedAt,
            lastObservedAt = lastObservedAt
        )
        return CapacityAnalysis(result, rejections, inliers.size)
    }
}
