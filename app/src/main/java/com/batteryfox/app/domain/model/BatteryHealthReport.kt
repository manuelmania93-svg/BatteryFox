package com.batteryfox.app.domain.model

data class BatteryHealthReport(
    val healthPercent: Float,
    val cycleCount: Int?,
    val designCapacityMah: Int,
    val currentCapacityMah: Int,
    val internalResistanceMilliOhms: Float?,
    val degradationRatePerMonth: Float?,
    val engineUsed: DiagnosticEngine,
    val isHardwareBacked: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
