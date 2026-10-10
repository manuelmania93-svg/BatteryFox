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

    @Test
    fun malformedZipFailsCleanly() {
        val garbage = ByteArrayInputStream("this is not a zip".toByteArray())
        try {
            parser.parseZip(garbage)
            throw AssertionError("Expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun truncatedZipFailsInsteadOfHanging() {
        val full = bugReport("Cycle count: 5").readBytes()
        val cut = ByteArrayInputStream(full.copyOf(full.size / 2))
        try {
            parser.parseZip(cut)
            // A truncated entry may still yield the lines that were readable; it must not hang.
        } catch (_: java.io.IOException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun hugeEntryIsRejectedByByteLimit() {
        // ~4 MB of zeros compresses to a few KB: a small decompression bomb against a 1 MB cap.
        val bomb = bugReport("x".repeat(4 * 1024 * 1024))
        try {
            parser.parseZip(bomb, ImportLimits(maxEntryBytes = 1024 * 1024))
            throw AssertionError("Expected limit failure")
        } catch (e: ImportLimitExceededException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test
    fun bombHiddenInSkippedEntryCountsAgainstTotalLimit() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("other.bin"))
            zip.write(ByteArray(3 * 1024 * 1024))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("bugreport-x.txt"))
            zip.write("Cycle count: 5".toByteArray())
            zip.closeEntry()
        }
        try {
            parser.parseZip(ByteArrayInputStream(bytes.toByteArray()), ImportLimits(maxTotalBytes = 1024 * 1024))
            throw AssertionError("Expected limit failure")
        } catch (_: ImportLimitExceededException) {
        }
    }

    @Test
    fun tooManyEntriesAreRejected() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            repeat(50) { i ->
                zip.putNextEntry(ZipEntry("f$i.bin"))
                zip.closeEntry()
            }
        }
        try {
            parser.parseZip(ByteArrayInputStream(bytes.toByteArray()), ImportLimits(maxEntries = 10))
            throw AssertionError("Expected limit failure")
        } catch (e: ImportLimitExceededException) {
            assertTrue(e.message!!.contains("entries"))
        }
    }

    @Test
    fun tooManyLinesAreRejected() {
        try {
            parser.parseZip(bugReport("a\n".repeat(1000)), ImportLimits(maxLines = 100))
            throw AssertionError("Expected limit failure")
        } catch (e: ImportLimitExceededException) {
            assertTrue(e.message!!.contains("lines"))
        }
    }

    @Test
    fun overlongLinesAreSkippedNotBuffered() {
        val result = parser.parseZip(
            bugReport("Cycle count: 1" + "9".repeat(200) + "\nCycle count: 77"),
            ImportLimits(maxLineChars = 100)
        )
        assertEquals(77, result.report.cycleCount)
    }

    @Test
    fun timeoutIsEnforced() {
        var now = 0L
        try {
            parser.parseZip(
                bugReport("a\n".repeat(100)),
                ImportLimits(timeoutMs = 1_000),
                clock = { now.also { now += 600 } }
            )
            throw AssertionError("Expected timeout")
        } catch (e: ImportLimitExceededException) {
            assertTrue(e.message!!.contains("timed out"))
        }
    }

    @Test
    fun cancellationAbortsParsing() {
        var calls = 0
        try {
            parser.parseZip(bugReport("a\n".repeat(100)), checkCancelled = {
                if (++calls > 3) throw kotlinx.coroutines.CancellationException("cancelled")
            })
            throw AssertionError("Expected cancellation")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun sameContentGivesSameReportIdAndFileNameYieldsDeviceAndTime() {
        val name = "bugreport-sunfish-RQ3A.210805.001.A1-2021-08-05-10-30-45.txt"
        val a = parser.parseZip(bugReport("health_percent: 90", name))
        val b = parser.parseZip(bugReport("health_percent: 90", name))
        val c = parser.parseZip(bugReport("health_percent: 91", name))
        assertEquals(a.reportId, b.reportId)
        assertTrue(a.reportId != c.reportId)
        assertEquals("sunfish-RQ3A.210805.001.A1", a.reportDeviceId)
        assertTrue(a.measuredAtMs != null)
        assertNull(parser.parseZip(bugReport("health_percent: 90")).measuredAtMs)
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
