package com.batteryfox.app.core.parser

import com.batteryfox.app.domain.model.BatteryHealthReport
import com.batteryfox.app.domain.model.DiagnosticEngine
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class UniversalBugReportParser(private val rules: MatcherRules = RemoteMatcherConfig.parseConfig()) {

    data class ParseTelemetry(
        val matchedFields: List<String>,
        val missedFields: List<String>,
        val recognizedVendor: String
    )

    data class ParseResult(
        val report: BatteryHealthReport,
        val telemetry: ParseTelemetry,
        /** SHA-256 (first 16 hex chars) of the bugreport text, used to recognise re-imports. */
        val reportId: String? = null,
        /** Product/build part of the bugreport file name, e.g. "sunfish-RQ3A.210805.001.A1". */
        val reportDeviceId: String? = null,
        /** Capture time parsed from the bugreport file name; null when the name has none. */
        val measuredAtMs: Long? = null
    )

    /**
     * Reads the first bugreport .txt in the ZIP within [limits]. [checkCancelled] is called
     * regularly and should throw (e.g. CancellationException) to abort; [clock] is injectable
     * for tests.
     */
    fun parseZip(
        inputStream: InputStream,
        limits: ImportLimits = ImportLimits(),
        checkCancelled: () -> Unit = {},
        clock: () -> Long = System::currentTimeMillis
    ): ParseResult {
        val deadline = clock() + limits.timeoutMs
        val guard = Guard(limits, deadline, checkCancelled, clock)
        val compressed = CountingInputStream(inputStream) {
            if (it > limits.maxCompressedBytes) throw ImportLimitExceededException("Bug report ZIP is too large.")
        }
        ZipInputStream(compressed).use { zis ->
            var entries = 0
            while (true) {
                guard.check()
                val entry = zis.nextEntry ?: break
                if (++entries > limits.maxEntries) {
                    throw ImportLimitExceededException("Bug report ZIP has too many entries.")
                }
                val entryName = entry.name.lowercase()
                val isReport = entryName.endsWith(".txt") && entryName.contains("bugreport")
                val entryStream = guard.limitedEntry(zis)
                if (isReport) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hashing = object : InputStream() {
                        override fun read(): Int {
                            val b = entryStream.read()
                            if (b >= 0) digest.update(b.toByte())
                            return b
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val n = entryStream.read(b, off, len)
                            if (n > 0) digest.update(b, off, n)
                            return n
                        }
                    }
                    val parsed = parseStream(hashing, limits, guard)
                    val id = digest.digest().joinToString("") { "%02x".format(it) }.take(16)
                    val meta = ReportFileName.parse(entry.name)
                    return parsed.copy(
                        reportId = id,
                        reportDeviceId = meta.deviceId,
                        measuredAtMs = meta.measuredAtMs
                    )
                }
                // Drain a skipped entry through the counters so a bomb hidden in it is caught.
                val buf = ByteArray(16 * 1024)
                while (entryStream.read(buf) >= 0) { /* counted by guard */ }
            }
        }
        throw IllegalArgumentException("No valid bugreport file found in ZIP archive.")
    }

    private class Guard(
        val limits: ImportLimits,
        val deadline: Long,
        val checkCancelled: () -> Unit,
        val clock: () -> Long
    ) {
        var totalBytes = 0L

        fun check() {
            checkCancelled()
            if (clock() > deadline) throw ImportLimitExceededException("Bug report import timed out.")
        }

        fun limitedEntry(source: InputStream): InputStream = object : InputStream() {
            var entryBytes = 0L
            override fun read(): Int {
                val b = source.read()
                if (b >= 0) account(1)
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = source.read(b, off, len)
                if (n > 0) account(n)
                return n
            }
            private fun account(n: Int) {
                entryBytes += n
                totalBytes += n
                if (entryBytes > limits.maxEntryBytes) throw ImportLimitExceededException("Bug report entry is too large.")
                if (totalBytes > limits.maxTotalBytes) throw ImportLimitExceededException("Bug report ZIP expands too large.")
                check()
            }
        }
    }

    private class CountingInputStream(
        private val source: InputStream,
        private val onCount: (Long) -> Unit
    ) : InputStream() {
        private var count = 0L
        override fun read(): Int {
            val b = source.read()
            if (b >= 0) onCount(++count)
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = source.read(b, off, len)
            if (n > 0) { count += n; onCount(count) }
            return n
        }
        override fun close() = source.close()
    }

    private fun readBoundedLines(
        stream: InputStream,
        limits: ImportLimits,
        guard: Guard,
        onLine: (String) -> Unit
    ) {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val buffer = CharArray(8 * 1024)
        val line = StringBuilder()
        var skipping = false
        var lines = 0L
        fun emit() {
            if (!skipping) {
                if (line.isNotEmpty() && line[line.length - 1] == '\r') line.setLength(line.length - 1)
                onLine(line.toString())
            }
            line.setLength(0)
            skipping = false
            if (++lines > limits.maxLines) throw ImportLimitExceededException("Bug report has too many lines.")
        }
        while (true) {
            guard.check()
            val n = reader.read(buffer)
            if (n < 0) break
            for (i in 0 until n) {
                val c = buffer[i]
                if (c == '\n') {
                    emit()
                } else if (!skipping) {
                    if (line.length >= limits.maxLineChars) { skipping = true; line.setLength(0) } else line.append(c)
                }
            }
        }
        if (line.isNotEmpty() || skipping) emit()
    }

    private fun parseStream(stream: InputStream, limits: ImportLimits, guard: Guard): ParseResult {
        var cycleCount: Int? = null
        var asocHealth: Float? = null
        var designCap: Int? = null
        var estimatedCap: Int? = null
        val vendors = linkedSetOf<String>()

        val matched = linkedSetOf<String>()

        readBoundedLines(stream, limits, guard) { line ->
            rules.aospAsoc.find(line)?.let {
                val value = it.groupValues[1].toFloatOrNull()
                if (value != null && value in 1f..100f) {
                    asocHealth = value
                    vendors.add("AOSP")
                    matched.add("aospAsoc")
                }
            }
            rules.aospCycle.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 0..100_000) {
                    cycleCount = value
                    vendors.add("AOSP")
                    matched.add("aospCycle")
                }
            }
            rules.aospDesign.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 100..50_000) {
                    designCap = value
                    vendors.add("AOSP")
                    matched.add("aospDesign")
                }
            }
            rules.aospEstimated.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 100..50_000) {
                    estimatedCap = value
                    vendors.add("AOSP")
                    matched.add("aospEstimated")
                }
            }

            rules.samsungAsoc.find(line)?.let {
                val value = it.groupValues[1].toFloatOrNull()
                if (asocHealth == null && value != null && value in 1f..100f) {
                    asocHealth = value
                    vendors.add("SAMSUNG_ONEUI")
                    matched.add("samsungAsoc")
                }
            }
            rules.samsungDesign.find(line)?.let {
                val value = it.groupValues[1].toIntOrNull()
                if (value != null && value in 100..50_000) {
                    designCap = designCap ?: value
                    vendors.add("SAMSUNG_ONEUI")
                    matched.add("samsungDesign")
                }
            }

            rules.qcomCycle.find(line)?.let {
                if (cycleCount == null) {
                    val value = it.groupValues[1].toIntOrNull()
                    if (value != null && value in 0..100_000) {
                        cycleCount = value
                        vendors.add("QUALCOMM_BMS")
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
                matchedFields = matched.toList(),
                missedFields = missed,
                recognizedVendor = when {
                    "SAMSUNG_ONEUI" in vendors -> "SAMSUNG_ONEUI"
                    "QUALCOMM_BMS" in vendors -> "QUALCOMM_BMS"
                    "AOSP" in vendors -> "AOSP"
                    else -> "UNKNOWN"
                }
            )
        )
    }
}

/** Extracts device and capture time from names like "bugreport-sunfish-RQ3A.210805.001.A1-2021-08-05-10-30-45.txt". */
object ReportFileName {
    data class Meta(val deviceId: String?, val measuredAtMs: Long?)

    private val pattern = Regex(
        """bugreport-(.+?)-(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})-(\d{2})\.txt$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(entryName: String): Meta {
        val base = entryName.substringAfterLast('/')
        val m = pattern.find(base) ?: return Meta(null, null)
        val (device, y, mo, d, h, mi, sec) = m.destructured
        val ms = try {
            java.time.LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), sec.toInt())
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: java.time.DateTimeException) {
            null
        }
        return Meta(device.takeIf { it.isNotBlank() }, ms)
    }
}
