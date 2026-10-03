package com.batteryfox.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.batteryfox.app.core.telemetry.BatteryChargeSampleRecorder
import com.batteryfox.app.core.telemetry.BatteryTelemetryReader
import com.batteryfox.app.presentation.MainActivity

class BatteryMonitorService : Service() {

    private val channelId = "battery_fox_monitor_channel"
    private val notificationId = 1001

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            BatteryChargeSampleRecorder.record(context, intent, includeStoredSamples = false)
            val telemetry = BatteryTelemetryReader.read(context, intent)
            val direction = when (telemetry.isCharging) {
                true -> "Charging"
                false -> "Discharging"
                null -> "Current"
            }
            val currentSource = telemetry.currentSource?.let { " ($it)" } ?: ""
            val current = telemetry.currentMa?.let { "$direction: ${it} mA$currentSource" } ?: "Current unavailable"
            val watts = telemetry.wattage?.let { " | %.1f W".format(it) } ?: ""
            val temp = telemetry.temperatureCelsius?.let { " | %.1f °C".format(it) } ?: ""
            updateNotification(telemetry.levelPercent ?: 0, "$current$watts$temp")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification(0, "Initializing hardware telemetry..."))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Battery Fox Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live Android-reported battery telemetry when available"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(soc: Int, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (soc > 0) "Battery Fox: $soc%" else "Battery Fox Active"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(soc: Int, content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(soc, content))
    }
}
