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

@Composable
fun BatteryCareCard(
    technology: String,
    isDualCell: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Detected Chemistry and Topology
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Smart Battery Care & Chemistry",
                        color = FoxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "Hardware-tailored longevity guidelines",
                        color = FoxTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Surface(
                    color = FoxSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val techLabel = if (isDualCell) "$technology (2S Dual)" else technology
                    Text(
                        text = techLabel,
                        color = FoxElectricGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tip 1: The 20-80 Rule
            CareTipItem(
                title = "1. Maintain the 20% – 80% Operating Window",
                description = "Voltage stress spikes exponentially above 80% (>4.2V/cell), causing parasitic electrolyte oxidation. Keeping charge below 80% doubles cycle life."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tip 2: Thermal Ceiling
            CareTipItem(
                title = "2. Keep Temperatures Below 35°C",
                description = "High heat during fast charging rapidly thickens the internal SEI barrier. Remove thick cases while fast-charging and never game while plugged in."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tip 3: Dual-cell Fast-Charging Realities
            if (isDualCell) {
                CareTipItem(
                    title = "3. Series 2S Dual-Cell Considerations",
                    description = "Your phone charges two cells in series to enable ultra-fast wattage. Avoid micro-discharges (<10%) which accelerate cell imbalance across the pair."
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Tip 4: Calibration Frequency
            CareTipItem(
                title = "4. Calibrate Only When Drift Occurs",
                description = "Deep 0% discharges cause mechanical anode strain. Run the 4-stage PMIC Calibration Wizard only once every 2-3 months to re-anchor endpoints."
            )
        }
    }
}

@Composable
private fun CareTipItem(title: String, description: String) {
    Column {
        Text(
            text = title,
            color = FoxAccentOrange,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            color = FoxTextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
