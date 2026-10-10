package com.batteryfox.app.domain.repository

import com.batteryfox.app.core.engine.BatteryChargeSample
import com.batteryfox.app.core.storage.HealthHistory
import com.batteryfox.app.core.storage.SavedHealthReading

/** Storage boundary. All reads/writes are called on IO, never from composables. */
interface BatteryRepository {
    fun getSavedParsedHealth(): Float?
    fun getSavedParsedHealthSource(): String?
    fun getSavedParsedHealthMeasuredAt(): Long?
    fun getSavedParsedHealthTimestamp(): Long?
    fun getSavedParsedCycles(): Int?
    fun getSavedDesignMah(): Int?
    fun getSavedAvailableMah(): Int?
    fun getSavedResistance(): Float?
    fun getSavedConfidence(): String?
    fun getHealthHistory(): List<SavedHealthReading>
    fun getChargeSamples(now: Long = System.currentTimeMillis()): List<BatteryChargeSample>
    fun resetForNewBattery(now: Long = System.currentTimeMillis())
    fun saveStressTestResult(resistanceMilliOhms: Float, confidence: String)
    fun saveBugReportData(health: Float?, cycles: Int?, designMah: Int?, availableMah: Int?,
        healthSource: String?, reportId: String? = null, deviceId: String? = null,
        measuredAt: Long? = null, importedAt: Long = System.currentTimeMillis()): HealthHistory.AddOutcome
}
