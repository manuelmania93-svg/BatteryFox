package com.batteryfox.app.core.storage

import com.batteryfox.app.core.engine.BatteryChargeSample
import org.json.JSONArray
import org.json.JSONObject

/** Pure history rules, shared by reads and writes. Legacy samples stay visible but cannot form windows. */
object ChargeSampleHistory {
    const val INTERVAL_MS = 15 * 60 * 1_000L
    const val RETENTION_MS = 60L * 24 * 60 * 60 * 1_000L
    const val MAX_SAMPLES = 6_000

    fun retain(samples: List<BatteryChargeSample>, now: Long): List<BatteryChargeSample> = samples
        .filter { it.timestamp > 0L && it.timestamp <= now && now - it.timestamp <= RETENTION_MS &&
            it.levelPercent in 0..100 && it.chargeCounterUah in 100_000L..30_000_000L }
        .sortedBy { it.timestamp }.distinctBy { it.timestamp }.takeLast(MAX_SAMPLES)

    fun shouldRecord(last: BatteryChargeSample?, sample: BatteryChargeSample): Boolean =
        last == null || sample.timestamp < last.timestamp ||
            sample.timestamp - last.timestamp >= INTERVAL_MS ||
            last.bootId != sample.bootId || last.sessionId != sample.sessionId ||
            last.isCharging != sample.isCharging

    fun decode(encoded: String?, now: Long): List<BatteryChargeSample> {
        if (encoded == null) return emptyList()
        return try {
            val array = JSONArray(encoded)
            retain((0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                BatteryChargeSample(
                    item.optLong("timestamp", 0L), item.optInt("level", -1), item.optLong("chargeUah", -1L),
                    if (item.isNull("charging")) null else item.optBoolean("charging"),
                    if (item.isNull("temperature")) null else item.optDouble("temperature").toFloat(),
                    item.optString("boot").takeIf { it.isNotBlank() },
                    item.optString("session").takeIf { it.isNotBlank() },
                    if (item.isNull("elapsed")) null else item.optLong("elapsed")
                )
            }, now)
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    fun encode(samples: List<BatteryChargeSample>): String = JSONArray().apply {
        samples.forEach { sample ->
            put(JSONObject().put("timestamp", sample.timestamp).put("level", sample.levelPercent)
                .put("chargeUah", sample.chargeCounterUah).put("charging", sample.isCharging)
                .put("temperature", sample.temperatureCelsius).put("boot", sample.bootId)
                .put("session", sample.sessionId).put("elapsed", sample.elapsedRealtimeMs))
        }
    }.toString()
}
