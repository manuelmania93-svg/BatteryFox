package com.batteryfox.app.presentation.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryfox.app.core.engine.AppDrainMetric
import com.batteryfox.app.core.engine.InternalResistanceTester
import com.batteryfox.app.core.engine.RetrospectiveDrainEngine
import com.batteryfox.app.core.oem.OemDiagnosticLauncher
import com.batteryfox.app.core.parser.UniversalBugReportParser
import com.batteryfox.app.core.permissions.UsageStatsPermissionHelper
import com.batteryfox.app.core.service.BatteryMonitorService
import com.batteryfox.app.core.storage.BatteryPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

enum class CalibrationStep {
    IDLE,
    DISCHARGING,
    CHARGING,
    SATURATING,
    COMPLETED
}

data class DashboardState(
    val batteryPercent: Int = 0,
    val voltageMv: Int = 0,
    val temperatureCelsius: Float = 0f,
    val currentMa: Int = 0,
    val wattage: Float = 0f,
    val cycleCount: Int? = null,
    val yearsActive: Float = 3.5f,
    val estimatedHealthPercent: Float? = null,
    val factoryDesignMah: Int = 0,
    val currentAvailableMah: Int = 0,
    val isDualCell: Boolean = false,
    val batteryTechnology: String = "Li-poly",
    val isServiceRunning: Boolean = false,
    val isTestingResistance: Boolean = false,
    val isParsingBugReport: Boolean = false,
    val measuredResistanceMilliOhms: Float? = null,
    val testConfidence: String? = null,
    val statusMessage: String? = null,
    val calibrationDriftDetected: Boolean = false,
    val calibrationStep: CalibrationStep = CalibrationStep.IDLE,
    val saturationMinutesRemaining: Int = 45,
    val hasUsagePermission: Boolean = false,
    val topHistoricalDrainers: List<AppDrainMetric> = emptyList(),
    val deviceModelName: String = "",
    val androidVersionString: String = "",
    val customOsName: String = "",
    val factoryLaunchOs: String = "",
    val firstUsageDate: String? = null,
    val currentUptimeHours: Long = 0L,
    val manufactureDate: String? = null
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val resistanceTester = InternalResistanceTester(application)
    private val oemLauncher = OemDiagnosticLauncher(application)
    private val bugReportParser = UniversalBugReportParser()
    private val permissionHelper = UsageStatsPermissionHelper(application)
    private val drainEngine = RetrospectiveDrainEngine(application)
    private val preferences = BatteryPreferences(application)

    private val _state = MutableStateFlow(
        DashboardState(
            measuredResistanceMilliOhms = preferences.getSavedResistance(),
            testConfidence = preferences.getSavedConfidence(),
            factoryDesignMah = preferences.getSavedDesignMah() ?: 0,
            currentAvailableMah = preferences.getSavedAvailableMah() ?: 0,
            calibrationStep = try {
                CalibrationStep.valueOf(preferences.getSavedCalibrationStep())
            } catch (_: Exception) {
                CalibrationStep.IDLE
            }
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
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val soc = if (scale > 0) (level * 100) / scale else 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temp = tempRaw / 10f
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-poly"

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val currentRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Samsung Quirk: Galaxy devices report in mA directly (<10,000), while AOSP/Xiaomi report in uA (>10,000)
        val rawMa = if (kotlin.math.abs(currentRaw) > 10_000) currentRaw / 1000 else currentRaw

        // Normalize OEM current sign inversion
        val currentMa = if (isCharging) kotlin.math.abs(rawMa) else -kotlin.math.abs(rawMa)
        val powerWatts = (voltage / 1000f) * (kotlin.math.abs(currentMa) / 1000f)

        var cycles: Int? = preferences.getSavedParsedCycles()
        if (Build.VERSION.SDK_INT >= 34) {
            val c = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (c >= 0) cycles = c
        }

        val isDualCell = voltage > 5000
        val designMah = preferences.getSavedDesignMah() ?: getFactoryDesignCapacityMah(context)

        val nowMs = System.currentTimeMillis()
        val buildYears = ((nowMs - Build.TIME).toDouble() / (1000L * 60 * 60 * 24 * 365.25)).toFloat()
        val cycleDerivedYears = if ((cycles ?: 0) > 0) (cycles!!.toFloat() / 520f) else 1.0f

        // True Hardware Launch Detection via ro.product.first_api_level
        val firstApi = getFirstApiLevel()
        val launchYears = getEstimatedYearsFromApi(firstApi)
        val resolvedYears = maxOf(buildYears, cycleDerivedYears, launchYears).coerceIn(0.5f, 8.5f)

        val devModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val customOs = detectCustomOs()
        val firstOs = "Android ${getAndroidNameFromApi(firstApi)} (API $firstApi)"

        // Query Android 14+ First Usage Timestamp if exposed by OEM
        var exactFirstUse: String? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val firstUseEpochMs = intent?.getLongExtra("android.os.extra.FIRST_USAGE_DATE", -1L) ?: -1L
            if (firstUseEpochMs > 0) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                exactFirstUse = sdf.format(java.util.Date(firstUseEpochMs))
            }
        }

        val uptimeHours = android.os.SystemClock.elapsedRealtime() / (1000L * 3600L)

        var mfgDateFormatted: String? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val mfgEpochMs = intent?.getLongExtra("android.os.extra.MANUFACTURING_DATE", -1L) ?: -1L
            if (mfgEpochMs > 0) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                mfgDateFormatted = sdf.format(java.util.Date(mfgEpochMs))
            }
        }

        // If cycles exist (Android 14+), calculate wear.
        // If Android <= 13, check saved test or saved bug report. Do NOT default to 100%.
        val calculatedHealth = preferences.getSavedParsedHealth() 
            ?: preferences.getSavedResistance()?.let { r ->
                // Map saved resistance to health if available
                if (r <= 70f) 100f else (100f - ((r - 70f) / 110f) * 35f).coerceIn(45f, 100f)
            }
            ?: if (cycles != null && cycles > 0) {
                val wear = cycles * 0.0225f
                max(50f, 100f - wear)
            } else {
                null // Unknown until test is executed
            }

        val availableMah = preferences.getSavedAvailableMah() ?: ((designMah * (calculatedHealth ?: 100f)) / 100f).toInt()
        val perCellVoltage = if (isDualCell) voltage / 2 else voltage
        val isDrifted = (soc > 20 && perCellVoltage < 3500) || (soc < 80 && perCellVoltage > 4300)

        when (_state.value.calibrationStep) {
            CalibrationStep.DISCHARGING -> {
                if (soc <= 5 || perCellVoltage < 3450) {
                    _state.value = _state.value.copy(
                        calibrationStep = CalibrationStep.CHARGING,
                        statusMessage = "Low cutoff reached! Plug into charger now."
                    )
                    preferences.saveCalibrationStep(CalibrationStep.CHARGING.name)
                }
            }
            CalibrationStep.CHARGING -> {
                if (plugged == 0 && soc < 99) {
                    _state.value = _state.value.copy(
                        statusMessage = "Warning: Charger disconnected early! Reconnect to continue."
                    )
                } else if (soc >= 100) {
                    startSaturationPhase()
                }
            }
            else -> {}
        }

        val running = isServiceRunning(context, BatteryMonitorService::class.java)
        val hasUsage = permissionHelper.hasUsageStatsAccess()

        _state.value = _state.value.copy(
            batteryPercent = soc,
            voltageMv = voltage,
            temperatureCelsius = temp,
            currentMa = currentMa,
            wattage = powerWatts,
            cycleCount = cycles,
            yearsActive = resolvedYears,
            factoryDesignMah = designMah,
            currentAvailableMah = availableMah,
            isDualCell = isDualCell,
            batteryTechnology = tech,
            deviceModelName = devModel,
            androidVersionString = osVersion,
            customOsName = customOs,
            factoryLaunchOs = firstOs,
            firstUsageDate = exactFirstUse,
            currentUptimeHours = uptimeHours,
            manufactureDate = mfgDateFormatted,
            isServiceRunning = running,
            hasUsagePermission = hasUsage,
            estimatedHealthPercent = calculatedHealth,
            calibrationDriftDetected = isDrifted
        )
    }

    fun loadHistoricalDrain() {
        viewModelScope.launch {
            if (permissionHelper.hasUsageStatsAccess()) {
                val list = drainEngine.getTopHistoricalDrainers(_state.value.factoryDesignMah.coerceAtLeast(4000))
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

    fun startCalibrationWizard() {
        val currentSoc = _state.value.batteryPercent
        val targetStep = if (currentSoc <= 6) CalibrationStep.CHARGING else CalibrationStep.DISCHARGING
        val msg = if (currentSoc <= 6) {
            "Cell is depleted. Connect charger to begin 0-100% saturation cycle."
        } else {
            "Discharge battery down to 5% to learn physical low-cutoff voltage."
        }

        _state.value = _state.value.copy(calibrationStep = targetStep, statusMessage = msg)
        preferences.saveCalibrationStep(targetStep.name)
    }

    private fun startSaturationPhase() {
        _state.value = _state.value.copy(
            calibrationStep = CalibrationStep.SATURATING,
            saturationMinutesRemaining = 45,
            statusMessage = "100% reached! Keep connected for 45 min float dwell."
        )
        preferences.saveCalibrationStep(CalibrationStep.SATURATING.name)

        viewModelScope.launch {
            for (min in 45 downTo 1) {
                delay(60_000L)
                _state.value = _state.value.copy(saturationMinutesRemaining = min - 1)
            }
            _state.value = _state.value.copy(
                calibrationStep = CalibrationStep.COMPLETED,
                calibrationDriftDetected = false,
                statusMessage = "PMIC registers calibrated! Cutoff & Qmax locked."
            )
            preferences.saveCalibrationStep(CalibrationStep.COMPLETED.name)
        }
    }

    fun cancelCalibration() {
        _state.value = _state.value.copy(
            calibrationStep = CalibrationStep.IDLE,
            statusMessage = "Calibration wizard cancelled."
        )
        preferences.saveCalibrationStep(CalibrationStep.IDLE.name)
    }

    fun toggleMonitorService() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, BatteryMonitorService::class.java)

        if (_state.value.isServiceRunning) {
            context.stopService(serviceIntent)
            _state.value = _state.value.copy(isServiceRunning = false, statusMessage = "Monitor service stopped.")
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            _state.value = _state.value.copy(isServiceRunning = true, statusMessage = "Monitor service running.")
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    private fun getFirstApiLevel(): Int {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            val levelStr = getMethod.invoke(null, "ro.product.first_api_level", "0") as String
            val level = levelStr.toIntOrNull() ?: 0
            if (level > 0) level else Build.VERSION.SDK_INT
        } catch (_: Exception) {
            Build.VERSION.SDK_INT
        }
    }

    private fun getEstimatedYearsFromApi(firstApi: Int): Float {
        // Approximate release date from factory shipping API level
        val launchEpoch = when (firstApi) {
            21 -> 1415000000000L // Android 5.0 (Late 2014)
            22 -> 1425000000000L // Android 5.1 (Early 2015)
            23 -> 1444000000000L // Android 6.0 (Late 2015)
            24, 25 -> 1472000000000L // Android 7.0/7.1 (Late 2016)
            26, 27 -> 1503000000000L // Android 8.0/8.1 (Late 2017)
            28 -> 1533500000000L // Android 9.0 (Mid 2018)
            29 -> 1567500000000L // Android 10 (Late 2019 / Early 2020)
            30 -> 1599500000000L // Android 11 (Late 2020)
            31, 32 -> 1633500000000L // Android 12 (Late 2021)
            33 -> 1660500000000L // Android 13 (Late 2022)
            34 -> 1696400000000L // Android 14 (Late 2023)
            35 -> 1725300000000L // Android 15 (Late 2024)
            else -> Build.TIME
        }
        val diff = System.currentTimeMillis() - launchEpoch
        return (diff.toDouble() / (1000L * 60 * 60 * 24 * 365.25)).toFloat()
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

            "Stock OS"
        } catch (_: Exception) {
            "Android"
        }
    }

    private fun getFactoryDesignCapacityMah(context: Context): Int {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfileInstance = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val getAveragePowerMethod = powerProfileClass.getMethod("getAveragePower", String::class.java)
            val cap = getAveragePowerMethod.invoke(powerProfileInstance, "battery.capacity") as Double
            cap.toInt()
        } catch (_: Exception) {
            4500
        }
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
                    estimatedHealthPercent = data.estimatedHealthPercent,
                    testConfidence = data.confidence.name,
                    statusMessage = "Test passed (${data.confidence.name} confidence)"
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
                    availableMah = parseResult.report.currentCapacityMah
                )

                _state.value = _state.value.copy(
                    isParsingBugReport = false,
                    estimatedHealthPercent = parseResult.report.healthPercent,
                    cycleCount = parseResult.report.cycleCount,
                    factoryDesignMah = parseResult.report.designCapacityMah,
                    currentAvailableMah = parseResult.report.currentCapacityMah,
                    statusMessage = "Parsed ${parseResult.telemetry.recognizedVendor} logs"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isParsingBugReport = false,
                    statusMessage = "Parse failed: ${e.localizedMessage ?: "Invalid file"}"
                )
            }
        }
    }

    fun launchOemMenu(): Boolean = oemLauncher.launchHighestPriorityDiagnostic()
}
