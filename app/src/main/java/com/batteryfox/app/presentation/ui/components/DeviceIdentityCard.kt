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
fun DeviceIdentityCard(
    deviceModel: String,
    androidVersion: String,
    customOs: String,
    firstAndroidVersion: String,
    yearsActive: Float,
    detailedAge: String,
    firstUsageDate: String?,
    manufactureDate: String?,
    uptimeHours: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Device Model & Total Lifetime Years
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = deviceModel,
                        color = FoxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "$androidVersion • $customOs",
                        color = FoxTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                Surface(
                    color = FoxSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = detailedAge,
                        color = FoxAccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FoxSurfaceVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Sub-grid 1: Factory Launch Era vs First Usage Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("FACTORY LAUNCH", color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(firstAndroidVersion, color = FoxTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    val dateLabel = if (firstUsageDate != null) "FIRST USAGE DATE" else "CURRENT RUNTIME"
                    val dateValue = firstUsageDate ?: "${uptimeHours}h active boot"
                    Text(dateLabel, color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(dateValue, color = FoxElectricGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Sub-grid 2: Battery Assembly Date (if available)
            manufactureDate?.let { mfg ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("PACK MANUFACTURE DATE", color = FoxTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(mfg, color = FoxAccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
