package com.batteryfox.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Fixes the "silent failure" problem: the original code fired componentName intents
 * directly, which throws ActivityNotFoundException on any device/OS version where
 * that screen doesn't exist. This checks resolvability first and always has a
 * universal fallback.
 *
 * REQUIRES on API 30+ (Android 11 package visibility): resolveActivity() below will
 * silently return null for every one of these packages — even if installed — unless
 * each is declared in AndroidManifest.xml under <queries>:
 *
 *   <queries>
 *       <package android:name="com.miui.cit" />
 *       <package android:name="com.oplus.engineermode" />
 *       <package android:name="com.android.settings" />
 *       <!-- Samsung is fired via an implicit action, not an explicit component,
 *            so declare the action itself rather than guessing the handler package
 *            (it's com.samsung.android.voc, but the action-based declaration below
 *            resolves it automatically and survives Samsung renaming the package) -->
 *       <intent>
 *           <action android:name="com.samsung.android.action.DIAGNOSTICS_BATTERY" />
 *       </intent>
 *   </queries>
 *
 * Without this, every OEM branch below silently fails and every device falls through
 * to the AOSP fallback, regardless of what's actually installed.
 */
object OemDiagnosticLauncher {

    private val oemIntents: List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.miui.cit", "com.miui.cit.CitLauncher")),
        Intent("com.samsung.android.action.DIAGNOSTICS_BATTERY"),
        Intent().setComponent(ComponentName("com.oplus.engineermode", "com.oplus.engineermode.ChargeActivity"))
    )

    private val universalFallback: Intent = Intent().setComponent(
        ComponentName("com.android.settings", "com.android.settings.TestingSettings")
    )

    /** Tries each known OEM diagnostic screen in order, then the AOSP field-test screen. Returns true if anything launched. */
    fun launchBestAvailable(context: Context): Boolean {
        for (intent in oemIntents) {
            if (tryLaunch(context, intent)) return true
        }
        return tryLaunch(context, universalFallback)
    }

    private fun tryLaunch(context: Context, intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            // Component exists and resolved, but the OEM guards it with a signature
            // permission or exported="false" on a newer security patch level.
            // Not the same failure as "not installed" — but the outcome for the
            // caller is the same: move on to the next candidate.
            false
        } catch (e: Exception) {
            false
        }
    }
}
