package com.batteryfox.app.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthHistoryTest {

    private fun reading(
        health: Float,
        imported: Long,
        measured: Long? = null,
        kind: HealthSourceKind = HealthSourceKind.OEM_DIRECT,
        report: String? = "r$imported",
        device: String? = "dev"
    ) = SavedHealthReading(imported, measured, health, "src", kind, report, device)

    @Test
    fun repeatedImportOfSameReportIsDeduplicated() {
        val first = HealthHistory.add(emptyList(), reading(90f, 10, report = "abc"), 0L)
        assertEquals(HealthHistory.AddOutcome.ADDED, first.outcome)
        val again = HealthHistory.add(first.history, reading(90f, 99, report = "abc"), 0L)
        assertEquals(HealthHistory.AddOutcome.DUPLICATE, again.outcome)
        assertEquals(1, again.history.size)
    }

    @Test
    fun readingsWithoutReportIdDedupeOnMeasurementTimeAndValue() {
        val a = reading(88f, 10, measured = 5, report = null)
        val h = HealthHistory.add(emptyList(), a, 0L).history
        val dup = HealthHistory.add(h, a.copy(importedAt = 50), 0L)
        assertEquals(HealthHistory.AddOutcome.DUPLICATE, dup.outcome)
    }

    @Test
    fun orderingUsesMeasurementTimeNotImportTime() {
        var h = HealthHistory.add(emptyList(), reading(90f, imported = 100, measured = 50), 0L).history
        h = HealthHistory.add(h, reading(95f, imported = 200, measured = 10), 0L).history
        assertEquals(listOf<Long?>(10L, 50L), h.map { it.measuredAt })
        val trend = HealthHistory.trend(h)!!
        assertEquals(95f, trend.first.healthPercent, 0f)
        assertEquals(90f, trend.latest.healthPercent, 0f)
    }

    @Test
    fun incompatibleSeriesAreNotMixedInTheTrend() {
        val h = listOf(
            reading(98f, 1, measured = 1, kind = HealthSourceKind.OEM_DIRECT),
            reading(70f, 2, measured = 2, kind = HealthSourceKind.ESTIMATED),
            reading(96f, 3, measured = 3, kind = HealthSourceKind.OEM_DIRECT),
            reading(80f, 4, measured = 4, kind = HealthSourceKind.OEM_DIRECT, device = "other-phone")
        )
        // Newest reading is from a different device: a single point, so no trend.
        assertNull(HealthHistory.trend(h))
        val trend = HealthHistory.trend(h.take(3))!!
        assertEquals(98f, trend.first.healthPercent, 0f)
        assertEquals(96f, trend.latest.healthPercent, 0f)
        assertEquals(1, trend.otherSeriesCount)
    }

    @Test
    fun readingMeasuredBeforeBatteryResetIsRejected() {
        val old = reading(60f, imported = 1000, measured = 400)
        assertEquals(HealthHistory.AddOutcome.BEFORE_RESET, HealthHistory.add(emptyList(), old, resetAt = 500).outcome)
        val fresh = reading(100f, imported = 1000, measured = 600)
        assertEquals(HealthHistory.AddOutcome.ADDED, HealthHistory.add(emptyList(), fresh, resetAt = 500).outcome)
        val unknownTime = reading(100f, imported = 1000, measured = null)
        assertEquals(HealthHistory.AddOutcome.ADDED, HealthHistory.add(emptyList(), unknownTime, resetAt = 500).outcome)
    }

    @Test
    fun encodeDecodeRoundTripsAllFields() {
        val h = listOf(reading(91.5f, 7, measured = 3), reading(80f, 9, measured = null, report = null, device = null))
        assertEquals(h, HealthHistory.decode(HealthHistory.encode(h)))
    }

    @Test
    fun legacyStoredEntriesStillLoadAsImportTimeOnly() {
        val legacy = """[{"timestamp":123,"health":87.0,"source":"Bug report: device-reported health"},
            {"timestamp":124,"health":70.0,"source":"Bug report: estimated/design capacity ratio"}]"""
        val h = HealthHistory.decode(legacy)
        assertEquals(2, h.size)
        assertNull(h[0].measuredAt)
        assertEquals(123L, h[0].importedAt)
        assertEquals(HealthSourceKind.OEM_DIRECT, h[0].sourceKind)
        assertEquals(HealthSourceKind.ESTIMATED, h[1].sourceKind)
        assertNull(h[0].reportId)
    }

    @Test
    fun corruptHistoryDecodesToEmpty() {
        assertTrue(HealthHistory.decode("{not json").isEmpty())
        assertTrue(HealthHistory.decode(null).isEmpty())
    }

    @Test
    fun historyIsCapped() {
        var h = emptyList<SavedHealthReading>()
        for (i in 1..60) h = HealthHistory.add(h, reading(90f, i.toLong(), measured = i.toLong()), 0L).history
        assertEquals(HealthHistory.MAX_ENTRIES, h.size)
        assertEquals(60L, h.last().measuredAt)
    }
}
