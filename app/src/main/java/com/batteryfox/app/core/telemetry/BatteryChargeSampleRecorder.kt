package com.batteryfox.app.core.telemetry

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.batteryfox.app.core.engine.BatteryChargeSample
import com.batteryfox.app.core.storage.BatteryPreferences

data class ChargeSampleCollection(
    val samples: List<BatteryChargeSample>,
    val counterAvailable: Boolean
)

object BatteryChargeSampleRecorder {

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

        if (percent != null && chargeCounterUah in 100_000..30_000_000) {
            val samples = preferences.saveChargeSample(
                BatteryChargeSample(
                    timestamp = System.currentTimeMillis(),
                    levelPercent = percent,
                    chargeCounterUah = chargeCounterUah.toLong()
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
