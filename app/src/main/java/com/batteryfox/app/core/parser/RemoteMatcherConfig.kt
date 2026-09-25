package com.batteryfox.app.core.parser

import org.json.JSONObject
import java.io.File

/**
 * Fixes the "regex drift" problem: instead of hardcoding vendor regexes into the APK
 * (which means every Samsung/Xiaomi log-format change needs a Play Store release),
 * matchers are loaded from a JSON document that can be:
 *   - bundled as a fallback default (assets/matcher_config.json)
 *   - overridden by a remotely-fetched version (Firebase Remote Config, or any hosted JSON)
 *
 * Example matcher_config.json:
 * {
 *   "version": 3,
 *   "vendors": {
 *     "aosp":     { "asoc": "(?:mSavedBatteryAsoc|health_percent):\\s*(\\d+)",
 *                   "cycle": "Cycle count:\\s*(\\d+)",
 *                   "design": "Device battery capacity:\\s*(\\d+)\\s*mAh",
 *                   "estimated": "Estimated battery capacity:\\s*(\\d+)\\s*mAh" },
 *     "samsung":  { "usage": "mSavedBatteryUsage:\\s*(\\d+)",
 *                   "asoc": "mSecBatteryStateOfHealth:\\s*(\\d+)" },
 *     "qualcomm": { "cycle": "(?:bms_cycle_count|fg_cycle|cycle_count):\\s*(\\d+)",
 *                   "chargeFull": "(?:charge_full|fg_charge_full):\\s*(\\d+)",
 *                   "design": "(?:charge_full_design|fg_design_cap):\\s*(\\d+)" }
 *   }
 * }
 */
data class VendorMatchers(val patterns: Map<String, Regex>)

class RemoteMatcherConfig private constructor(
    val version: Int,
    val vendors: Map<String, VendorMatchers>
) {
    companion object {
        private const val CACHE_FILENAME = "matcher_config_cache.json"

        /** Load order: on-disk cache from a prior remote fetch, else the bundled asset default. */
        fun load(cacheDir: File, bundledAssetJson: String): RemoteMatcherConfig {
            val cacheFile = File(cacheDir, CACHE_FILENAME)
            val json = if (cacheFile.exists()) {
                runCatching { cacheFile.readText() }.getOrDefault(bundledAssetJson)
            } else {
                bundledAssetJson
            }
            return parse(json)
        }

        /** Call after fetching a fresher config (e.g. via Remote Config or a hosted URL fetch). */
        fun updateCache(cacheDir: File, newJson: String): RemoteMatcherConfig {
            val parsed = parse(newJson) // validate before persisting — a malformed push shouldn't brick parsing
            File(cacheDir, CACHE_FILENAME).writeText(newJson)
            return parsed
        }

        private fun parse(json: String): RemoteMatcherConfig {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            val vendorsObj = root.getJSONObject("vendors")
            val vendors = mutableMapOf<String, VendorMatchers>()
            vendorsObj.keys().forEach { vendorKey ->
                val fields = vendorsObj.getJSONObject(vendorKey)
                val patterns = mutableMapOf<String, Regex>()
                fields.keys().forEach { fieldKey ->
                    val pattern = fields.getString(fieldKey)
                    patterns[fieldKey] = Regex(pattern, RegexOption.IGNORE_CASE)
                }
                vendors[vendorKey] = VendorMatchers(patterns)
            }
            return RemoteMatcherConfig(version, vendors)
        }
    }
}
