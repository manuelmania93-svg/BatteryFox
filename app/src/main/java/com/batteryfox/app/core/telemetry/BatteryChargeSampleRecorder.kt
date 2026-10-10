package com.batteryfox.app.core.telemetry

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID
import com.batteryfox.app.core.engine.BatteryChargeSample
import com.batteryfox.app.core.storage.BatteryPreferences

data class ChargeSampleCollection(
    val samples: List<BatteryChargeSample>,
    val counterAvailable: Boolean
)

object BatteryChargeSampleRecorder {
    // Shared across UI and service; a process restart creates a conservative collection boundary.
    private val collectionSession = ChargeCollectionSession { UUID.randomUUID().toString() }

    @Synchronized
    fun record(
        context: Context,
        batteryIntent: Intent?,
        includeStoredSamples: Boolean = true
    ): ChargeSampleCollection {
        val preferences = BatteryPreferences(context)
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val chargeCounterUah = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val percent = if (scale > 0 && level in 0..scale) (level * 100) / scale else null

        val available = percent != null && chargeCounterUah in 100_000..30_000_000
        val sessionId = collectionSession.observe(available)
        if (percent != null && available) {
            val telemetry = BatteryTelemetryReader.read(context, batteryIntent)
            val samples = preferences.saveChargeSample(
                BatteryChargeSample(
                    timestamp = System.currentTimeMillis(),
                    levelPercent = percent,
                    chargeCounterUah = chargeCounterUah.toLong(),
                    isCharging = telemetry.isCharging,
                    temperatureCelsius = telemetry.temperatureCelsius,
                    bootId = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                        .takeIf { it >= 0 }?.toString() ?: sessionId,
                    sessionId = sessionId,
                    elapsedRealtimeMs = SystemClock.elapsedRealtime()
                )
            )
            return ChargeSampleCollection(
                if (samples.isEmpty() && includeStoredSamples) preferences.getChargeSamples() else samples,
                counterAvailable = true
            )
        }

        return ChargeSampleCollection(
            if (includeStoredSamples) preferences.getChargeSamples() else emptyList(),
            counterAvailable = false
        )
    }
}
