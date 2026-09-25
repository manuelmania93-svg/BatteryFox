package com.batteryfox.app.core.parser

import com.batteryfox.app.domain.model.BatteryHealthReport
import com.batteryfox.app.domain.model.DiagnosticEngine
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Consumes RemoteMatcherConfig instead of hardcoded regex constants, so vendor
 * log-format drift is fixed by pushing new JSON, not shipping a new APK.
 * Also reports which fields it failed to find, so parse failures can be logged
 * (field names only — never raw log content) to catch OEM format changes early.
 */
class UniversalBugReportParser(private val matcherConfig: RemoteMatcherConfig) {

    data class ParseOutcome(
        val report: BatteryHealthReport?,
        val matchedFields: Set<String>,
        val missingFields: Set<String>
    )

    fun parseZipStream(inputStream: InputStream): ParseOutcome {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".txt") && entry.name.contains("bugreport")) {
                    return parseTextStream(zis)
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalArgumentException("No valid bugreport dump file found in ZIP archive.")
    }

    private fun parseTextStream(stream: InputStream): ParseOutcome {
        var designCapacity: Int? = null
        var estimatedCapacity: Int? = null
        var cycleCount: Int? = null
        var asocHealth: Float? = null
        val matched = mutableSetOf<String>()

        stream.bufferedReader().forEachLine { line ->
            for ((vendorName, vendor) in matcherConfig.vendors) {
                vendor.patterns.forEach { (field, regex) ->
                    val match = regex.find(line) ?: return@forEach
                    val value = match.groupValues.getOrNull(1) ?: return@forEach
                    when (field) {
                        "asoc" -> if (asocHealth == null) { asocHealth = value.toFloatOrNull(); matched.add("$vendorName.asoc") }
                        "cycle" -> if (cycleCount == null) { cycleCount = value.toIntOrNull(); matched.add("$vendorName.cycle") }
                        "design", "chargeFull" -> if (designCapacity == null) { designCapacity = value.toIntOrNull(); matched.add("$vendorName.$field") }
                        "estimated" -> if (estimatedCapacity == null) { estimatedCapacity = value.toIntOrNull(); matched.add("$vendorName.estimated") }
                        "usage" -> if (cycleCount == null) {
                            val raw = value.toIntOrNull() ?: 0
                            cycleCount = if (raw > 1000) raw / 100 else raw
                            matched.add("$vendorName.usage")
                        }
                    }
                }
            }
        }

        val expectedFields = setOf("design_or_chargeFull", "cycle_or_usage", "asoc_or_estimated")
        val missing = mutableSetOf<String>()
        if (designCapacity == null) missing.add("design_or_chargeFull")
        if (cycleCount == null) missing.add("cycle_or_usage")
        if (asocHealth == null && estimatedCapacity == null) missing.add("asoc_or_estimated")

        if (designCapacity == null && asocHealth == null && estimatedCapacity == null) {
            // Nothing usable found at all — signal this clearly rather than returning a fabricated 5000mAh guess
            return ParseOutcome(report = null, matchedFields = matched, missingFields = expectedFields)
        }

        val design = designCapacity ?: 5000
        val estimated = estimatedCapacity ?: ((design * (asocHealth ?: 100f)) / 100f).toInt()
        val finalHealth = asocHealth ?: ((estimated.toFloat() / design.toFloat()) * 100f).coerceIn(0f, 100f)

        val report = BatteryHealthReport(
            healthPercent = finalHealth,
            cycleCount = cycleCount ?: 0,
            designCapacityMah = design,
            currentCapacityMah = estimated,
            internalResistanceMilliOhms = null,
            degradationRatePerMonth = null,
            engineUsed = DiagnosticEngine.BUG_REPORT_STREAM,
            isHardwareBacked = true
        )
        return ParseOutcome(report, matched, missing)
    }
}
