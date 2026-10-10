package com.batteryfox.app.core.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OemFixtureTest {
    private fun parse(text: String): UniversalBugReportParser.ParseResult {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use {
            it.putNextEntry(ZipEntry("bugreport-test.txt"))
            it.write(text.toByteArray())
            it.closeEntry()
        }
        return UniversalBugReportParser().parseZip(ByteArrayInputStream(bytes.toByteArray()))
    }
    @Test fun actualSamsungUnsupportedHealthNeverBecomesTwoPercent() {
        val fixture = javaClass.getResource("/oem/samsung-a20e.txt")!!.readText()
        val report = parse(fixture).report
        assertNull(report.healthPercent)
        assertNull(report.cycleCount)
        assertFalse(report.isHardwareBacked)
    }
    @Test fun samsungAsocIsNotAnAospMatch() {
        val result = parse("mSavedBatteryAsoc: 94")
        assertEquals(94f, result.report.healthPercent!!, 0f)
        assertEquals("SAMSUNG_ONEUI", result.telemetry.recognizedVendor)
        assertEquals(listOf("samsungAsoc"), result.telemetry.matchedFields)
    }
    @Test fun genericAospFieldsDoNotOverwriteSamsungVendor() {
        for (text in listOf("mSavedBatteryAsoc: 94\nCycle count: 12", "Cycle count: 12\nmSavedBatteryAsoc: 94")) {
            assertEquals("SAMSUNG_ONEUI", parse(text).telemetry.recognizedVendor)
        }
    }
    @Test fun fieldNamesAndNumbersMustMatchWholeLines() {
        val result = parse("not_health_percent: 95\nmSavedBatteryAsoc: 9.5\nmDesignCapacity: 5000junk\ncycle_count: 4.5")
        assertNull(result.report.healthPercent)
        assertNull(result.report.designCapacityMah)
        assertNull(result.report.cycleCount)
    }
    @Test fun syntheticQualcommCycleDoesNotOverrideAospCount() {
        val result = parse("Cycle count: 12\ncycle_count: 99")
        assertEquals(12, result.report.cycleCount)
    }
}
