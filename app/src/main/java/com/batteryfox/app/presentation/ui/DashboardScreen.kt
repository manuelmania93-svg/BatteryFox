package com.batteryfox.app.presentation.ui

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
import com.batteryfox.app.presentation.viewmodel.CalibrationStep

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

        // Top Header
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

        // Hero Card: State of Health + Real Hardware Capacity
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
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${state.currentAvailableMah} mAh usable / ${state.factoryDesignMah} mAh design",
                    color = FoxTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))
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

        // Telemetry Row 2 (Current + Power Watts)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(title = "CURRENT", value = "${state.currentMa} mA", modifier = Modifier.weight(1f))
            val formattedWatts = String.format("%.2f", state.wattage)
            TelemetryCard(title = "POWER", value = "$formattedWatts W", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 3 (Capacity Details)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(title = "FACTORY DESIGN", value = "${state.factoryDesignMah} mAh", modifier = Modifier.weight(1f))
            TelemetryCard(title = "LIFETIME CYCLES", value = state.cycleCount?.toString() ?: "N/A", modifier = Modifier.weight(1f))
        }

        // --- Active Legit PMIC Calibration Wizard Card ---
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Legit PMIC Calibration Wizard", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Surface(
                        color = when (state.calibrationStep) {
                            CalibrationStep.IDLE -> FoxSurfaceVariant
                            CalibrationStep.COMPLETED -> FoxElectricGreen.copy(alpha = 0.15f)
                            else -> FoxAccentOrange.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = state.calibrationStep.name,
                            color = when (state.calibrationStep) {
                                CalibrationStep.IDLE -> FoxTextSecondary
                                CalibrationStep.COMPLETED -> FoxElectricGreen
                                else -> FoxAccentOrange
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (state.calibrationStep) {
                    CalibrationStep.IDLE -> {
                        Text(
                            text = "Calibrates fuel gauge registers by learning true low cutoff & 100% saturation dwell. Fixes sudden % drops.",
                            color = FoxTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.startCalibrationWizard() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
                        ) {
                            Text("Start Calibration Cycle", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    CalibrationStep.DISCHARGING -> {
                        Text(
                            text = "Stage 1: Discharge battery until phone hits 5% or powers off. Do not plug in yet.",
                            color = FoxAccentOrange,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Per-cell voltage: ${if (state.isDualCell) state.voltageMv / 2 else state.voltageMv} mV (Target: < 3450 mV)",
                            color = FoxTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelCalibration() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel Wizard", color = FoxTextSecondary)
                        }
                    }
                    CalibrationStep.CHARGING -> {
                        Text(
                            text = "Stage 2: Plug in and charge undisturbed to 100%. Do not unplug.",
                            color = FoxElectricGreen,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Current: ${state.currentMa} mA | Power: ${String.format("%.1f", state.wattage)} W",
                            color = FoxTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    CalibrationStep.SATURATING -> {
                        Text(
                            text = "Stage 3 (Saturation Dwell): Android says 100%, but PMIC is still learning top registers.",
                            color = FoxAccentOrange,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Taper Current: ${state.currentMa} mA | Dwell Timer: ${state.saturationMinutesRemaining} min left",
                            color = FoxTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    CalibrationStep.COMPLETED -> {
                        Text(
                            text = "Calibration Complete! Fuel gauge has learned real chemical cutoff and saturation endpoints.",
                            color = FoxElectricGreen,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.cancelCalibration() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FoxElectricGreen)
                        ) {
                            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                    Text("Tracks live mA, wattage, and temperature in notification.", color = FoxTextSecondary, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = state.isServiceRunning,
                    onCheckedChange = { viewModel.toggleMonitorService() }
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
                Text("10-Second Impedance Test", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Measures internal resistance (R_int) to calculate cell wear.", color = FoxTextSecondary, fontSize = 12.sp)

                state.measuredResistanceMilliOhms?.let { res ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Resistance: ${res.toInt()} mΩ (${state.testConfidence ?: ""})", color = FoxAccentOrange, fontWeight = FontWeight.Bold)
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
                        if (state.isTestingResistance) "Testing Pulses..." else "Run 10-Second Health Test",
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

        Spacer(modifier = Modifier.height(28.dp))
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
