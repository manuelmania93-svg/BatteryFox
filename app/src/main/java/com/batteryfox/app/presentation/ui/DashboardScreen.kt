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

Composable
fun DashboardScreen(viewModel: BatteryViewModel) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  uri ->
        uri?.let {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  viewModel.parseBugReportUri(it) 
    

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxBackground)
            .padding(20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        Spacer(modifier = Modifier.height(24.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Row(verticalAlignment = Alignment.CenterVertically) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Image(
                    painter = painterResource(id = R.drawable.fox),
                    contentDescription = "Logo",
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    Text("Battery Fox", color = FoxTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Hardware Telemetry", color = FoxTextSecondary, fontSize = 12.sp)
                
            
            Surface(color = FoxSurfaceVariant, shape = RoundedCornerShape(20.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Text(
                    text = "$state.batteryPercent% SoC",
                    color = FoxElectricGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            
        

        Spacer(modifier = Modifier.height(24.dp))

        // Hero Card: State of Health + Real Hardware Capacity
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Text("HARDWARE STATE OF HEALTH", color = FoxTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "$state.estimatedHealthPercent.toInt()%",
                    color = if (state.estimatedHealthPercent >= 80f) FoxTextPrimary else FoxAccentOrange,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Real mAh Readout directly under Health
                Text(
                    text = "$state.currentAvailableMah mAh usable / $state.factoryDesignMah mAh design",
                    color = FoxTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen.copy(alpha = 0.15f) else FoxAccentOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    Text(
                        text = if (state.estimatedHealthPercent >= 80f) "HEALTHY CELL" else "AGED (REPLACEMENT RECOMMENDED)",
                        color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen else FoxAccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                
            
        

        Spacer(modifier = Modifier.height(16.dp))

        // Telemetry Row 1 (Voltage + Temp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            val voltLabel = if (state.isDualCell) "VOLTS (2S DUAL)" else "VOLTAGE"
            TelemetryCard(title = voltLabel, value = "$state.voltageMv mV", modifier = Modifier.weight(1f))
            TelemetryCard(title = "TEMP", value = "$state.temperatureCelsius °C", modifier = Modifier.weight(1f))
        

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 2 (Current + Power Watts)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            TelemetryCard(title = "CURRENT", value = "$state.currentMa mA", modifier = Modifier.weight(1f))
            val formattedWatts = String.format("%.2f", state.wattage)
            TelemetryCard(title = "POWER", value = "$formattedWatts W", modifier = Modifier.weight(1f))
        

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry Row 3 (Capacity Details)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            TelemetryCard(title = "FACTORY DESIGN", value = "$state.factoryDesignMah mAh", modifier = Modifier.weight(1f))
            TelemetryCard(title = "LIFETIME CYCLES", value = state.cycleCount?.toString() ?: "N/A", modifier = Modifier.weight(1f))
        

        // Calibration Drift Alert Card
        if (state.calibrationDriftDetected) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FoxAccentOrange.copy(alpha = 0.12f))
            ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Column(modifier = Modifier.padding(16.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    Text("Fuel Gauge Drift Detected", color = FoxAccentOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your voltage does not match your battery %. Follow the 1-100% calibration cycle below to reset PMIC tracking.",
                        color = FoxTextPrimary,
                        fontSize = 12.sp
                    )
                
            
        

        state.statusMessage?.let {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = msg, color = FoxAccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        

        Spacer(modifier = Modifier.height(18.dp))

        // Legitimate Calibration Protocol Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Text("Legit 1-100% PMIC Calibration", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Fixes sudden percentage drops (e.g. 20% -> 0%) by forcing the fuel-gauge chip to learn real cutoff points.",
                    color = FoxTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    Text("1. Discharge continuously until phone powers off naturally.", color = FoxTextPrimary, fontSize = 12.sp)
                    Text("2. Plug in and charge undisturbed to 100% without unplugging.", color = FoxTextPrimary, fontSize = 12.sp)
                    Text("3. Leave plugged in for 60 min after 100% to saturate cell registers.", color = FoxTextPrimary, fontSize = 12.sp)
                
            
        

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons: Hardened OEM Menu + Bug Report Picker
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Button(
                onClick = {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  viewModel.launchOemMenu() ,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
            ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Text("Launch Testing Menu", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            

            OutlinedButton(
                onClick = {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  filePicker.launch(arrayOf("application/zip", "application/octet-stream")) ,
                enabled = !state.isParsingBugReport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FoxTextPrimary)
            ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Text(if (state.isParsingBugReport) "Parsing..." else "Import Bug Report", fontSize = 12.sp)
            
        

        Spacer(modifier = Modifier.height(28.dp))
    


Composable
fun TelemetryCard(title: String, value: String, modifier: Modifier = Modifier) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        Column(modifier = Modifier.padding(14.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Text(title, color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = FoxTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        
    

