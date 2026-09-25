package com.batteryfox.app.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryfox.app.presentation.theme.*
import kotlin.math.sqrt

@Composable
fun LifetimeWearCard(
    currentHealthPercent: Float,
    cycleCount: Int,
    yearsActive: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FoxSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Constrained with flex weight to prevent badge squishing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Lifetime Wear Trajectory",
                        color = FoxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "Model: Calendar √t + Linear Cycle Wear",
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
                        text = "${"%.1f".format(yearsActive)} YRS ACTIVE",
                        color = FoxAccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Canvas Line Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Draw 80% Critical Service Line (Dashed Horizon)
                    val y80 = h * (1f - (80f - 40f) / 60f)
                    drawLine(
                        color = Color(0x66FF5252),
                        start = Offset(0f, y80),
                        end = Offset(w, y80),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // 2. Generate Degradation Curve Points
                    val totalMonths = (yearsActive * 12).toInt().coerceAtLeast(12)
                    val path = Path()
                    val fillPath = Path()

                    val startY = h * (1f - (100f - 40f) / 60f)
                    path.moveTo(0f, startY)
                    fillPath.moveTo(0f, h)
                    fillPath.lineTo(0f, startY)

                    val totalDrop = (100f - currentHealthPercent).coerceAtLeast(1f)

                    for (m in 1..totalMonths) {
                        val progress = m.toFloat() / totalMonths.toFloat()
                        val wearProgress = (0.4f * sqrt(progress)) + (0.6f * progress)
                        val estimatedHealth = 100f - (totalDrop * wearProgress)

                        val x = (m.toFloat() / totalMonths.toFloat()) * w
                        val y = h * (1f - (estimatedHealth - 40f) / 60f).coerceIn(0f, h)

                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Draw Gradient Fill Under Curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                FoxAccentOrange.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Main Trajectory Stroke
                    drawPath(
                        path = path,
                        color = FoxAccentOrange,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )

                    // Draw "Today" Anchor Dot
                    val todayY = h * (1f - (currentHealthPercent - 40f) / 60f).coerceIn(0f, h)
                    drawCircle(color = Color.White, radius = 6f, center = Offset(w, todayY))
                    drawCircle(color = FoxAccentOrange, radius = 4f, center = Offset(w, todayY))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-Axis Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Day 0 (100%)", color = FoxTextSecondary, fontSize = 10.sp)
                Text("80% Service Line", color = Color(0xFFFF5252), fontSize = 10.sp)
                Text("Today (${currentHealthPercent.toInt()}%)", color = FoxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}
