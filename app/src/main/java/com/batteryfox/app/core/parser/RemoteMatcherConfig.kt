package com.batteryfox.app.core.parser

import org.json.JSONObject

data class MatcherRules(
    val aospAsoc: Regex,
    val aospCycle: Regex,
    val aospDesign: Regex,
    val aospEstimated: Regex,
    val samsungUsage: Regex,
    val samsungAsoc: Regex,
    val samsungDesign: Regex,
    val qcomCycle: Regex,
    val qcomChargeFull: Regex,
    val qcomDesign: Regex
)

object RemoteMatcherConfig {

    private const val DEFAULT_CONFIG_JSON = """
    {
      "aospAsoc": "(?:mSavedBatteryAsoc|health_percent):\\s*(\\d+)",
      "aospCycle": "Cycle count:\\s*(\\d+)",
      "aospDesign": "Device battery capacity:\\s*(\\d+)\\s*mAh",
      "aospEstimated": "Estimated battery capacity:\\s*(\\d+)\\s*mAh",
      "samsungUsage": "mSavedBatteryUsage:\\s*(\\d+)",
      "samsungAsoc": "(?:mSecBatteryStateOfHealth|mSavedBatteryAsoc):\\s*(\\d+)",
      "samsungDesign": "mDesignCapacity:\\s*(\\d+)",
      "qcomCycle": "(?:bms_cycle_count|fg_cycle|cycle_count):\\s*(\\d+)",
      "qcomChargeFull": "(?:charge_full|fg_charge_full):\\s*(\\d+)",
      "qcomDesign": "(?:charge_full_design|fg_design_cap):\\s*(\\d+)"
    }
    """

    fun parseConfig(jsonString: String = DEFAULT_CONFIG_JSON): MatcherRules {
        val root = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            JSONObject(DEFAULT_CONFIG_JSON)
        }

        return MatcherRules(
            aospAsoc = Regex(root.optString("aospAsoc"), RegexOption.IGNORE_CASE),
            aospCycle = Regex(root.optString("aospCycle"), RegexOption.IGNORE_CASE),
            aospDesign = Regex(root.optString("aospDesign"), RegexOption.IGNORE_CASE),
            aospEstimated = Regex(root.optString("aospEstimated"), RegexOption.IGNORE_CASE),
            samsungUsage = Regex(root.optString("samsungUsage"), RegexOption.IGNORE_CASE),
            samsungAsoc = Regex(root.optString("samsungAsoc"), RegexOption.IGNORE_CASE),
            samsungDesign = Regex(root.optString("samsungDesign"), RegexOption.IGNORE_CASE),
            qcomCycle = Regex(root.optString("qcomCycle"), RegexOption.IGNORE_CASE),
            qcomChargeFull = Regex(root.optString("qcomChargeFull"), RegexOption.IGNORE_CASE),
            qcomDesign = Regex(root.optString("qcomDesign"), RegexOption.IGNORE_CASE)
        )
    }
}
