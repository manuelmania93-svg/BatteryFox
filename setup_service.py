import sys

service_code = '''package com.batteryfox.app.core.service

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
            val temp = (intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) / 10f

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val currentMa = currentUa / 1000
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
'''

manifest_code = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />

    <queries>
        <package android:name="com.miui.cit" />
        <package android:name="com.oplus.engineermode" />
        <package android:name="com.android.settings" />
        <intent>
            <action android:name="com.samsung.android.action.DIAGNOSTICS_BATTERY" />
        </intent>
    </queries>

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Battery Fox"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">

        <activity
            android:name=".presentation.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/zip" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
        </activity>

        <service
            android:name=".core.service.BatteryMonitorService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Battery hardware telemetry and fuel gauge calibration tracking" />
        </service>

    </application>
</manifest>
'''

vm_code = '''package com.batteryfox.app.presentation.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryfox.app.core.engine.InternalResistanceTester
import com.batteryfox.app.core.oem.OemDiagnosticLauncher
import com.batteryfox.app.core.parser.UniversalBugReportParser
import com.batteryfox.app.core.service.BatteryMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

data class DashboardState(
    val batteryPercent: Int = 0,
    val voltageMv: Int = 0,
    val temperatureCelsius: Float = 0f,
    val currentMa: Int = 0,
    val wattage: Float = 0f,
    val cycleCount: Int? = null,
    val estimatedHealthPercent: Float = 100f,
    val designCapacityMah: Int = 0,
    val remainingCapacityMah: Int = 0,
    val isDualCell: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isTestingResistance: Boolean = false,
    val isParsingBugReport: Boolean = false,
    val measuredResistanceMilliOhms: Float? = null,
    val testConfidence: String? = null,
    val statusMessage: String? = null
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val resistanceTester = InternalResistanceTester(application)
    private val oemLauncher = OemDiagnosticLauncher(application)
    private val bugReportParser = UniversalBugReportParser()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refreshTelemetry()
    }

    fun refreshTelemetry() {
        val context = getApplication<Application>()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val soc = if (scale > 0) (level * 100) / scale else 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = currentUa / 1000
        val powerWatts = (voltage / 1000f) * (abs(currentMa) / 1000f)

        var cycles: Int? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val c = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (c >= 0) cycles = c
        }

        val isDualCell = voltage > 5000
        val designMah = getFactoryDesignCapacityMah(context)

        val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val liveRemainingMah = if (chargeCounterUah > 0) {
            chargeCounterUah / 1000
        } else {
            (designMah * soc) / 100
        }

        val calculatedHealth = if (cycles != null && cycles > 0) {
            val totalWear = cycles * 0.0225f
            max(52f, 100f - totalWear)
        } else {
            _state.value.estimatedHealthPercent
        }

        val running = isServiceRunning(context, BatteryMonitorService::class.java)

        _state.value = _state.value.copy(
            batteryPercent = soc,
            voltageMv = voltage,
            temperatureCelsius = temp,
            currentMa = currentMa,
            wattage = powerWatts,
            cycleCount = cycles,
            designCapacityMah = designMah,
            remainingCapacityMah = liveRemainingMah,
            isDualCell = isDualCell,
            isServiceRunning = running,
            estimatedHealthPercent = calculatedHealth
        )
    }

    fun toggleMonitorService() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, BatteryMonitorService::class.java)

        if (_state.value.isServiceRunning) {
            context.stopService(serviceIntent)
            _state.value = _state.value.copy(isServiceRunning = false, statusMessage = "Monitor service stopped.")
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            _state.value = _state.value.copy(isServiceRunning = true, statusMessage = "Monitor service running.")
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun getFactoryDesignCapacityMah(context: Context): Int {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfileInstance = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val getAveragePowerMethod = powerProfileClass.getMethod("getAveragePower", String::class.java)
            val cap = getAveragePowerMethod.invoke(powerProfileInstance, "battery.capacity") as Double
            cap.toInt()
        } catch (_: Exception) {
            5000
        }
    }

    fun runResistanceStressTest() {
        if (_state.value.isTestingResistance) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isTestingResistance = true, statusMessage = null)
            val result = resistanceTester.executeMultiSampleTest()
            result.onSuccess { data ->
                _state.value = _state.value.copy(
                    isTestingResistance = false,
                    measuredResistanceMilliOhms = data.finalResistanceMilliOhms,
                    estimatedHealthPercent = data.estimatedHealthPercent,
                    testConfidence = data.confidence.name,
                    statusMessage = "Test passed (${data.confidence.name} confidence)"
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isTestingResistance = false,
                    statusMessage = error.message ?: "Stress test failed"
                )
            }
        }
    }

    fun parseBugReportUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isParsingBugReport = true, statusMessage = "Parsing dump...")
            val context = getApplication<Application>()
            try {
                val parseResult = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bugReportParser.parseZip(stream)
                    } ?: throw IllegalStateException("Unable to open bug report stream.")
                }

                _state.value = _state.value.copy(
                    isParsingBugReport = false,
                    estimatedHealthPercent = parseResult.report.healthPercent,
                    cycleCount = parseResult.report.cycleCount,
                    designCapacityMah = parseResult.report.designCapacityMah,
                    statusMessage = "Parsed ${parseResult.telemetry.recognizedVendor} logs"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isParsingBugReport = false,
                    statusMessage = "Parse failed: ${e.localizedMessage ?: "Invalid file"}"
                )
            }
        }
    }

    fun launchOemMenu(): Boolean = oemLauncher.launchHighestPriorityDiagnostic()
}
'''

ui_code = '''package com.batteryfox.app.presentation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryfox.app.R
import com.batteryfox.app.presentation.theme.*
import com.batteryfox.app.presentation.viewmodel.BatteryViewModel

@Composable
fun DashboardScreen(viewModel: BatteryViewModel) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.parseBugReportUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxBackground)
            .padding(20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.fox),
                    contentDescription = "Logo",
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Battery Fox", color = FoxTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Hardware Telemetry", color = FoxTextSecondary, fontSize = 12.sp)
                }
            }
            Surface(color = FoxSurfaceVariant, shape = RoundedCornerShape(20.dp)) {
                Text(
                    text = "${state.batteryPercent}% SoC",
                    color = FoxElectricGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Health Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("HARDWARE STATE OF HEALTH", color = FoxTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${state.estimatedHealthPercent.toInt()}%",
                    color = if (state.estimatedHealthPercent >= 80f) FoxTextPrimary else FoxAccentOrange,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen.copy(alpha = 0.15f) else FoxAccentOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (state.estimatedHealthPercent >= 80f) "HEALTHY CELL" else "AGED (REPLACEMENT RECOMMENDED)",
                        color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen else FoxAccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Telemetry Row 1 (Voltage + Temp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val voltLabel = if (state.isDualCell) "VOLTS (2S DUAL)" else "VOLTAGE"
            TelemetryCard(title = voltLabel, value = "${state.voltageMv} mV", modifier = Modifier.weight(1f))
            TelemetryCard(title = "TEMP", value = "${state.temperatureCelsius} °C", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 2 (Current + Power)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(title = "CURRENT", value = "${state.currentMa} mA", modifier = Modifier.weight(1f))
            val formattedWatts = String.format("%.2f", state.wattage)
            TelemetryCard(title = "POWER", value = "$formattedWatts W", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 3 (Capacity in mAh + Cycles)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val capValue = if (state.designCapacityMah > 0) "${state.remainingCapacityMah}/${state.designCapacityMah} mAh" else "${state.remainingCapacityMah} mAh"
            TelemetryCard(title = "CAPACITY (REAL)", value = capValue, modifier = Modifier.weight(1f))
            TelemetryCard(title = "CYCLES", value = state.cycleCount?.toString() ?: "N/A", modifier = Modifier.weight(1f))
        }

        state.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = msg, color = FoxAccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Foreground Service Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Background Monitor Service", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tracks live mA, wattage, and temperature in persistent notification.", color = FoxTextSecondary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = state.isServiceRunning,
                    onCheckedChange = { viewModel.toggleMonitorService() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stress Test Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("10-Second Impedance Test", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Measures internal resistance (R_int) to calculate cell wear.", color = FoxTextSecondary, fontSize = 12.sp)

                state.measuredResistanceMilliOhms?.let { res ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Resistance: ${res.toInt()} mΩ (${state.testConfidence ?: ""})", color = FoxAccentOrange, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.runResistanceStressTest() },
                    enabled = !state.isTestingResistance,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
                ) {
                    Text(
                        if (state.isTestingResistance) "Testing Pulses..." else "Run 10-Second Health Test",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Diagnostics Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !state.isParsingBugReport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FoxTextPrimary)
            ) {
                Text(if (state.isParsingBugReport) "Parsing..." else "Import Bug Report", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { viewModel.launchOemMenu() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FoxTextPrimary)
            ) {
                Text("OEM Menu", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TelemetryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = FoxTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
'''

with open("app/src/main/java/com/batteryfox/app/core/service/BatteryMonitorService.kt", "w", encoding="utf-8") as f:
    f.write(service_code)

with open("app/src/main/AndroidManifest.xml", "w", encoding="utf-8") as f:
    f.write(manifest_code)

with open("app/src/main/java/com/batteryfox/app/presentation/viewmodel/BatteryViewModel.kt", "w", encoding="utf-8") as f:
    f.write(vm_code)

with open("app/src/main/java/com/batteryfox/app/presentation/ui/DashboardScreen.kt", "w", encoding="utf-8") as f:
    f.write(ui_code)

print("Service and UI successfully scaffolded.")
