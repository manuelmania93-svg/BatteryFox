package com.batteryfox.app.core.parser

import org.json.JSONObject

data class MatcherRules(
    val aospAsoc: Regex,
    val aospCycle: Regex,
    val aospDesign: Regex,
    val aospEstimated: Regex,
    val samsungAsoc: Regex,
    val samsungDesign: Regex,
    val qcomCycle: Regex
)

object RemoteMatcherConfig {

    private const val DEFAULT_CONFIG_JSON = """
    {
      "aospAsoc": "(?:mSavedBatteryAsoc|health_percent):\\s*(\\d+(?:\\.\\d+)?)",
      "aospCycle": "Cycle count:\\s*(\\d+)",
      "aospDesign": "Device battery capacity:\\s*(\\d+)\\s*mAh",
      "aospEstimated": "Estimated battery capacity:\\s*(\\d+)\\s*mAh",
      "samsungAsoc": "(?:mSecBatteryStateOfHealth|mSavedBatteryAsoc):\\s*(\\d+)",
      "samsungDesign": "mDesignCapacity:\\s*(\\d+)",
      "qcomCycle": "(?:bms_cycle_count|fg_cycle|cycle_count):\\s*(\\d+)"
    }
    """

    fun parseConfig(jsonString: String = DEFAULT_CONFIG_JSON): MatcherRules {
        val root = JSONObject(jsonString)
        fun rule(name: String): Regex {
            val pattern = root.optString(name).takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Missing matcher pattern: $name")
            return Regex(pattern, RegexOption.IGNORE_CASE)
        }

        return MatcherRules(
            aospAsoc = rule("aospAsoc"),
            aospCycle = rule("aospCycle"),
            aospDesign = rule("aospDesign"),
            aospEstimated = rule("aospEstimated"),
            samsungAsoc = rule("samsungAsoc"),
            samsungDesign = rule("samsungDesign"),
            qcomCycle = rule("qcomCycle")
        )
    }
}
