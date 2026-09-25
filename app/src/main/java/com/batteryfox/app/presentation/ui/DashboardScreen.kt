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

        Spacer(modifier = Modifier.height(28.dp))

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
                    color = FoxTextPrimary,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen.copy(alpha = 0.15f) else FoxAccentOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (state.estimatedHealthPercent >= 80f) "HEALTHY CELL" else "AGED (SERVICE RECOMMENDED)",
                        color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen else FoxAccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Telemetry Row 1
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val voltLabel = if (state.isDualCell) "VOLTAGE (2S DUAL)" else "VOLTAGE"
            TelemetryCard(title = voltLabel, value = "${state.voltageMv} mV", modifier = Modifier.weight(1f))
            TelemetryCard(title = "TEMP", value = "${state.temperatureCelsius} °C", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 2
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(title = "CURRENT", value = "${state.currentMa} mA", modifier = Modifier.weight(1f))
            TelemetryCard(title = "LIFETIME CYCLES", value = state.cycleCount?.toString() ?: "N/A", modifier = Modifier.weight(1f))
        }

        state.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = msg, color = FoxAccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(18.dp))

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
                    Text("Resistance: ${res.toInt()} mO (${state.testConfidence ?: ""})", color = FoxAccentOrange, fontWeight = FontWeight.Bold)
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
            Text(value, color = FoxTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
