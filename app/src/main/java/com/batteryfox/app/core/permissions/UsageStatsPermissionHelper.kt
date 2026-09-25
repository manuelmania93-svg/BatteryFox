package com.batteryfox.app.core.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * PACKAGE_USAGE_STATS can't be requested via ActivityCompat.requestPermissions —
 * it's a special-access permission granted only through its own Settings screen.
 * hasAccess() lets you show an explainer BEFORE deep-linking, since a bare
 * Settings redirect with no context reads as suspicious to users and reviewers.
 */
object UsageStatsPermissionHelper {

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Call only after showing an explainer screen — this jumps straight to system Settings.
     *
     * Some OEM skins (MIUI, older ColorOS builds) crash with ActivityNotFoundException when a
     * package: URI is attached to ACTION_USAGE_ACCESS_SETTINGS, even though the action itself
     * exists on the device. Falls back to the generic (unscoped) usage-access screen, then to
     * ACTION_SETTINGS as a last resort, rather than letting the crash propagate.
     */
    fun openUsageAccessSettings(context: Context) {
        val scoped = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(scoped)
            return
        } catch (e: Exception) {
            // Fall through to the unscoped variant below
        }

        val unscoped = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        try {
            context.startActivity(unscoped)
            return
        } catch (e: Exception) {
            // Fall through to generic settings
        }

        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
