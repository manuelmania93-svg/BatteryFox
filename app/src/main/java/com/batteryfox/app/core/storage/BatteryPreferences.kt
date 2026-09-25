package com.batteryfox.app.core.storage

import android.content.Context
import android.content.SharedPreferences

class BatteryPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("battery_fox_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RESISTANCE = "key_resistance"
        private const val KEY_CONFIDENCE = "key_confidence"
        private const val KEY_TEST_TIMESTAMP = "key_test_timestamp"
        private const val KEY_PARSED_HEALTH = "key_parsed_health"
        private const val KEY_PARSED_CYCLES = "key_parsed_cycles"
        private const val KEY_PARSED_DESIGN_MAH = "key_parsed_design_mah"
        private const val KEY_PARSED_AVAILABLE_MAH = "key_parsed_available_mah"
        private const val KEY_CALIBRATION_STEP = "key_calibration_step"
    }

    fun saveStressTestResult(resistanceMilliOhms: Float, confidence: String) {
        prefs.edit()
            .putFloat(KEY_RESISTANCE, resistanceMilliOhms)
            .putString(KEY_CONFIDENCE, confidence)
            .putLong(KEY_TEST_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getSavedResistance(): Float? {
        val res = prefs.getFloat(KEY_RESISTANCE, -1f)
        return if (res > 0f) res else null
    }

    fun getSavedConfidence(): String? = prefs.getString(KEY_CONFIDENCE, null)

    fun getSavedTestTimestamp(): Long = prefs.getLong(KEY_TEST_TIMESTAMP, 0L)

    fun saveBugReportData(health: Float, cycles: Int, designMah: Int, availableMah: Int) {
        prefs.edit()
            .putFloat(KEY_PARSED_HEALTH, health)
            .putInt(KEY_PARSED_CYCLES, cycles)
            .putInt(KEY_PARSED_DESIGN_MAH, designMah)
            .putInt(KEY_PARSED_AVAILABLE_MAH, availableMah)
            .apply()
    }

    fun getSavedParsedHealth(): Float? {
        val h = prefs.getFloat(KEY_PARSED_HEALTH, -1f)
        return if (h > 0f) h else null
    }

    fun getSavedParsedCycles(): Int? {
        val c = prefs.getInt(KEY_PARSED_CYCLES, -1)
        return if (c >= 0) c else null
    }

    fun getSavedDesignMah(): Int? {
        val d = prefs.getInt(KEY_PARSED_DESIGN_MAH, -1)
        return if (d > 0) d else null
    }

    fun getSavedAvailableMah(): Int? {
        val a = prefs.getInt(KEY_PARSED_AVAILABLE_MAH, -1)
        return if (a > 0) a else null
    }

    fun saveCalibrationStep(stepName: String) {
        prefs.edit().putString(KEY_CALIBRATION_STEP, stepName).apply()
    }

    fun getSavedCalibrationStep(): String = prefs.getString(KEY_CALIBRATION_STEP, "IDLE") ?: "IDLE"
}
