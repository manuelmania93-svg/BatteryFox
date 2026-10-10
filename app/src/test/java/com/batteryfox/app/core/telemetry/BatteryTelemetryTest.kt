package com.batteryfox.app.core.telemetry

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryTelemetryTest {

    @Test
    fun convertsDocumentedMicroampCurrentToMilliampAndPower() {
        val telemetry = BatteryTelemetryReader.fromRaw(
            level = 50,
            scale = 100,
            rawVoltageMv = 3_800,
            rawTemperatureTenthsC = 250,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            currentUa = 1_500_000
        )

        assertEquals(50, telemetry.levelPercent)
        assertEquals(3_800, telemetry.voltageMv)
        assertEquals(25f, telemetry.temperatureCelsius!!, 0f)
        assertEquals(-1_500, telemetry.currentMa)
        assertEquals("Android microamps converted to mA", telemetry.currentSource)
        assertEquals(5.7f, telemetry.wattage!!, 0.001f)
        assertEquals(false, telemetry.isCharging)
    }

    @Test
    fun reportsUnsupportedOrInvalidTelemetryAsUnavailable() {
        val telemetry = BatteryTelemetryReader.fromRaw(
            level = 110,
            scale = 100,
            rawVoltageMv = 0,
            rawTemperatureTenthsC = Int.MIN_VALUE,
            status = -1,
            currentUa = Int.MIN_VALUE
        )

        assertNull(telemetry.levelPercent)
        assertNull(telemetry.voltageMv)
        assertNull(telemetry.temperatureCelsius)
        assertNull(telemetry.currentMa)
        assertNull(telemetry.currentSource)
        assertNull(telemetry.wattage)
        assertNull(telemetry.isCharging)
    }

    @Test
    fun currentDirectionUsesReportedBatteryStatus() {
        val telemetry = BatteryTelemetryReader.fromRaw(
            level = 80,
            scale = 100,
            rawVoltageMv = 4_000,
            rawTemperatureTenthsC = 220,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            currentUa = -750_000
        )

        assertEquals(750, telemetry.currentMa)
        assertEquals(true, telemetry.isCharging)
    }

    @Test
    fun treatsAmbiguousSmallRawValuesAsUnavailableInsteadOfGuessingMilliamps() {
        val telemetry = BatteryTelemetryReader.fromRaw(
            level = 50,
            scale = 100,
            rawVoltageMv = 4_000,
            rawTemperatureTenthsC = 220,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            currentUa = 850
        )

        assertNull(telemetry.currentMa)
        assertNull(telemetry.currentSource)
        assertNull(telemetry.wattage)
    }

    @Test
    fun valuesUpToTenThousandAreNoLongerReinterpretedAsMilliamps() {
        val telemetry = BatteryTelemetryReader.fromRaw(
            level = 50,
            scale = 100,
            rawVoltageMv = 4_000,
            rawTemperatureTenthsC = 220,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            currentUa = 5_000
        )

        // Documented microamps: 5000 uA is 5 mA, not 5000 mA.
        assertEquals(-5, telemetry.currentMa)
        assertEquals("Android microamps converted to mA", telemetry.currentSource)
        assertEquals(0.02f, telemetry.wattage!!, 0.0001f)
    }

    @Test
    fun zeroCurrentIsStillValidAndImplausiblyLargeIsRejected() {
        assertEquals(0, BatteryTelemetryReader.normalizeCurrent(0, BatteryManager.BATTERY_STATUS_FULL)!!.milliAmps)
        assertNull(BatteryTelemetryReader.normalizeCurrent(25_000_000, BatteryManager.BATTERY_STATUS_CHARGING))
    }

    @Test
    fun wattageUsesCorrectedUnits() {
        val telemetry = BatteryTelemetryReader.fromRaw(
            level = 60,
            scale = 100,
            rawVoltageMv = 4_000,
            rawTemperatureTenthsC = 250,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            currentUa = 2_000_000
        )
        assertEquals(8f, telemetry.wattage!!, 0.001f)
    }
}
