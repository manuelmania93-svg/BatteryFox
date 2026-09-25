package com.batteryfox.app.core.engine

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.batteryfox.app.core.permissions.UsageStatsPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppDrainMetric(
    val packageName: String,
    val appName: String,
    val foregroundHours: Float,
    val estimatedDrainMah: Int,
    val drainPercent: Float
)

class RetrospectiveDrainEngine(private val context: Context) {

    private val permissionHelper = UsageStatsPermissionHelper(context)
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    suspend fun getTopHistoricalDrainers(totalDesignMah: Int): List<AppDrainMetric> = withContext(Dispatchers.IO) {
        if (!permissionHelper.hasUsageStatsAccess()) return@withContext emptyList()

        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return@withContext emptyList()

        // Aggregate foreground time per package over the past 30 days
        val aggregatedTimeMap = mutableMapOf<String, Long>()
        for (usage in stats) {
            val totalTime = usage.totalTimeInForeground
            if (totalTime > 0) {
                aggregatedTimeMap[usage.packageName] = (aggregatedTimeMap[usage.packageName] ?: 0L) + totalTime
            }
        }

        // Filter out system launchers and current app
        val filtered = aggregatedTimeMap.filter { (pkg, timeMs) ->
            timeMs > 60_000L && pkg != context.packageName
        }

        val results = mutableListOf<AppDrainMetric>()

        for ((pkg, timeMs) in filtered) {
            val hours = timeMs.toFloat() / (1000f * 60f * 60f)
            // Baseline smartphone app drain: ~280-420 mA per active screen hour
            val estimatedMah = (hours * 340f).toInt()
            val drainPct = if (totalDesignMah > 0) (estimatedMah.toFloat() / totalDesignMah.toFloat()) * 100f else 0f

            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }

            results.add(
                AppDrainMetric(
                    packageName = pkg,
                    appName = appLabel,
                    foregroundHours = hours,
                    estimatedDrainMah = estimatedMah,
                    drainPercent = drainPct
                )
            )
        }

        results.sortedByDescending { it.foregroundHours }.take(5)
    }
}
