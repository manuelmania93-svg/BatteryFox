package com.batteryfox.app.core.oem

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class OemDiagnosticLauncher(private val context: Context) {

    companion object {
        private const val TAG = "OemDiagnosticLauncher"
    }

    fun launchHighestPriorityDiagnostic(): Boolean {
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TestingSettings")),
            Intent(Intent.ACTION_MAIN).setClassName("com.android.settings", "com.android.settings.RadioInfo"),
            Intent("com.samsung.android.action.DIAGNOSTICS_BATTERY"),
            Intent().setComponent(ComponentName("com.miui.cit", "com.miui.cit.CitLauncher")),
            Intent().setComponent(ComponentName("com.oplus.engineermode", "com.oplus.engineermode.ChargeActivity")),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:*#*#4636#*#*")),
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
            } catch (error: SecurityException) {
                Log.w(TAG, "OEM diagnostic launch was denied", error)
            } catch (error: ActivityNotFoundException) {
                Log.i(TAG, "OEM diagnostic activity is unavailable", error)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "OEM diagnostic intent was rejected", error)
            }
        }
        return false
    }
}
