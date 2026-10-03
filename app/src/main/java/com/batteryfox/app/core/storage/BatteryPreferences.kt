package com.batteryfox.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SavedHealthReading(
    val timestamp: Long,
    val healthPercent: Float,
    val source: String
)

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
        private const val KEY_PARSED_HEALTH_SOURCE = "key_parsed_health_source"
        private const val KEY_PARSED_HEALTH_TIMESTAMP = "key_parsed_health_timestamp"
        private const val KEY_HEALTH_HISTORY = "key_health_history"
        private const val KEY_VALIDATED_REPORT_VERSION = "key_validated_report_version"
        private const val VALIDATED_REPORT_VERSION = 1
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

    fun saveBugReportData(
        health: Float?,
        cycles: Int?,
        designMah: Int?,
        availableMah: Int?,
        healthSource: String?
    ) {
        val editor = prefs.edit()
        val validHealth = health?.takeIf { it in 1f..100f }
        val validSource = healthSource?.takeIf(String::isNotBlank)
        val validCycles = cycles?.takeIf { it in 0..100_000 }
        val validDesign = designMah?.takeIf { it in 100..50_000 }
        val validAvailable = availableMah?.takeIf { it in 100..50_000 }
        editor.putInt(KEY_VALIDATED_REPORT_VERSION, VALIDATED_REPORT_VERSION)
        if (validHealth != null && validSource != null) {
            val timestamp = System.currentTimeMillis()
            editor.putFloat(KEY_PARSED_HEALTH, validHealth)
                .putString(KEY_PARSED_HEALTH_SOURCE, validSource)
                .putLong(KEY_PARSED_HEALTH_TIMESTAMP, timestamp)
            val history = getHealthHistory().toMutableList()
            history.add(SavedHealthReading(timestamp, validHealth, validSource))
            editor.putString(
                KEY_HEALTH_HISTORY,
                JSONArray().apply {
                    history.takeLast(50).forEach { reading ->
                        put(
                            JSONObject()
                                .put("timestamp", reading.timestamp)
                                .put("health", reading.healthPercent.toDouble())
                                .put("source", reading.source)
                        )
                    }
                }.toString()
            )
        } else {
            editor.remove(KEY_PARSED_HEALTH)
                .remove(KEY_PARSED_HEALTH_SOURCE)
                .remove(KEY_PARSED_HEALTH_TIMESTAMP)
        }
        if (validCycles != null) editor.putInt(KEY_PARSED_CYCLES, validCycles) else editor.remove(KEY_PARSED_CYCLES)
        if (validDesign != null) editor.putInt(KEY_PARSED_DESIGN_MAH, validDesign) else editor.remove(KEY_PARSED_DESIGN_MAH)
        if (validAvailable != null) editor.putInt(KEY_PARSED_AVAILABLE_MAH, validAvailable) else editor.remove(KEY_PARSED_AVAILABLE_MAH)
        editor.apply()
    }

    fun getSavedParsedHealth(): Float? {
        if (getSavedParsedHealthSource().isNullOrBlank() || getSavedParsedHealthTimestamp() == null) return null
        val h = prefs.getFloat(KEY_PARSED_HEALTH, -1f)
        return h.takeIf { it in 1f..100f }
    }

    fun getSavedParsedCycles(): Int? {
        if (!hasValidatedReport()) return null
        val c = prefs.getInt(KEY_PARSED_CYCLES, -1)
        return c.takeIf { it in 0..100_000 }
    }

    fun getSavedDesignMah(): Int? {
        if (!hasValidatedReport()) return null
        val d = prefs.getInt(KEY_PARSED_DESIGN_MAH, -1)
        return d.takeIf { it in 100..50_000 }
    }

    fun getSavedAvailableMah(): Int? {
        if (!hasValidatedReport()) return null
        val a = prefs.getInt(KEY_PARSED_AVAILABLE_MAH, -1)
        return a.takeIf { it in 100..50_000 }
    }

    fun getSavedParsedHealthSource(): String? = prefs.getString(KEY_PARSED_HEALTH_SOURCE, null)

    fun getSavedParsedHealthTimestamp(): Long? =
        prefs.getLong(KEY_PARSED_HEALTH_TIMESTAMP, 0L).takeIf { it > 0L }

    fun getHealthHistory(): List<SavedHealthReading> {
        val encoded = prefs.getString(KEY_HEALTH_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(encoded)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val timestamp = item.optLong("timestamp", 0L)
                val health = item.optDouble("health", Double.NaN).toFloat()
                val source = item.optString("source").takeIf(String::isNotBlank)
                if (timestamp > 0L && health in 1f..100f && source != null) {
                    SavedHealthReading(timestamp, health, source)
                } else {
                    null
                }
            }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    private fun hasValidatedReport(): Boolean =
        prefs.getInt(KEY_VALIDATED_REPORT_VERSION, 0) == VALIDATED_REPORT_VERSION
}
