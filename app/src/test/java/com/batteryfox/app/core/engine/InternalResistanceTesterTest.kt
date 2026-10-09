package com.batteryfox.app.core.engine

import android.os.BatteryManager
import com.batteryfox.app.core.telemetry.BatteryTelemetryReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalResistanceTesterTest {

    @Test
    fun resistanceConsumesCorrectedMilliampUnits() {
        // 1.0 A -> 2.0 A step (raw microamps) with a 50 mV sag is 50 milliohms.
        val idle = BatteryTelemetryReader.normalizeCurrent(1_000_000, BatteryManager.BATTERY_STATUS_DISCHARGING)!!
        val load = BatteryTelemetryReader.normalizeCurrent(2_000_000, BatteryManager.BATTERY_STATUS_DISCHARGING)!!

        val r = InternalResistanceTester.resistanceMilliOhms(4_000f, 3_950f, idle.milliAmps, load.milliAmps)

        assertEquals(50f, r!!, 0.01f)
    }

    @Test
    fun tinyCurrentStepIsRejected() {
        assertNull(InternalResistanceTester.resistanceMilliOhms(4_000f, 3_990f, -500, -600))
    }

    @Test
    fun ambiguousRawCurrentNeverReachesTheResistanceMath() {
        assertNull(BatteryTelemetryReader.normalizeCurrent(850, BatteryManager.BATTERY_STATUS_DISCHARGING))
    }
}
