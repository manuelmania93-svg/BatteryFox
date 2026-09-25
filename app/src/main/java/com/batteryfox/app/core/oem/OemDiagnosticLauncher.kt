package com.batteryfox.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

class OemDiagnosticLauncher(private val context: Context) {

    fun launchHighestPriorityDiagnostic(): Boolean {
        val candidates = listOf(
            // 1. Universal AOSP Testing Screen (*#*#4636#*#*)
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TestingSettings")),

            // 2. Qualcomm / AOSP RadioInfo
            Intent(Intent.ACTION_MAIN).setClassName("com.android.settings", "com.android.settings.RadioInfo"),

            // 3. Samsung Members Direct Diagnostics
            Intent("com.samsung.android.action.DIAGNOSTICS_BATTERY"),

            // 4. Xiaomi CIT Hardware Test
            Intent().setComponent(ComponentName("com.miui.cit", "com.miui.cit.CitLauncher")),

            // 5. OnePlus / Oppo Engineer Mode
            Intent().setComponent(ComponentName("com.oplus.engineermode", "com.oplus.engineermode.ChargeActivity")),

            // 6. Direct Dial Intent Fallback for *#*#4636#*#*
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:*#*#4636#*#*")),

            // 7. General Power & Battery Usage Details
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        )

        val pm = context.packageManager
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: SecurityException) {
                // Continue down fallback list
            } catch (_: Exception) {
                // Continue
            }
        }
        return false
    }
}
