package com.batteryfox.app.core.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.batteryfox.app.core.telemetry.BatteryTelemetryReader
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
        val initialStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (
            initialStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            initialStatus == BatteryManager.BATTERY_STATUS_FULL ||
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        ) {
            return@withContext Result.failure(IllegalStateException("Disconnect the charger before running this experimental test."))
        }

        val rawSamples = mutableListOf<Float>()

        for (i in 1..3) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
                    return@withContext Result.failure(IllegalStateException("Device throttled mid-test."))
                }
            }

            val vIdleMv = getBatteryVoltageMv()
                ?: return@withContext Result.failure(IllegalStateException("Battery voltage is unavailable."))
            val iIdle = BatteryTelemetryReader.normalizeCurrent(
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                initialStatus
            )
            if (iIdle == null) {
                return@withContext Result.failure(IllegalStateException("Battery current is unavailable."))
            }
            delay(1000)

            val isRunning = AtomicBoolean(true)
            val cores = Runtime.getRuntime().availableProcessors().coerceAtMost(2)
            val threads = List(cores) {
                Thread {
                    var x = 1.0001
                    while (isRunning.get()) { x = x * x + Math.sin(x) }
                }
            }
            threads.forEach { it.start() }
            val loadSample = try {
                delay(1800)
                getBatteryVoltageMv() to BatteryTelemetryReader.normalizeCurrent(
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                    initialStatus
                )
            } finally {
                isRunning.set(false)
                threads.forEach { it.join(1000) }
            }
            delay(1000)
            val vLoadMv = loadSample.first ?: continue
            val iLoad = loadSample.second ?: continue
            if (iLoad.source != iIdle.source) continue

            val deltaV = abs((vIdleMv - vLoadMv) / 1000f)
            val deltaI = abs(iLoad.milliAmps - iIdle.milliAmps) / 1_000f
            if (deltaI < 0.15f) continue
            val rMilliOhms = (deltaV / deltaI) * 1000f

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

        Result.success(
            MultiSampleResult(
                finalResistanceMilliOhms = finalResistance,
                confidence = confidence,
                sampleCount = cleanSamples.size
            )
        )
    }

    private fun getBatteryVoltageMv(): Float? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        return voltage.takeIf { it in 2_000..20_000 }?.toFloat()
    }
}
