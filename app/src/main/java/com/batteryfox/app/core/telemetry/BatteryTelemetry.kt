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

    fun normalizeCurrent(rawCurrentUa: Int, status: Int): NormalizedBatteryCurrent? {
        if (rawCurrentUa == Int.MIN_VALUE) return null

        val rawMagnitude = abs(rawCurrentUa.toLong())
        // A few OEMs expose mA despite Android's documented microamp unit.
        val usesOemUnitHeuristic = rawMagnitude <= 10_000L
        val magnitudeMa = if (usesOemUnitHeuristic) {
            rawMagnitude.takeIf { it <= 20_000L }?.toInt()
        } else {
            (rawMagnitude / 1_000L).takeIf { it in 1L..20_000L }?.toInt()
        } ?: return null
        val signedMa = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> magnitudeMa
            BatteryManager.BATTERY_STATUS_DISCHARGING -> -magnitudeMa
            else -> if (rawCurrentUa < 0) -magnitudeMa else magnitudeMa
        }
        val source = if (usesOemUnitHeuristic) {
            "OEM raw-unit assumption"
        } else {
            "Android microamps converted to mA"
        }
        return NormalizedBatteryCurrent(signedMa, source)
    }
}
