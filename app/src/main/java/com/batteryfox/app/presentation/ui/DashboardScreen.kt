package com.batteryfox.app.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.batteryfox.app.R
import com.batteryfox.app.presentation.theme.*
import com.batteryfox.app.presentation.ui.components.BatteryCareCard
import com.batteryfox.app.presentation.ui.components.DeviceIdentityCard
import com.batteryfox.app.presentation.ui.components.RetrospectiveDrainCard
import com.batteryfox.app.presentation.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: BatteryViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleMonitorService()
        } else {
            viewModel.setStatusMessage("Notification permission is needed to show the background monitor.")
        }
    }

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
                    text = "${state.batteryPercent?.let { "$it%" } ?: "N/A"} SoC",
                    color = FoxElectricGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // --- Device Silicon & Platform Auto-Detector ---
        Spacer(modifier = Modifier.height(20.dp))
        DeviceIdentityCard(
            deviceModel = state.deviceModelName,
            androidVersion = state.androidVersionString,
            customOs = state.customOsName,
            firstAndroidVersion = state.factoryLaunchOs,
            firstUsageDate = state.firstUsageDate,
            manufactureDate = state.manufactureDate,
            uptimeHours = state.currentUptimeHours
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Battery health is only shown when a report provides usable evidence.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BATTERY HEALTH", color = FoxTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.estimatedHealthPercent?.let { "${it.toInt()}%" } ?: "N/A",
                    color = if ((state.estimatedHealthPercent ?: 100f) >= 80f) FoxTextPrimary else FoxAccentOrange,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (state.currentAvailableMah != null && state.factoryDesignMah != null) {
                        "${state.currentAvailableMah} mAh estimated / ${state.factoryDesignMah} mAh design"
                    } else {
                        "Capacity data unavailable"
                    },
                    color = FoxTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = FoxSurfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = state.healthSource ?: "No health measurement available",
                        color = FoxTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
                state.healthMeasuredAt?.let { timestamp ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Imported ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))}",
                        color = FoxTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (state.healthHistory.size >= 2) {
            val first = state.healthHistory.first()
            val latest = state.healthHistory.last()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Report trend: ${"%.1f".format(first.healthPercent)}% (${dateFormat.format(Date(first.timestamp))}) → " +
                    "${"%.1f".format(latest.healthPercent)}% (${dateFormat.format(Date(latest.timestamp))})",
                color = FoxTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    "BATTERYFOX LEARNED CAPACITY",
                    color = FoxTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val learned = state.learnedCapacity
                Text(
                    text = learned?.let { "~${it.capacityMah} mAh" } ?: "Collecting evidence",
                    color = FoxTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (learned != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Observed range: ${learned.lowerBoundMah}–${learned.upperBoundMah} mAh",
                        color = FoxTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${learned.observationCount} usable charge-change windows from ${learned.sampleCount} samples",
                        color = FoxTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Observed ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(learned.firstObservedAt))} – " +
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(learned.lastObservedAt)),
                        color = FoxTextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    val collectionMessage = if (state.chargeCounterAvailable) {
                        "BatteryFox has ${state.chargeSampleCount} local samples; it needs at least 3 consistent windows spanning 12% or more charge changes."
                    } else {
                        "No usable charge-counter reading yet. Some phones or firmware versions do not expose this value to apps."
                    }
                    Text(collectionMessage, color = FoxTextSecondary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Experimental capacity estimate from Android charge-counter history, not an OEM health reading. BatteryFox samples while this screen is open or the optional monitor is running; samples stay on this device.",
                    color = FoxTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Telemetry Row 1 (Voltage + Temp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(title = "VOLTAGE", value = state.voltageMv?.let { "$it mV" } ?: "N/A", modifier = Modifier.weight(1f))
            TelemetryCard(title = "TEMP", value = state.temperatureCelsius?.let { "%.1f °C".format(it) } ?: "N/A", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 2 (Current + Power Watts)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                title = "CURRENT",
                value = state.currentMa?.let { "$it mA" } ?: "N/A",
                modifier = Modifier.weight(1f),
                detail = state.currentSource
            )
            TelemetryCard(title = "POWER", value = state.wattage?.let { "%.2f W".format(it) } ?: "N/A", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 3 (Capacity Details)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                title = "REPORTED DESIGN",
                value = state.factoryDesignMah?.let { "$it mAh" } ?: "N/A",
                modifier = Modifier.weight(1f),
                detail = state.factoryDesignMah?.let { "Bug report" }
            )
            TelemetryCard(
                title = "LIFETIME CYCLES",
                value = state.cycleCount?.toString() ?: "N/A",
                modifier = Modifier.weight(1f),
                detail = state.cycleCountSource
            )
        }

        // --- Tier 4: 30-Day Retrospective App Drain Card ---
        Spacer(modifier = Modifier.height(16.dp))
        RetrospectiveDrainCard(
            hasPermission = state.hasUsagePermission,
            drainList = state.topHistoricalDrainers,
            onRequestPermission = { viewModel.requestUsagePermission() }
        )

        // --- Hardware Chemistry & Smart Battery Care Suite ---
        Spacer(modifier = Modifier.height(16.dp))
        BatteryCareCard(
            technology = state.batteryTechnology
        )

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Battery gauge calibration", color = FoxTextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Android does not let ordinary apps rewrite the phone's fuel-gauge hardware. Avoid deliberate full-drain cycles; use the manufacturer's service diagnostics if the percentage behaves abnormally.",
                    color = FoxTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Foreground Service Toggle Card
        Spacer(modifier = Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Background Monitor Service", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Tracks Android-reported current, power, and temperature when available.", color = FoxTextSecondary, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = state.isServiceRunning,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.toggleMonitorService()
                        } else if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.toggleMonitorService()
                        }
                    }
                )
            }
        }

        state.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = msg, color = FoxAccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stress Test Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Experimental Current-Pulse Test", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Experimental voltage/current response only; it is not a calibrated battery-health measurement.", color = FoxTextSecondary, fontSize = 12.sp)

                state.measuredResistanceMilliOhms?.let { res ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Resistance: ${res.toInt()} mΩ | sample consistency: ${state.testConfidence ?: "N/A"}", color = FoxAccentOrange, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.runResistanceStressTest() },
                    enabled = !state.isTestingResistance,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
                ) {
                    Text(
                        if (state.isTestingResistance) "Testing..." else "Run Experimental Test",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.launchOemMenu() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
            ) {
                Text("Launch Testing Menu", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !state.isParsingBugReport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FoxTextPrimary)
            ) {
                Text(if (state.isParsingBugReport) "Parsing..." else "Import Bug Report", fontSize = 12.sp)
            }
        }
        Text(
            "The selected bug-report ZIP is parsed on this device and is not uploaded by Battery Fox. Bug reports may contain other sensitive device information.",
            color = FoxTextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun TelemetryCard(title: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = FoxTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            detail?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(it, color = FoxTextSecondary, fontSize = 10.sp)
            }
        }
    }
}
