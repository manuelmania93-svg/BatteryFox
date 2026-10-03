package com.batteryfox.app.core.parser

import com.batteryfox.app.domain.model.BatteryHealthReport
import com.batteryfox.app.domain.model.DiagnosticEngine
import java.io.InputStream
import java.util.zip.ZipInputStream

class UniversalBugReportParser(private val rules: MatcherRules = RemoteMatcherConfig.parseConfig()) {

    data class ParseTelemetry(
        val matchedFields: List<String>,
        val missedFields: List<String>,
        val recognizedVendor: String
    )

    data class ParseResult(
        val report: BatteryHealthReport,
        val telemetry: ParseTelemetry
    )

    fun parseZip(inputStream: InputStream): ParseResult {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                if (entryName.endsWith(".txt") && entryName.contains("bugreport")) {
                    return parseStream(zis)
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalArgumentException("No valid bugreport file found in ZIP archive.")
    }

    private fun parseStream(stream: InputStream): ParseResult {
        var cycleCount: Int? = null
        var asocHealth: Float? = null
        var designCap: Int? = null
        var estimatedCap: Int? = null
        var vendorDetected = "UNKNOWN"

        val matched = mutableListOf<String>()

        stream.bufferedReader().forEachLine { line ->
            rules.aospAsoc.find(line)?.let {
                val value = it.groupValues[1].toFloatOrNull()
                if (value != null && value in 1f..100f) {
                    asocHealth = value
                    vendorDetected = "AOSP"
                    matched.add("aospAsoc")
                }
            }
            rules.aospCycle.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 0..100_000) {
                    cycleCount = value
                    vendorDetected = "AOSP"
                    matched.add("aospCycle")
                }
            }
            rules.aospDesign.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 100..50_000) {
                    designCap = value
                    vendorDetected = "AOSP"
                    matched.add("aospDesign")
                }
            }
            rules.aospEstimated.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 100..50_000) {
                    estimatedCap = value
                    vendorDetected = "AOSP"
                    matched.add("aospEstimated")
                }
            }

            rules.samsungAsoc.find(line)?.let {
                val value = it.groupValues[1].toFloatOrNull()
                if (asocHealth == null && value != null && value in 1f..100f) {
                    asocHealth = value
                    vendorDetected = "SAMSUNG_ONEUI"
                    matched.add("samsungAsoc")
                }
            }
            rules.samsungDesign.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 100..50_000) {
                    designCap = designCap ?: value
                    vendorDetected = "SAMSUNG_ONEUI"
                    matched.add("samsungDesign")
                }
            }

            rules.qcomCycle.find(line)?.let {
                if (cycleCount == null) {
                    val value = it.groupValues[1].toIntOrNull()
                    if (value != null && value in 0..100_000) {
                        cycleCount = value
                        vendorDetected = "QUALCOMM_BMS"
                        matched.add("qcomCycle")
                    }
                }
            }
        }

        val design = designCap
        val estimated = estimatedCap
        val capacityRatioHealth = if (
            asocHealth == null &&
            design != null &&
            estimated != null &&
            estimated <= design
        ) {
            (estimated.toFloat() / design) * 100f
        } else {
            null
        }
        val finalHealth = asocHealth ?: capacityRatioHealth
        val healthSource = when {
            asocHealth != null -> "Bug report: device-reported health"
            capacityRatioHealth != null -> "Bug report: estimated/design capacity ratio"
            else -> null
        }

        val allExpected = listOf("asoc", "cycles", "designCap", "estimatedCap")
        val missed = allExpected.filter { field ->
            when (field) {
                "asoc" -> asocHealth == null
                "cycles" -> cycleCount == null
                "designCap" -> designCap == null
                "estimatedCap" -> estimatedCap == null
                else -> false
            }
        }

        return ParseResult(
            report = BatteryHealthReport(
                healthPercent = finalHealth,
                cycleCount = cycleCount,
                designCapacityMah = designCap,
                currentCapacityMah = estimatedCap,
                healthSource = healthSource,
                internalResistanceMilliOhms = null,
                degradationRatePerMonth = null,
                engineUsed = DiagnosticEngine.BUG_REPORT_STREAM,
                isHardwareBacked = asocHealth != null
            ),
            telemetry = ParseTelemetry(
                matchedFields = matched.distinct(),
                missedFields = missed,
                recognizedVendor = vendorDetected
            )
        )
    }
}
