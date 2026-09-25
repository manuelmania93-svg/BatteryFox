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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.batteryfox.app.presentation.MainActivity
import kotlin.math.abs

class BatteryMonitorService : Service() {

    private val channelId = "battery_fox_monitor_channel"
    private val notificationId = 1001

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val soc = if (scale > 0) (level * 100) / scale else -1
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val temp = tempRaw / 10f

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val rawMa = currentUa / 1000
            val currentMa = if (isCharging) abs(rawMa) else -abs(rawMa)
            val watts = (voltage / 1000f) * (abs(currentMa) / 1000f)

            val statusText = if (currentMa > 0) {
                "Charging: +${currentMa} mA (%.1f W) | %.1f °C".format(watts, temp)
            } else {
                "Discharging: ${currentMa} mA (%.1f W) | %.1f °C".format(watts, temp)
            }

            updateNotification(soc, statusText)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification(0, "Initializing hardware telemetry..."))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Battery Fox Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live battery current, wattage and calibration tracking"
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
