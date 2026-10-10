package com.batteryfox.app.domain.usecase

import com.batteryfox.app.core.parser.UniversalBugReportParser
import com.batteryfox.app.core.engine.BatteryChargeSample
import com.batteryfox.app.core.storage.HealthHistory
import com.batteryfox.app.core.storage.SavedHealthReading
import com.batteryfox.app.domain.repository.BatteryRepository
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportBatteryReportTest {
    private class FakeRepository : BatteryRepository {
        var writes = 0
        var health: Float? = null
        var cycles: Int? = null
        var reportId: String? = null
        var measuredAt: Long? = null
        var outcome = HealthHistory.AddOutcome.ADDED
        override fun saveBugReportData(health: Float?, cycles: Int?, designMah: Int?, availableMah: Int?,
            healthSource: String?, reportId: String?, deviceId: String?, measuredAt: Long?, importedAt: Long): HealthHistory.AddOutcome {
            writes++; this.health = health; this.cycles = cycles; this.reportId = reportId; this.measuredAt = measuredAt
            return outcome
        }
        override fun getSavedParsedHealth() = health
        override fun getSavedParsedHealthSource() = "source"
        override fun getSavedParsedHealthMeasuredAt() = measuredAt
        override fun getSavedParsedHealthTimestamp() = 123L
        override fun getSavedParsedCycles() = cycles
        override fun getSavedDesignMah(): Int? = null
        override fun getSavedAvailableMah(): Int? = null
        override fun getSavedResistance(): Float? = null
        override fun getSavedConfidence(): String? = null
        override fun getHealthHistory() = emptyList<SavedHealthReading>()
        override fun getChargeSamples(now: Long) = emptyList<BatteryChargeSample>()
        override fun resetForNewBattery(now: Long) { health = null }
        override fun saveStressTestResult(resistanceMilliOhms: Float, confidence: String) = Unit
    }
    private fun zip(): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use {
            it.putNextEntry(ZipEntry("bugreport-phone-2026-10-09-12-00-00.txt"))
            it.write("health_percent: 87\nCycle count: 42".toByteArray())
            it.closeEntry()
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
    @Test fun parsingDoesNotWriteAndSaveForwardsIdentityAndMeasurement() {
        val repo = FakeRepository()
        val useCase = ImportBatteryReport(UniversalBugReportParser(), repo)
        val parsed = useCase.parse(zip(), {})
        assertEquals(0, repo.writes)
        val saved = useCase.save(parsed)
        assertEquals(1, repo.writes)
        assertEquals(87f, repo.health!!, 0f)
        assertEquals(42, repo.cycles)
        assertEquals(parsed.reportId, repo.reportId)
        assertEquals(parsed.measuredAtMs, repo.measuredAt)
        assertEquals(HealthHistory.AddOutcome.ADDED, saved.outcome)
        assertEquals(repo.health, saved.health)
    }
    @Test fun repositoryRejectionIsPreservedForUi() {
        val repo = FakeRepository().apply { outcome = HealthHistory.AddOutcome.BEFORE_RESET }
        val useCase = ImportBatteryReport(UniversalBugReportParser(), repo)
        assertEquals(HealthHistory.AddOutcome.BEFORE_RESET, useCase.save(useCase.parse(zip(), {})).outcome)
    }
    @Test fun cancellationDuringDecodeCannotReachPersistence() {
        val repo = FakeRepository()
        val useCase = ImportBatteryReport(UniversalBugReportParser(), repo)
        try {
            useCase.parse(zip()) { throw kotlinx.coroutines.CancellationException("cancel") }
            fail("Expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(0, repo.writes)
    }
}
