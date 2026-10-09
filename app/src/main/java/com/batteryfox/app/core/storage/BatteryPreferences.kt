package com.batteryfox.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.batteryfox.app.core.engine.BatteryChargeSample
import org.json.JSONArray
import org.json.JSONObject

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
        private const val KEY_PARSED_HEALTH_MEASURED_AT = "key_parsed_health_measured_at"
        private const val KEY_PARSED_HEALTH_KIND = "key_parsed_health_kind"
        private const val KEY_RESET_AT = "key_battery_reset_at"
        private const val KEY_HEALTH_HISTORY = "key_health_history"
        private const val KEY_VALIDATED_REPORT_VERSION = "key_validated_report_version"
        private const val VALIDATED_REPORT_VERSION = 1
        private const val KEY_CHARGE_SAMPLES = "key_charge_samples"
        private const val KEY_LAST_CHARGE_SAMPLE_TIMESTAMP = "key_last_charge_sample_timestamp"
        private val CHARGE_SAMPLE_LOCK = Any()
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

    /**
     * Saves an imported report. Repeated imports of the same report, and reports captured before
     * the last battery reset, change nothing and are reported through the returned outcome.
     */
    fun saveBugReportData(
        health: Float?,
        cycles: Int?,
        designMah: Int?,
        availableMah: Int?,
        healthSource: String?,
        reportId: String? = null,
        deviceId: String? = null,
        measuredAt: Long? = null,
        importedAt: Long = System.currentTimeMillis()
    ): HealthHistory.AddOutcome {
        val validHealth = health?.takeIf { it in 1f..100f }
        val validSource = healthSource?.takeIf(String::isNotBlank)
        var reading: SavedHealthReading? = null
        var updatedHistory: List<SavedHealthReading>? = null
        if (validHealth != null && validSource != null) {
            reading = SavedHealthReading(
                importedAt = importedAt,
                measuredAt = measuredAt,
                healthPercent = validHealth,
                source = validSource,
                sourceKind = HealthHistory.kindFor(validSource),
                reportId = reportId,
                deviceId = deviceId
            )
            val result = HealthHistory.add(getHealthHistory(), reading, getBatteryResetAt())
            if (result.outcome != HealthHistory.AddOutcome.ADDED) return result.outcome
            updatedHistory = result.history
        } else if (reportId != null && getHealthHistory().any { it.reportId == reportId }) {
            return HealthHistory.AddOutcome.DUPLICATE
        }

        val editor = prefs.edit()
        val validCycles = cycles?.takeIf { it in 0..100_000 }
        val validDesign = designMah?.takeIf { it in 100..50_000 }
        val validAvailable = availableMah?.takeIf { it in 100..50_000 }
        editor.putInt(KEY_VALIDATED_REPORT_VERSION, VALIDATED_REPORT_VERSION)
        if (reading != null && updatedHistory != null) {
            editor.putFloat(KEY_PARSED_HEALTH, reading.healthPercent)
                .putString(KEY_PARSED_HEALTH_SOURCE, reading.source)
                .putString(KEY_PARSED_HEALTH_KIND, reading.sourceKind.name)
                .putLong(KEY_PARSED_HEALTH_TIMESTAMP, reading.importedAt)
            val measured = reading.measuredAt
            if (measured != null) {
                editor.putLong(KEY_PARSED_HEALTH_MEASURED_AT, measured)
            } else {
                editor.remove(KEY_PARSED_HEALTH_MEASURED_AT)
            }
            editor.putString(KEY_HEALTH_HISTORY, HealthHistory.encode(updatedHistory))
        } else {
            editor.remove(KEY_PARSED_HEALTH)
                .remove(KEY_PARSED_HEALTH_SOURCE)
                .remove(KEY_PARSED_HEALTH_KIND)
                .remove(KEY_PARSED_HEALTH_TIMESTAMP)
                .remove(KEY_PARSED_HEALTH_MEASURED_AT)
        }
        if (validCycles != null) editor.putInt(KEY_PARSED_CYCLES, validCycles) else editor.remove(KEY_PARSED_CYCLES)
        if (validDesign != null) editor.putInt(KEY_PARSED_DESIGN_MAH, validDesign) else editor.remove(KEY_PARSED_DESIGN_MAH)
        if (validAvailable != null) editor.putInt(KEY_PARSED_AVAILABLE_MAH, validAvailable) else editor.remove(KEY_PARSED_AVAILABLE_MAH)
        editor.apply()
        return HealthHistory.AddOutcome.ADDED
    }

    fun getBatteryResetAt(): Long = prefs.getLong(KEY_RESET_AT, 0L)

    /**
     * Battery replaced / start over: forgets the old battery's imported health, history, cycles,
     * capacities, learned charge samples and resistance reading. Report identities are kept
     * with the history cut-off, so an old report cannot be re-imported as the new battery.
     */
    fun resetForNewBattery(now: Long = System.currentTimeMillis()) {
        synchronized(CHARGE_SAMPLE_LOCK) {
            prefs.edit()
                .putLong(KEY_RESET_AT, now)
                .remove(KEY_PARSED_HEALTH).remove(KEY_PARSED_HEALTH_SOURCE).remove(KEY_PARSED_HEALTH_KIND)
                .remove(KEY_PARSED_HEALTH_TIMESTAMP).remove(KEY_PARSED_HEALTH_MEASURED_AT)
                .remove(KEY_PARSED_CYCLES).remove(KEY_PARSED_DESIGN_MAH).remove(KEY_PARSED_AVAILABLE_MAH)
                .remove(KEY_HEALTH_HISTORY)
                .remove(KEY_CHARGE_SAMPLES).remove(KEY_LAST_CHARGE_SAMPLE_TIMESTAMP)
                .remove(KEY_RESISTANCE).remove(KEY_CONFIDENCE).remove(KEY_TEST_TIMESTAMP)
                .apply()
        }
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

    fun getSavedParsedHealthMeasuredAt(): Long? =
        prefs.getLong(KEY_PARSED_HEALTH_MEASURED_AT, 0L).takeIf { it > 0L }

    fun getHealthHistory(): List<SavedHealthReading> =
        HealthHistory.decode(prefs.getString(KEY_HEALTH_HISTORY, null))

    fun saveChargeSample(sample: BatteryChargeSample): List<BatteryChargeSample> {
        return synchronized(CHARGE_SAMPLE_LOCK) {
            val lastRecordedAt = prefs.getLong(KEY_LAST_CHARGE_SAMPLE_TIMESTAMP, 0L)
            if (lastRecordedAt > 0L && sample.timestamp - lastRecordedAt < 15 * 60 * 1_000L) {
                return@synchronized emptyList()
            }
            val samples = getChargeSamples().toMutableList()
            samples.add(sample)
            val retained = samples
                .filter { sample.timestamp - it.timestamp <= 60L * 24L * 60L * 60L * 1_000L }
                .takeLast(6_000)
            prefs.edit()
                .putLong(KEY_LAST_CHARGE_SAMPLE_TIMESTAMP, sample.timestamp)
                .putString(
                    KEY_CHARGE_SAMPLES,
                    JSONArray().apply {
                        retained.forEach {
                            put(
                                JSONObject()
                                    .put("timestamp", it.timestamp)
                                    .put("level", it.levelPercent)
                                    .put("chargeUah", it.chargeCounterUah)
                            )
                        }
                    }.toString()
                )
                .apply()
            retained
        }
    }

    fun getChargeSamples(): List<BatteryChargeSample> {
        return synchronized(CHARGE_SAMPLE_LOCK) {
            val encoded = prefs.getString(KEY_CHARGE_SAMPLES, null) ?: return@synchronized emptyList()
            try {
                val array = JSONArray(encoded)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.getJSONObject(index)
                    val timestamp = item.optLong("timestamp", 0L)
                    val level = item.optInt("level", -1)
                    val charge = item.optLong("chargeUah", -1L)
                    if (
                        timestamp > 0L &&
                        level in 0..100 &&
                        charge in 100_000L..30_000_000L
                    ) {
                        BatteryChargeSample(timestamp, level, charge)
                    } else {
                        null
                    }
                }.sortedBy { it.timestamp }
            } catch (_: org.json.JSONException) {
                emptyList()
            }
        }
    }

    private fun hasValidatedReport(): Boolean =
        prefs.getInt(KEY_VALIDATED_REPORT_VERSION, 0) == VALIDATED_REPORT_VERSION
}
