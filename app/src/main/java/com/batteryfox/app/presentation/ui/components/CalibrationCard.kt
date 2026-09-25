package com.batteryfox.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryfox.app.presentation.theme.*
import com.batteryfox.app.presentation.viewmodel.CalibrationStep

@Composable
fun CalibrationCard(
    step: CalibrationStep,
    voltageMv: Int,
    isDualCell: Boolean,
    currentMa: Int,
    wattage: Float,
    saturationMinutesRemaining: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Legit PMIC Calibration",
                    color = FoxTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1
                )
                Surface(
                    color = when (step) {
                        CalibrationStep.IDLE -> FoxSurfaceVariant
                        CalibrationStep.COMPLETED -> FoxElectricGreen.copy(alpha = 0.15f)
                        else -> FoxAccentOrange.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = step.name,
                        color = when (step) {
                            CalibrationStep.IDLE -> FoxTextSecondary
                            CalibrationStep.COMPLETED -> FoxElectricGreen
                            else -> FoxAccentOrange
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (step) {
                CalibrationStep.IDLE -> {
                    Text(
                        text = "Calibrates fuel gauge registers by learning real low cutoff and top saturation dwell. Fixes sudden % drops.",
                        color = FoxTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStart,
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
                    val perCellMv = if (isDualCell) voltageMv / 2 else voltageMv
                    Text(
                        text = "Per-cell voltage: $perCellMv mV (Target: < 3450 mV)",
                        color = FoxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCancel,
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
                    val wattsFormatted = String.format("%.1f", wattage)
                    Text(
                        text = "Current: $currentMa mA | Power: $wattsFormatted W",
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
                        text = "Taper Current: $currentMa mA | Dwell Timer: $saturationMinutesRemaining min left",
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
                        onClick = onCancel,
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
}
