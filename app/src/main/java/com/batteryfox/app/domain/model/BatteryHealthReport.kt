package com.batteryfox.app.domain.model

data class BatteryHealthReport(
    val healthPercent: Float,
    val cycleCount: Int,
    val designCapacityMah: Int,
    val currentCapacityMah: Int,
    val internalResistanceMilliOhms: Float? = null,
    val degradationRatePerMonth: Float? = null,
    val engineUsed: DiagnosticEngine = DiagnosticEngine.BUG_REPORT_STREAM,
    val isHardwareBacked: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
