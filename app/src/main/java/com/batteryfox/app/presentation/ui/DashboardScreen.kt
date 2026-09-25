package com.batteryfox.app.presentation.ui

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // --- Header Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.fox),
                    contentDescription = "Battery Fox Logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Battery Fox",
                        color = FoxTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hardware Telemetry Engine",
                        color = FoxTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                color = FoxSurfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "${state.batteryPercent}% SoC",
                    color = FoxElectricGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Health Hero Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ESTIMATED STATE OF HEALTH",
                    color = FoxTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${"%.1f".format(state.estimatedHealthPercent)}%",
                    color = FoxTextPrimary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen.copy(alpha = 0.15f) else FoxAccentOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (state.estimatedHealthPercent >= 80f) "HEALTHY CELL" else "DEGRADED CELL",
                        color = if (state.estimatedHealthPercent >= 80f) FoxElectricGreen else FoxAccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Live Telemetry Grid ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                title = "VOLTAGE",
                value = "${state.voltageMv} mV",
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = "TEMPERATURE",
                value = "${state.temperatureCelsius} °C",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                title = "CURRENT",
                value = "${state.currentMa} mA",
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = "CYCLES",
                value = state.cycleCount?.toString() ?: "Android 14+",
                modifier = Modifier.weight(1f)
            )
        

        Spacer(modifier = Modifier.height(24.dp))

        // --- 10-Second Stress Test Section ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FoxSurface)
        ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Text(
                    text = "Active Impedance Calibration",
                    color = FoxTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Runs 3 multi-core load pulses to calculate internal cell resistance (R_int) via Ohm's Law.",
                    color = FoxTextSecondary,
                    fontSize = 12.sp
                )

                state.measuredResistanceMilliOhms?.let {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  resistance ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        Column {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                            Text("Internal Resistance", color = FoxTextSecondary, fontSize = 12.sp)
                            Text("$"%.1f".format(resistance) mO", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        
                        state.testConfidence?.let {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  conf ->
                            Surface(
                                color = FoxSurfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                                Text(
                                    text = "$conf CONFIDENCE",
                                    color = FoxAccentOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            
                        
                    
                

                state.testErrorMessage?.let {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = err, color = Color(0xFFFF5252), fontSize = 12.sp)
                

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  viewModel.runResistanceStressTest() ,
                    enabled = !state.isTestingResistance,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
                ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    if (state.isTestingResistance) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Executing Pulses (10s)...", color = Color.Black, fontWeight = FontWeight.Bold)
                     else {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        Text("Run 10-Second Health Test", color = Color.Black, fontWeight = FontWeight.Bold)
                    
                
            
        

        Spacer(modifier = Modifier.height(16.dp))

        // --- Hardware Diagnostic Deep Link Button ---
        OutlinedButton(
            onClick = {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  viewModel.launchOemMenu() ,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FoxTextPrimary)
        ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Text("Launch OEM Hardware Diagnostic Screen")
        

        Spacer(modifier = Modifier.height(32.dp))
    


Composable
fun TelemetryCard(title: String, value: String, modifier: Modifier = Modifier) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        Column(modifier = Modifier.padding(16.dp)) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Text(text = title, color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = FoxTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        
    

