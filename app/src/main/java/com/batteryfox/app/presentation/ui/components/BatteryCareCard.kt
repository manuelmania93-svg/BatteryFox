package com.batteryfox.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryfox.app.presentation.theme.*

@Composable
fun BatteryCareCard(
    technology: String?,
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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Battery Care",
                        color = FoxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "General lithium-ion battery guidance",
                        color = FoxTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Surface(
                    color = FoxSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = technology ?: "Technology unavailable",
                        color = FoxElectricGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            CareTipItem(
                title = "Limit heat when practical",
                description = "High temperatures can accelerate battery aging. Avoid leaving the phone hot or in direct sun, especially while charging."
            )

            Spacer(modifier = Modifier.height(10.dp))

            CareTipItem(
                title = "Use built-in optimized charging",
                description = "If your phone offers a charge limit or optimized charging, enable it if it suits your routine. These controls are optional; normal charging is fine."
            )

            Spacer(modifier = Modifier.height(10.dp))

            CareTipItem(
                title = "Avoid repeated deep discharges",
                description = "There is no need to deliberately drain the phone to 0% to calibrate it. If the battery indicator behaves abnormally, follow the manufacturer's support guidance."
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
