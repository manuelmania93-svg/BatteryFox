package com.batteryfox.app.core.parser

/** Hard bounds for importing an untrusted bug-report ZIP. */
data class ImportLimits(
    val maxCompressedBytes: Long = 400L * 1024 * 1024,
    val maxEntries: Int = 10_000,
    val maxEntryBytes: Long = 256L * 1024 * 1024,
    val maxTotalBytes: Long = 512L * 1024 * 1024,
    val maxLines: Long = 5_000_000L,
    val maxLineChars: Int = 64 * 1024,
    val timeoutMs: Long = 90_000L
)

class ImportLimitExceededException(message: String) : IllegalArgumentException(message)
