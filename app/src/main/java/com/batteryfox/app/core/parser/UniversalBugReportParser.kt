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
                if (entry.name.endsWith(".txt") && entry.name.contains("bugreport")) {
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
        var vendorDetected = "GENERIC_AOSP"
        var mfgDateString: String? = null
        var totalChargingHours: Long? = null

        val mfgRegex = Regex("""(?:mfg_date|manufacture_date|battery_mfg|mSavedBatteryMfgDate):\\s*([\\d\\-/]+)""", RegexOption.IGNORE_CASE)
        val chargingHoursRegex = Regex("""(?:total_charging_time|charge_time_total):\\s*(\\d+)""", RegexOption.IGNORE_CASE)

        val matched = mutableListOf<String>()

        stream.bufferedReader().forEachLine { line ->
            rules.aospAsoc.find(line)?.let {
                asocHealth = it.groupValues[1].toFloatOrNull()
                matched.add("aospAsoc")
            }
            rules.aospCycle.find(line)?.let {
                cycleCount = it.groupValues[1].toIntOrNull()
                matched.add("aospCycle")
            }
            rules.aospDesign.find(line)?.let {
                designCap = it.groupValues[1].toIntOrNull()
                matched.add("aospDesign")
            }
            rules.aospEstimated.find(line)?.let {
                estimatedCap = it.groupValues[1].toIntOrNull()
                matched.add("aospEstimated")
            }

            rules.samsungAsoc.find(line)?.let {
                if (asocHealth == null) asocHealth = it.groupValues[1].toFloatOrNull()
                vendorDetected = "SAMSUNG_ONEUI"
                matched.add("samsungAsoc")
            }
            rules.samsungUsage.find(line)?.let {
                if (cycleCount == null) {
                    val raw = it.groupValues[1].toIntOrNull() ?: 0
                    cycleCount = if (raw > 1000) raw / 100 else raw
                    vendorDetected = "SAMSUNG_ONEUI"
                    matched.add("samsungUsage")
                }
            }

            rules.qcomCycle.find(line)?.let {
                if (cycleCount == null) {
                    cycleCount = it.groupValues[1].toIntOrNull()
                    vendorDetected = "QUALCOMM_BMS"
                    matched.add("qcomCycle")
                }
            }
        }

        val design = designCap ?: 5000
        val finalEstimated = estimatedCap ?: ((design * (asocHealth ?: 100f)) / 100f).toInt()
        val finalHealth = asocHealth ?: ((finalEstimated.toFloat() / design.toFloat()) * 100f).coerceIn(0f, 100f)

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
                cycleCount = cycleCount ?: 0,
                designCapacityMah = design,
                currentCapacityMah = finalEstimated,
                internalResistanceMilliOhms = null,
                degradationRatePerMonth = null,
                engineUsed = DiagnosticEngine.BUG_REPORT_STREAM,
                isHardwareBacked = true
            ),
            telemetry = ParseTelemetry(
                matchedFields = matched.distinct(),
                missedFields = missed,
                recognizedVendor = vendorDetected
            )
        )
    }
}
