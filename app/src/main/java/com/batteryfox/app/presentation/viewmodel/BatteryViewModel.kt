package com.batteryfox.app.presentation.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryfox.app.core.engine.AppDrainMetric
import com.batteryfox.app.core.engine.BatteryCapacityEstimate
import com.batteryfox.app.core.engine.BatteryCapacityEstimator
import com.batteryfox.app.core.engine.InternalResistanceTester
import com.batteryfox.app.core.engine.RetrospectiveDrainEngine
import com.batteryfox.app.core.oem.OemDiagnosticLauncher
import com.batteryfox.app.core.parser.UniversalBugReportParser
import com.batteryfox.app.core.permissions.UsageStatsPermissionHelper
import com.batteryfox.app.core.service.BatteryMonitorService
import com.batteryfox.app.core.storage.BatteryPreferences
import com.batteryfox.app.core.storage.SavedHealthReading
import com.batteryfox.app.core.telemetry.BatteryTelemetryReader
import com.batteryfox.app.core.telemetry.BatteryChargeSampleRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardState(
    val batteryPercent: Int? = null,
    val voltageMv: Int? = null,
    val temperatureCelsius: Float? = null,
    val currentMa: Int? = null,
    val currentSource: String? = null,
    val wattage: Float? = null,
    val cycleCount: Int? = null,
    val cycleCountSource: String? = null,
    val estimatedHealthPercent: Float? = null,
    val healthSource: String? = null,
    val healthMeasuredAt: Long? = null,
    val healthHistory: List<SavedHealthReading> = emptyList(),
    val learnedCapacity: BatteryCapacityEstimate? = null,
    val chargeSampleCount: Int = 0,
    val chargeCounterAvailable: Boolean = false,
    val factoryDesignMah: Int? = null,
    val currentAvailableMah: Int? = null,
    val batteryTechnology: String? = null,
    val deviceModelName: String = "",
    val androidVersionString: String = "",
    val customOsName: String = "",
    val factoryLaunchOs: String = "",
    val firstUsageDate: String? = null,
    val manufactureDate: String? = null,
    val currentUptimeHours: Long = 0L,
    val isServiceRunning: Boolean = false,
    val isTestingResistance: Boolean = false,
    val isParsingBugReport: Boolean = false,
    val measuredResistanceMilliOhms: Float? = null,
    val testConfidence: String? = null,
    val statusMessage: String? = null,
    val hasUsagePermission: Boolean = false,
    val topHistoricalDrainers: List<AppDrainMetric> = emptyList()
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val resistanceTester = InternalResistanceTester(application)
    private val oemLauncher = OemDiagnosticLauncher(application)
    private val bugReportParser = UniversalBugReportParser()
    private val permissionHelper = UsageStatsPermissionHelper(application)
    private val drainEngine = RetrospectiveDrainEngine(application)
    private val preferences = BatteryPreferences(application)
    private var foregroundSamplingJob: Job? = null

    private val _state = MutableStateFlow(
        DashboardState(
            estimatedHealthPercent = preferences.getSavedParsedHealth(),
            healthSource = preferences.getSavedParsedHealthSource(),
            healthMeasuredAt = preferences.getSavedParsedHealthTimestamp(),
            healthHistory = preferences.getHealthHistory(),
            learnedCapacity = BatteryCapacityEstimator.estimate(preferences.getChargeSamples()),
            chargeSampleCount = preferences.getChargeSamples().size,
            chargeCounterAvailable = preferences.getChargeSamples().isNotEmpty(),
            cycleCount = preferences.getSavedParsedCycles(),
            factoryDesignMah = preferences.getSavedDesignMah(),
            currentAvailableMah = preferences.getSavedAvailableMah(),
            measuredResistanceMilliOhms = preferences.getSavedResistance(),
            testConfidence = preferences.getSavedConfidence()
        )
    )
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refreshTelemetry()
        loadHistoricalDrain()
    }

    fun refreshTelemetry() {
        val context = getApplication<Application>()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val telemetry = BatteryTelemetryReader.read(context, intent)
        val soc = telemetry.levelPercent
        val voltage = telemetry.voltageMv
        val temp = telemetry.temperatureCelsius
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val chargeSampleCollection = BatteryChargeSampleRecorder.record(context, intent)
        val chargeSamples = chargeSampleCollection.samples

        var cycles: Int? = preferences.getSavedParsedCycles()
        var cycleSource: String? = cycles?.let { "Imported bug report" }
        if (Build.VERSION.SDK_INT >= 34) {
            val c = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (c in 0..100_000) {
                cycles = c
                cycleSource = "Android battery broadcast"
            }
        }

        val designMah = preferences.getSavedDesignMah()

        val firstApi = getFirstApiLevel()
        val devModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val customOs = detectCustomOs()
        val firstOs = firstApi?.let { "Android ${getAndroidNameFromApi(it)} (API $it)" } ?: "Not exposed"

        var exactFirstUse: String? = null
        var mfgDateFormatted: String? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val firstUseEpochMs = intent?.getLongExtra("android.os.extra.FIRST_USAGE_DATE", -1L) ?: -1L
            if (firstUseEpochMs > 0) {
                exactFirstUse = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(firstUseEpochMs))
            }
            val mfgEpochMs = intent?.getLongExtra("android.os.extra.MANUFACTURING_DATE", -1L) ?: -1L
            if (mfgEpochMs > 0) {
                mfgDateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(mfgEpochMs))
            }
        }

        val uptimeHours = SystemClock.elapsedRealtime() / (1000L * 3600L)

        val running = isServiceRunning(context, BatteryMonitorService::class.java)
        val hasUsage = permissionHelper.hasUsageStatsAccess()

        _state.value = _state.value.copy(
            batteryPercent = soc,
            voltageMv = voltage,
            temperatureCelsius = temp,
            currentMa = telemetry.currentMa,
            currentSource = telemetry.currentSource,
            wattage = telemetry.wattage,
            cycleCount = cycles,
            cycleCountSource = cycleSource,
            learnedCapacity = BatteryCapacityEstimator.estimate(chargeSamples),
            chargeSampleCount = chargeSamples.size,
            chargeCounterAvailable = chargeSampleCollection.counterAvailable ||
                chargeSamples.isNotEmpty(),
            factoryDesignMah = designMah,
            currentAvailableMah = preferences.getSavedAvailableMah(),
            batteryTechnology = tech,
            deviceModelName = devModel,
            androidVersionString = osVersion,
            customOsName = customOs,
            factoryLaunchOs = firstOs,
            firstUsageDate = exactFirstUse,
            manufactureDate = mfgDateFormatted,
            currentUptimeHours = uptimeHours,
            isServiceRunning = running,
            hasUsagePermission = hasUsage
        )
    }

    fun startForegroundSampling() {
        if (foregroundSamplingJob?.isActive == true) return
        foregroundSamplingJob = viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1_000L)
                refreshTelemetry()
            }
        }
    }

    fun stopForegroundSampling() {
        foregroundSamplingJob?.cancel()
        foregroundSamplingJob = null
    }

    private fun getFirstApiLevel(): Int? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            val levelStr = getMethod.invoke(null, "ro.product.first_api_level", "0") as String
            levelStr.toIntOrNull()?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun getAndroidNameFromApi(api: Int): String {
        return when (api) {
            26 -> "8.0"; 27 -> "8.1"; 28 -> "9.0"; 29 -> "10"; 30 -> "11"
            31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"
            else -> api.toString()
        }
    }

    private fun detectCustomOs(): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)

            val miui = getMethod.invoke(null, "ro.miui.ui.version.name", "") as String
            if (miui.isNotEmpty()) return "MIUI $miui"

            val hyperOs = getMethod.invoke(null, "ro.mi.os.version.name", "") as String
            if (hyperOs.isNotEmpty()) return "HyperOS $hyperOs"

            val oplus = getMethod.invoke(null, "ro.build.version.oplusrom", "") as String
            if (oplus.isNotEmpty()) return "ColorOS $oplus"

            val oneUi = getMethod.invoke(null, "ro.build.version.oneui", "") as String
            if (oneUi.isNotEmpty()) return "One UI $oneUi"

            if (Build.BRAND.equals("google", ignoreCase = true)) return "Pixel Experience"

            "OS variant not identified"
        } catch (_: Exception) {
            "OS variant not exposed"
        }
    }

    fun loadHistoricalDrain() {
        viewModelScope.launch {
            if (permissionHelper.hasUsageStatsAccess()) {
                val list = drainEngine.getTopHistoricalDrainers()
                _state.value = _state.value.copy(
                    hasUsagePermission = true,
                    topHistoricalDrainers = list
                )
            } else {
                _state.value = _state.value.copy(hasUsagePermission = false)
            }
        }
    }

    fun requestUsagePermission() {
        permissionHelper.openUsageAccessSettings()
    }

    fun toggleMonitorService() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, BatteryMonitorService::class.java)

        if (_state.value.isServiceRunning) {
            context.stopService(serviceIntent)
            _state.value = _state.value.copy(isServiceRunning = false, statusMessage = "Monitor service stopped.")
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                _state.value = _state.value.copy(isServiceRunning = true, statusMessage = "Monitor service starting.")
            } catch (error: SecurityException) {
                _state.value = _state.value.copy(
                    isServiceRunning = false,
                    statusMessage = "Monitor service could not start: ${error.localizedMessage ?: "permission denied"}"
                )
            } catch (error: IllegalStateException) {
                _state.value = _state.value.copy(
                    isServiceRunning = false,
                    statusMessage = "Monitor service could not start: ${error.localizedMessage ?: "Android blocked the request"}"
                )
            }
        }
    }

    fun setStatusMessage(message: String) {
        _state.value = _state.value.copy(statusMessage = message)
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    fun runResistanceStressTest() {
        if (_state.value.isTestingResistance) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isTestingResistance = true, statusMessage = null)
            val result = resistanceTester.executeMultiSampleTest()
            result.onSuccess { data ->
                preferences.saveStressTestResult(data.finalResistanceMilliOhms, data.confidence.name)
                _state.value = _state.value.copy(
                    isTestingResistance = false,
                    measuredResistanceMilliOhms = data.finalResistanceMilliOhms,
                    testConfidence = data.confidence.name,
                    statusMessage = "Experimental resistance reading complete (${data.confidence.name} sample consistency)"
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isTestingResistance = false,
                    statusMessage = error.message ?: "Stress test failed"
                )
            }
        }
    }

    fun parseBugReportUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isParsingBugReport = true, statusMessage = "Parsing dump...")
            val context = getApplication<Application>()
            try {
                val parseResult = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bugReportParser.parseZip(stream)
                    } ?: throw IllegalStateException("Unable to open bug report stream.")
                }

                preferences.saveBugReportData(
                    health = parseResult.report.healthPercent,
                    cycles = parseResult.report.cycleCount,
                    designMah = parseResult.report.designCapacityMah,
                    availableMah = parseResult.report.currentCapacityMah,
                    healthSource = parseResult.report.healthSource
                )

                _state.value = _state.value.copy(
                    isParsingBugReport = false,
                    estimatedHealthPercent = parseResult.report.healthPercent,
                    healthSource = parseResult.report.healthSource,
                    healthMeasuredAt = preferences.getSavedParsedHealthTimestamp(),
                    healthHistory = preferences.getHealthHistory(),
                    cycleCount = parseResult.report.cycleCount,
                    cycleCountSource = parseResult.report.cycleCount?.let { "Imported bug report" },
                    factoryDesignMah = parseResult.report.designCapacityMah,
                    currentAvailableMah = parseResult.report.currentCapacityMah,
                    statusMessage = if (parseResult.report.healthPercent != null) {
                        "Found health data in ${parseResult.telemetry.recognizedVendor} report"
                    } else {
                        "Report parsed, but no valid battery-health/capacity measurement was found"
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isParsingBugReport = false,
                    statusMessage = "Parse failed: ${e.localizedMessage ?: "Invalid file"}"
                )
            }

        }
    }

    fun launchOemMenu(): Boolean {
        val launched = oemLauncher.launchHighestPriorityDiagnostic()
        _state.value = _state.value.copy(
            statusMessage = if (launched) {
                "Opened an available device diagnostic."
            } else {
                "No compatible OEM diagnostic menu could be opened on this phone."
            }
        )
        return launched
    }
}
