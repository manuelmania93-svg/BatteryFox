package com.batteryfox.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UniversalBugReportParserTest {

    private val parser = UniversalBugReportParser()

    @Test
    fun parsesReportedHealthAndAospBatteryFields() {
        val result = parser.parseZip(bugReport(
            """
                health_percent: 87
                Cycle count: 456
                Device battery capacity: 5000 mAh
                Estimated battery capacity: 4350 mAh
            """.trimIndent()
        ))

        assertEquals(87f, result.report.healthPercent!!, 0f)
        assertEquals(456, result.report.cycleCount)
        assertEquals(5000, result.report.designCapacityMah)
        assertEquals(4350, result.report.currentCapacityMah)
        assertEquals("Bug report: device-reported health", result.report.healthSource)
        assertTrue(result.report.isHardwareBacked)
        assertEquals("AOSP", result.telemetry.recognizedVendor)
        assertTrue(result.telemetry.missedFields.isEmpty())
    }

    @Test
    fun derivesHealthOnlyWhenBothCapacitiesArePresentAndPlausible() {
        val result = parser.parseZip(bugReport(
            """
                Device battery capacity: 5000 mAh
                Estimated battery capacity: 4000 mAh
            """.trimIndent()
        ))

        assertEquals(80f, result.report.healthPercent!!, 0.001f)
        assertEquals("Bug report: estimated/design capacity ratio", result.report.healthSource)
        assertFalse(result.report.isHardwareBacked)
    }

    @Test
    fun doesNotInventHealthOrCapacityWhenReportHasNoMeasurement() {
        val result = parser.parseZip(bugReport("Cycle count: 250"))

        assertNull(result.report.healthPercent)
        assertNull(result.report.designCapacityMah)
        assertNull(result.report.currentCapacityMah)
        assertNull(result.report.healthSource)
        assertEquals(250, result.report.cycleCount)
    }

    @Test
    fun rejectsOutOfRangeAndContradictoryFields() {
        val result = parser.parseZip(bugReport(
            """
                health_percent: 140
                Cycle count: -2
                Device battery capacity: 5000 mAh
                Estimated battery capacity: 6000 mAh
            """.trimIndent()
        ))

        assertNull(result.report.healthPercent)
        assertNull(result.report.cycleCount)
        assertEquals(5000, result.report.designCapacityMah)
        assertEquals(6000, result.report.currentCapacityMah)
    }

    @Test
    fun parsesQualcommCycleCountWithoutAssumingCapacityUnits() {
        val result = parser.parseZip(bugReport("bms_cycle_count: 812"))

        assertEquals(812, result.report.cycleCount)
        assertEquals("QUALCOMM_BMS", result.telemetry.recognizedVendor)
        assertNull(result.report.healthPercent)
        assertNull(result.report.designCapacityMah)
    }

    @Test
    fun recognizesSamsungHealthAndCaseInsensitiveBugreportFileNames() {
        val result = parser.parseZip(bugReport(
            """
                mSecBatteryStateOfHealth: 88
                mDesignCapacity: 5000
            """.trimIndent(),
            "BUGREPORT-device.TXT"
        ))

        assertEquals(88f, result.report.healthPercent!!, 0f)
        assertEquals(5000, result.report.designCapacityMah)
        assertEquals("SAMSUNG_ONEUI", result.telemetry.recognizedVendor)
    }

    @Test
    fun rejectsMalformedMatcherConfigurationInsteadOfSilentlyUsingDefaults() {
        try {
            RemoteMatcherConfig.parseConfig("{invalid")
            throw AssertionError("Expected malformed matcher configuration to fail")
        } catch (_: org.json.JSONException) {
        }
    }

    private fun bugReport(contents: String, entryName: String = "bugreport-device.txt"): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(contents.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
