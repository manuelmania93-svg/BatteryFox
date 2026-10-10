package com.batteryfox.app.domain.usecase

import com.batteryfox.app.core.engine.BatteryCapacityEstimator
import com.batteryfox.app.core.engine.BatteryChargeSample
import com.batteryfox.app.core.engine.CapacityAnalysis

class AnalyzeCapacity {
    operator fun invoke(samples: List<BatteryChargeSample>): CapacityAnalysis = BatteryCapacityEstimator.analyze(samples)
    fun progress(analysis: CapacityAnalysis): String {
        val reasons = analysis.rejections.entries.joinToString("; ") { "${it.key.explanation} (${it.value})" }
        val progress = "${analysis.usableWindowCount} consistent windows; at least 3 needed."
        return if (reasons.isEmpty()) progress else "$progress Skipped: $reasons"
    }
}
