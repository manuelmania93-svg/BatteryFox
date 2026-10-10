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
        val runner = PulseTestRunner(::readSafety, ::startLoad)
        try { runner.checkSafety() } catch (error: IllegalStateException) {
            return@withContext Result.failure(error)
        }

        val rawSamples = mutableListOf<Float>()

        for (i in 1..3) {
            try { runner.checkSafety() } catch (error: IllegalStateException) {
                return@withContext Result.failure(error)
            }
            val vIdleMv = getBatteryVoltageMv()
                ?: return@withContext Result.failure(IllegalStateException("Battery voltage is unavailable."))
            val iIdle = BatteryTelemetryReader.normalizeCurrent(
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                BatteryManager.BATTERY_STATUS_DISCHARGING
            )
            if (iIdle == null) {
                return@withContext Result.failure(IllegalStateException("Battery current is unavailable."))
            }
            delay(1000)

            val loadSample = try {
                runner.pulse {
                    getBatteryVoltageMv() to BatteryTelemetryReader.normalizeCurrent(
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                        BatteryManager.BATTERY_STATUS_DISCHARGING
                    )
                }
            } catch (error: IllegalStateException) {
                return@withContext Result.failure(error)
            }
            delay(1000)
            try { runner.checkSafety() } catch (error: IllegalStateException) {
                return@withContext Result.failure(error)
            }
            val vLoadMv = loadSample.first ?: continue
            val iLoad = loadSample.second ?: continue
            if (iLoad.source != iIdle.source) continue

            val rMilliOhms = resistanceMilliOhms(vIdleMv, vLoadMv, iIdle.milliAmps, iLoad.milliAmps)
                ?: continue

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

    private fun readSafety(): PulseSafetyState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return PulseSafetyState(
            soc = if (scale > 0 && level in 0..scale) level * 100 / scale else null,
            temperatureC = temp.takeIf { it >= 0 }?.div(10f),
            plugged = if (plugged < 0 || status !in 2..5) null else
                plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            throttled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        )
    }

    private fun startLoad(): () -> Unit {
        val running = AtomicBoolean(true)
        val threads = List(Runtime.getRuntime().availableProcessors().coerceIn(1, 2)) {
            Thread {
                var x = 1.0001
                while (running.get()) { x = x * x + Math.sin(x) }
            }.apply { isDaemon = true; name = "BatteryFox-pulse" }
        }
        try { threads.forEach { it.start() } } catch (error: Throwable) {
            running.set(false)
            threads.forEach { it.join(1000) }
            throw error
        }
        return {
            running.set(false)
            threads.forEach { it.join(1000) }
        }
    }

    private fun getBatteryVoltageMv(): Float? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        return voltage.takeIf { it in 2_000..20_000 }?.toFloat()
    }

    companion object {
        /**
         * Resistance from a voltage sag between two current samples. Voltages are in mV and
         * currents in mA (as produced by BatteryTelemetryReader.normalizeCurrent). Returns null
         * when the current step is under 150 mA, too small to be meaningful.
         */
        fun resistanceMilliOhms(vIdleMv: Float, vLoadMv: Float, iIdleMa: Int, iLoadMa: Int): Float? {
            val deltaV = abs(vIdleMv - vLoadMv) / 1000f
            val deltaI = abs(iLoadMa - iIdleMa) / 1_000f
            if (deltaI < 0.15f) return null
            return (deltaV / deltaI) * 1000f
        }
    }
}
