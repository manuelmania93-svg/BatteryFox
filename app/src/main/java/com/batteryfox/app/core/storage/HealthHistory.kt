package com.batteryfox.app.core.storage

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class HealthSourceKind {
    /** The device (OEM/firmware) reported a health percentage directly. */
    OEM_DIRECT,
    /** Derived by BatteryFox, e.g. estimated/design capacity ratio. */
    ESTIMATED
}

data class SavedHealthReading(
    /** When the reading was imported into BatteryFox. */
    val importedAt: Long,
    /** When the device measured it, if the report says so; null when unknown. */
    val measuredAt: Long?,
    val healthPercent: Float,
    val source: String,
    val sourceKind: HealthSourceKind,
    /** Identity of the imported report (content hash); null for legacy entries. */
    val reportId: String?,
    /** Device/build the report came from; null when not stated. */
    val deviceId: String?
) {
    /** Time used for ordering and display: measurement time when known, else import time. */
    val effectiveTime: Long get() = measuredAt ?: importedAt

    /** Readings are only comparable within the same kind of measurement on the same device. */
    val seriesKey: String get() = "${sourceKind.name}|${deviceId ?: "unknown-device"}"
}

data class HealthTrend(
    val first: SavedHealthReading,
    val latest: SavedHealthReading,
    /** Number of other, incompatible series that are kept out of this trend. */
    val otherSeriesCount: Int
)

object HealthHistory {
    const val MAX_ENTRIES = 50

    enum class AddOutcome { ADDED, DUPLICATE, BEFORE_RESET }

    data class AddResult(val history: List<SavedHealthReading>, val outcome: AddOutcome)

    fun kindFor(source: String): HealthSourceKind =
        if (source.contains("device-reported", ignoreCase = true)) HealthSourceKind.OEM_DIRECT
        else HealthSourceKind.ESTIMATED

    /**
     * Adds [reading] unless it repeats an already imported report or predates the last battery
     * reset (a report captured before a replacement describes the old battery).
     */
    fun add(
        history: List<SavedHealthReading>,
        reading: SavedHealthReading,
        resetAt: Long
    ): AddResult {
        if (resetAt > 0L && reading.measuredAt != null && reading.measuredAt < resetAt) {
            return AddResult(history, AddOutcome.BEFORE_RESET)
        }
        val duplicate = history.any { existing ->
            if (reading.reportId != null && existing.reportId != null) {
                existing.reportId == reading.reportId
            } else {
                existing.measuredAt != null &&
                    existing.measuredAt == reading.measuredAt &&
                    existing.seriesKey == reading.seriesKey &&
                    existing.healthPercent == reading.healthPercent
            }
        }
        if (duplicate) return AddResult(history, AddOutcome.DUPLICATE)
        val updated = (history + reading).sortedBy { it.effectiveTime }.takeLast(MAX_ENTRIES)
        return AddResult(updated, AddOutcome.ADDED)
    }

    /** Trend over the series of the newest reading; other series are counted, never mixed in. */
    fun trend(history: List<SavedHealthReading>): HealthTrend? {
        if (history.isEmpty()) return null
        val sorted = history.sortedBy { it.effectiveTime }
        val newest = sorted.last()
        val series = sorted.filter { it.seriesKey == newest.seriesKey }
        if (series.size < 2) return null
        val others = sorted.map { it.seriesKey }.distinct().size - 1
        return HealthTrend(series.first(), series.last(), others)
    }

    fun encode(history: List<SavedHealthReading>): String = JSONArray().apply {
        history.takeLast(MAX_ENTRIES).forEach { r ->
            put(
                JSONObject()
                    .put("timestamp", r.importedAt)
                    .put("measuredAt", r.measuredAt ?: JSONObject.NULL)
                    .put("health", r.healthPercent.toDouble())
                    .put("source", r.source)
                    .put("kind", r.sourceKind.name)
                    .put("reportId", r.reportId ?: JSONObject.NULL)
                    .put("deviceId", r.deviceId ?: JSONObject.NULL)
            )
        }
    }.toString()

    /** Reads current and legacy (timestamp/health/source only) entries. */
    fun decode(encoded: String?): List<SavedHealthReading> {
        if (encoded == null) return emptyList()
        return try {
            val array = JSONArray(encoded)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val importedAt = item.optLong("timestamp", 0L)
                val health = item.optDouble("health", Double.NaN).toFloat()
                val source = item.optString("source").takeIf(String::isNotBlank)
                if (importedAt > 0L && health in 1f..100f && source != null) {
                    val kind = runCatching { HealthSourceKind.valueOf(item.optString("kind")) }
                        .getOrDefault(kindFor(source))
                    SavedHealthReading(
                        importedAt = importedAt,
                        measuredAt = if (item.isNull("measuredAt")) null else item.optLong("measuredAt").takeIf { it > 0L },
                        healthPercent = health,
                        source = source,
                        sourceKind = kind,
                        reportId = if (item.isNull("reportId")) null else item.optString("reportId").takeIf(String::isNotBlank),
                        deviceId = if (item.isNull("deviceId")) null else item.optString("deviceId").takeIf(String::isNotBlank)
                    )
                } else {
                    null
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
