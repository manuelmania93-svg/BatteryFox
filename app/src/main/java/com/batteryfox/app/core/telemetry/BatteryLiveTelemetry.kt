package com.batteryfox.app.core.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.batteryfox.app.core.storage.ChargeSampleHistory

object BatteryMonitoringPolicy {
    const val UI_INTERVAL_MS = 5_000L
    const val SAMPLE_INTERVAL_MS = ChargeSampleHistory.INTERVAL_MS
}

/** Collect only while the Activity is resumed. No alarms, wake locks or screen-off UI polling. */
fun batteryIntents(context: Context) = liveTelemetryFlow<Intent>(BatteryMonitoringPolicy.UI_INTERVAL_MS) { emit ->
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { emit(intent) }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(emit)
    val unsubscribe: () -> Unit = { context.unregisterReceiver(receiver) }
    unsubscribe
}
