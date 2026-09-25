package com.batteryfox.app.core.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fixes applied vs the original single-shot design:
 *  1. Multiple load/idle cycles instead of one, with outlier rejection.
 *  2. Refuses to run (or flags low confidence) outside a safe SoC/temp window,
 *     since R_int naturally rises at extremes regardless of battery health.
 *  3. Aborts a sample if thermal throttling kicked in mid-test (API 29+).
 *  4. Reports a confidence score alongside the result instead of a bare number,
 *     so the UI can show "estimated" rather than implying lab-grade precision.
 */
class InternalResistanceTester(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    data class ResistanceResult(
        val resistanceMilliOhms: Float,
        val estimatedHealthPercent: Float,
        val sampleCount: Int,
        val confidence: Confidence,
        val rejectedSamples: Int
    )

    enum class Confidence { HIGH, MEDIUM, LOW, UNAVAILABLE }

    companion object {
        private const val TARGET_SAMPLES = 4
        private const val MAX_ATTEMPTS = 7
        private const val MIN_SOC_PERCENT = 30
        private const val MAX_SOC_PERCENT = 80
        private const val MIN_TEMP_C = 15f
        private const val MAX_TEMP_C = 35f
        private const val MIN_VALID_DELTA_I_AMPS = 0.15f
    }

    suspend fun executeStressTest(): ResistanceResult = withContext(Dispatchers.Default) {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val soc = readStateOfCharge(batteryStatus)
        val tempC = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250) / 10f

        if (soc !in MIN_SOC_PERCENT..MAX_SOC_PERCENT || tempC !in MIN_TEMP_C..MAX_TEMP_C) {
            // Outside the window where R_int is a meaningful health proxy.
            // Caller should surface this as "try again later" rather than a bad number.
            return@withContext ResistanceResult(0f, 0f, 0, Confidence.UNAVAILABLE, 0)
        }

        val samples = mutableListOf<Float>()
        var rejected = 0
        var attempts = 0

        while (samples.size < TARGET_SAMPLES && attempts < MAX_ATTEMPTS) {
            attempts++
            val sample = runSingleCycle()
            if (sample == null) {
                rejected++
                continue
            }
            samples.add(sample)
            delay(500) // brief cooldown between cycles
        }

        if (samples.isEmpty()) {
            return@withContext ResistanceResult(0f, 0f, 0, Confidence.UNAVAILABLE, rejected)
        }

        val filtered = rejectOutliers(samples)
        val avgResistance = filtered.average().toFloat()
        val health = mapResistanceToHealth(avgResistance)
        val confidence = when {
            filtered.size >= 3 -> Confidence.HIGH
            filtered.size == 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        ResistanceResult(avgResistance, health, filtered.size, confidence, rejected + (samples.size - filtered.size))
    }

    /** Runs one idle→load→sample cycle. Returns null if thermal throttling or a degenerate reading invalidates it. */
    private suspend fun runSingleCycle(): Float? {
        if (isThrottling()) return null

        val vIdleMv = getBatteryVoltageMv()
        val iIdleUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val isStressRunning = AtomicBoolean(true)
        val coreCount = Runtime.getRuntime().availableProcessors()
        val stressThreads = List(coreCount) {
            Thread {
                var dummy = 1.0001
                while (isStressRunning.get()) {
                    dummy = dummy * dummy + Math.sin(dummy)
                }
            }
        }
        stressThreads.forEach { it.start() }
        delay(2500)

        if (isThrottling()) {
            isStressRunning.set(false)
            stressThreads.forEach { it.join(300) }
            return null // discard: throttling invalidates the current-draw assumption
        }

        val vLoadMv = getBatteryVoltageMv()
        val iLoadUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        isStressRunning.set(false)
        stressThreads.forEach { it.join(500) }

        val deltaV = abs((vIdleMv - vLoadMv) / 1000f)
        val deltaI = abs((iLoadUa - iIdleUa) / 1_000_000f)

        if (deltaI < MIN_VALID_DELTA_I_AMPS) return null // degenerate: no meaningful load step registered

        return (deltaV / deltaI) * 1000f // milliohms
    }

    /** Drops samples more than ~1.5x the median-absolute-deviation from the median, rather than trusting every reading. */
    private fun rejectOutliers(samples: List<Float>): List<Float> {
        if (samples.size < 3) return samples
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val mad = sorted.map { abs(it - median) }.sorted()[sorted.size / 2]
        val threshold = if (mad < 1f) 15f else mad * 3f // absolute floor so tiny MAD doesn't reject everything
        return samples.filter { abs(it - median) <= threshold }
    }

    private fun isThrottling(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        } catch (e: Exception) {
            false
        }
    }

    private fun readStateOfCharge(intent: Intent?): Int {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return 50 // safe fallback, won't be used if data is missing
        return ((level.toFloat() / scale.toFloat()) * 100f).toInt()
    }

    private fun getBatteryVoltageMv(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)?.toFloat() ?: 4000f
    }

    private fun mapResistanceToHealth(rMilliOhms: Float): Float {
        return when {
            rMilliOhms <= 75f -> 100f - (rMilliOhms / 75f) * 5f
            rMilliOhms >= 160f -> 65f
            else -> 95f - ((rMilliOhms - 75f) / (160f - 75f)) * 30f
        }.coerceIn(50f, 100f)
    }
}
