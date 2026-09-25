package com.batteryfox.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class OemDiagnosticLauncher(private val context: Context) {

    fun launchHighestPriorityDiagnostic(): Boolean {
        val pm = context.packageManager
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.cit", "com.miui.cit.CitLauncher")),
            Intent("com.samsung.android.action.DIAGNOSTICS_BATTERY"),
            Intent().setComponent(ComponentName("com.oplus.engineermode", "com.oplus.engineermode.ChargeActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TestingSettings")),
            Intent(Intent.ACTION_MAIN).setClassName("com.android.settings", "com.android.settings.RadioInfo")
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: SecurityException) {
                // OEM component unexported or signature-locked
            } catch (_: Exception) {
                // Fallback to next diagnostic in priority chain
            }
        }
        return false
    }
}
