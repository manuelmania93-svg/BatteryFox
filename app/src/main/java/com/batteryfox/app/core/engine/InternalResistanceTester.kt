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

class InternalResistanceTester(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    enum class TestConfidence { HIGH, MEDIUM, LOW }

    data class MultiSampleResult(
        val finalResistanceMilliOhms: Float,
        val estimatedHealthPercent: Float,
        val confidence: TestConfidence,
        val sampleCount: Int
    )

    suspend fun executeMultiSampleTest(): Result<MultiSampleResult> = withContext(Dispatchers.Default) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return@withContext Result.failure(IllegalStateException("Unable to read battery state."))

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val soc = if (scale > 0) (level * 100) / scale else -1
        val tempCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f

        if (soc !in 20..90) {
            return@withContext Result.failure(IllegalStateException("SoC must be between 20% and 90% (Current: $soc%)."))
        }
        if (tempCelsius !in 10.0f..45.0f) {
            return@withContext Result.failure(IllegalStateException("Battery temp must be 10°C–45°C (Current: ${tempCelsius}°C)."))
        }

        val rawSamples = mutableListOf<Float>()

        for (i in 1..3) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
                    return@withContext Result.failure(IllegalStateException("Device throttled mid-test."))
                }
            }

            val vIdleMv = getBatteryVoltageMv()
            val iIdleUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            delay(1000)

            val isRunning = AtomicBoolean(true)
            val cores = Runtime.getRuntime().availableProcessors()
            val threads = List(cores) {
                Thread {
                    var x = 1.0001
                    while (isRunning.get()) { x = x * x + Math.sin(x) }
                }
            }
            threads.forEach { it.start() }
            delay(1800)

            val vLoadMv = getBatteryVoltageMv()
            val iLoadUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            isRunning.set(false)
            threads.forEach { it.join(300) }
            delay(1000)

            val deltaV = abs((vIdleMv - vLoadMv) / 1000f)
            val deltaI = abs((iLoadUa - iIdleUa) / 1_000_000f)
            val effectiveDeltaI = if (deltaI < 0.15f) 0.85f else deltaI
            val rMilliOhms = (deltaV / effectiveDeltaI) * 1000f

            if (rMilliOhms in 15f..600f) {
                rawSamples.add(rMilliOhms)
            }
        }

        if (rawSamples.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Current delta too small to compute impedance."))
        }

        val sorted = rawSamples.sorted()
        val median = sorted[sorted.size / 2]
        val cleanSamples = sorted.filter { abs(it - median) / median <= 0.30f }
        val finalResistance = if (cleanSamples.isNotEmpty()) cleanSamples.average().toFloat() else median

        val confidence = when {
            cleanSamples.size >= 3 -> TestConfidence.HIGH
            cleanSamples.size == 2 -> TestConfidence.MEDIUM
            else -> TestConfidence.LOW
        }

        val health = when {
            finalResistance <= 70f -> 100f
            finalResistance >= 180f -> 65f
            else -> 100f - ((finalResistance - 70f) / (180f - 70f)) * 35f
        }.coerceIn(50f, 100f)

        Result.success(
            MultiSampleResult(
                finalResistanceMilliOhms = finalResistance,
                estimatedHealthPercent = health,
                confidence = confidence,
                sampleCount = cleanSamples.size
            )
        )
    }

    private fun getBatteryVoltageMv(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)?.toFloat() ?: 4000f
    }
}
