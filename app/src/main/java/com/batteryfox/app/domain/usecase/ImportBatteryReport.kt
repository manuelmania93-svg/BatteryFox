package com.batteryfox.app.domain.usecase

import com.batteryfox.app.core.parser.UniversalBugReportParser
import com.batteryfox.app.domain.repository.BatteryRepository
import com.batteryfox.app.core.storage.HealthHistory
import com.batteryfox.app.core.storage.SavedHealthReading
import java.io.InputStream

/** Owns report decoding and persistence, independent of Android's URI and the UI. */
class ImportBatteryReport(private val parser: UniversalBugReportParser, private val repository: BatteryRepository) {
    fun parse(stream: InputStream, checkCancelled: () -> Unit): UniversalBugReportParser.ParseResult =
        parser.parseZip(stream, checkCancelled = checkCancelled)

    data class Saved(val outcome: HealthHistory.AddOutcome, val history: List<SavedHealthReading>,
        val health: Float?, val source: String?, val measuredAt: Long?, val importedAt: Long?)

    fun save(result: UniversalBugReportParser.ParseResult): Saved {
        val report = result.report
        val outcome = repository.saveBugReportData(report.healthPercent, report.cycleCount,
            report.designCapacityMah, report.currentCapacityMah, report.healthSource,
            result.reportId, result.reportDeviceId, result.measuredAtMs)
        return Saved(outcome, repository.getHealthHistory(), repository.getSavedParsedHealth(),
            repository.getSavedParsedHealthSource(), repository.getSavedParsedHealthMeasuredAt(),
            repository.getSavedParsedHealthTimestamp())
    }
}
