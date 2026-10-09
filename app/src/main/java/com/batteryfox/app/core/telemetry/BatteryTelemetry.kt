package com.batteryfox.app.core.telemetry

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import kotlin.math.abs

data class BatteryTelemetry(
    val levelPercent: Int?,
    val voltageMv: Int?,
    val temperatureCelsius: Float?,
    val currentMa: Int?,
    val currentSource: String?,
    val isCharging: Boolean?,
    val wattage: Float?
)

data class NormalizedBatteryCurrent(val milliAmps: Int, val source: String)

object BatteryTelemetryReader {

    fun read(context: Context, intent: Intent?): BatteryTelemetry {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val rawTemperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val currentUa = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return fromRaw(level, scale, rawVoltage, rawTemperature, status, currentUa)
    }

    fun fromRaw(
        level: Int,
        scale: Int,
        rawVoltageMv: Int,
        rawTemperatureTenthsC: Int,
        status: Int,
        currentUa: Int
    ): BatteryTelemetry {
        val percent = if (scale > 0 && level in 0..scale) (level * 100) / scale else null
        val voltage = rawVoltageMv.takeIf { it in 2_000..20_000 }
        val temperature = (rawTemperatureTenthsC / 10f).takeIf { it in -40f..100f }
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        val normalizedCurrent = normalizeCurrent(currentUa, status)
        val currentMa = normalizedCurrent?.milliAmps
        val wattage = if (voltage != null && currentMa != null) {
            (voltage / 1_000f) * (abs(currentMa) / 1_000f)
        } else {
            null
        }

        return BatteryTelemetry(
            percent,
            voltage,
            temperature,
            currentMa,
            normalizedCurrent?.source,
            charging,
            wattage
        )
    }

    /**
     * BatteryManager.BATTERY_PROPERTY_CURRENT_NOW is documented as microamps, so that is the only
     * unit assumed. Non-zero magnitudes below 1 mA (under 1000 raw) would be implausibly tiny as
     * microamps and are the signature of an OEM reporting mA instead; the unit is ambiguous there,
     * so the value is reported as unavailable instead of guessed. Implausibly large values are
     * rejected too.
     */
    fun normalizeCurrent(rawCurrentUa: Int, status: Int): NormalizedBatteryCurrent? {
        if (rawCurrentUa == Int.MIN_VALUE) return null

        val rawMagnitude = abs(rawCurrentUa.toLong())
        if (rawMagnitude in 1L until MIN_UNAMBIGUOUS_MICROAMPS) return null
        val magnitudeMa = (rawMagnitude / 1_000L).takeIf { it <= MAX_PLAUSIBLE_MA }?.toInt()
            ?: return null
        val signedMa = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> magnitudeMa
            BatteryManager.BATTERY_STATUS_DISCHARGING -> -magnitudeMa
            else -> if (rawCurrentUa < 0) -magnitudeMa else magnitudeMa
        }
        return NormalizedBatteryCurrent(signedMa, "Android microamps converted to mA")
    }

    private const val MIN_UNAMBIGUOUS_MICROAMPS = 1_000L
    private const val MAX_PLAUSIBLE_MA = 20_000L
}
