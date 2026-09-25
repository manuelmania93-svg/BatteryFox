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
import com.batteryfox.app.core.engine.AppDrainMetric
import com.batteryfox.app.presentation.theme.*

@Composable
fun RetrospectiveDrainCard(
    hasPermission: Boolean,
    drainList: List<AppDrainMetric>,
    onRequestPermission: () -> Unit,
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
                Column {
                    Text(
                        text = "30-Day Retrospective Drain",
                        color = FoxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Historical app drain prior to installation",
                        color = FoxTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = FoxSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "PAST 30D",
                        color = FoxElectricGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hasPermission) {
                Text(
                    text = "Android prevents unprivileged apps from reading past app usage. Grant Usage Access to calculate which apps drained your battery before Battery Fox was installed.",
                    color = FoxTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FoxAccentOrange)
                ) {
                    Text("Unlock 30-Day App History", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                if (drainList.isEmpty()) {
                    Text("Aggregating historical usage statistics...", color = FoxTextSecondary, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        drainList.forEach { metric ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = metric.appName,
                                        color = FoxTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${"%.1f".format(metric.foregroundHours)} hrs screen-time (~${metric.estimatedDrainMah} mAh)",
                                        color = FoxTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "~${"%.1f".format(metric.drainPercent)}%",
                                    color = FoxAccentOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            HorizontalDivider(color = FoxSurfaceVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
