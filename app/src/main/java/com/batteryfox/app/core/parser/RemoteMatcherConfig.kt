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
      "aospAsoc": "^\\s*health_percent:\\s*(\\d+(?:\\.\\d+)?)\\s*$",
      "aospCycle": "^\\s*Cycle count:\\s*(\\d+)\\s*$",
      "aospDesign": "^\\s*Device battery capacity:\\s*(\\d+)\\s*mAh\\s*$",
      "aospEstimated": "^\\s*Estimated battery capacity:\\s*(\\d+)\\s*mAh\\s*$",
      "samsungAsoc": "^\\s*(?:mSecBatteryStateOfHealth|mSavedBatteryAsoc):\\s*(\\d+)\\s*$",
      "samsungDesign": "^\\s*mDesignCapacity:\\s*(\\d+)\\s*$",
      "qcomCycle": "^\\s*(?:bms_cycle_count|fg_cycle|cycle_count):\\s*(\\d+)\\s*$"
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
