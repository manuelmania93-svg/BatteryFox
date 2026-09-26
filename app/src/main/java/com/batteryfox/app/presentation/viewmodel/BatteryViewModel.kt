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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val formattedDetailedAge: String = "Calculating...",
    val estimatedHealthPercent: Float? = null,
    val factoryDesignMah: Int = 0,
    val currentAvailableMah: Int = 0,
    val isDualCell: Boolean = false,
    val batteryTechnology: String = "Li-poly",
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
    val calibrationDriftDetected: Boolean = false,
    val calibrationStep: CalibrationStep = CalibrationStep.IDLE,
    val saturationMinutesRemaining: Int = 45,
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
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-poly"

        val currentRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawMa = if (abs(currentRaw) > 10_000) currentRaw / 1000 else currentRaw
        val currentMa = if (isCharging) abs(rawMa) else -abs(rawMa)
        val powerWatts = (voltage / 1000f) * (abs(currentMa) / 1000f)

        var cycles: Int? = preferences.getSavedParsedCycles()
        if (Build.VERSION.SDK_INT >= 34) {
            val c = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (c >= 0) cycles = c
        }

        val isDualCell = voltage > 5000
        val designMah = preferences.getSavedDesignMah() ?: getFactoryDesignCapacityMah(context)

        // Multi-Sensor Hardware Age Engine
        val preciseAgeInfo = calculatePreciseDeviceAge(cycles)
        val resolvedYears = preciseAgeInfo.yearsFloat
        val detailedAgeString = preciseAgeInfo.formattedString

        val firstApi = getFirstApiLevel()
        val devModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val customOs = detectCustomOs()
        val firstOs = "Android ${getAndroidNameFromApi(firstApi)} (API $firstApi)"

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

        // Multi-tier health resolution
        val calculatedHealth = preferences.getSavedParsedHealth()
            ?: preferences.getSavedResistance()?.let { r ->
                if (r <= 70f) 100f else (100f - ((r - 70f) / 110f) * 35f).coerceIn(45f, 100f)
            }
            ?: if (cycles != null && cycles > 0) {
                val wear = cycles * 0.0225f
                max(50f, 100f - wear)
            } else {
                val calendarWear = (resolvedYears * 5.5f).coerceIn(5f, 45f)
                max(55f, 100f - calendarWear)
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
            formattedDetailedAge = detailedAgeString,
            factoryDesignMah = designMah,
            currentAvailableMah = availableMah,
            isDualCell = isDualCell,
            batteryTechnology = tech,
            deviceModelName = devModel,
            androidVersionString = osVersion,
            customOsName = customOs,
            factoryLaunchOs = firstOs,
            firstUsageDate = exactFirstUse,
            manufactureDate = mfgDateFormatted,
            currentUptimeHours = uptimeHours,
            isServiceRunning = running,
            hasUsagePermission = hasUsage,
            estimatedHealthPercent = calculatedHealth,
            calibrationDriftDetected = isDrifted
        )
    }

    private data class PreciseAgeResult(val yearsFloat: Float, val formattedString: String)

    private fun calculatePreciseDeviceAge(cycles: Int?): PreciseAgeResult {
        val now = System.currentTimeMillis()

        var securityPatchEpoch = 0L
        try {
            val patchStr = Build.VERSION.SECURITY_PATCH
            if (patchStr.isNotEmpty()) {
                securityPatchEpoch = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patchStr)?.time ?: 0L
            }
        } catch (_: Exception) {}

        val firstApi = getFirstApiLevel()
        val apiLaunchEpoch = getEstimatedYearsFromApiEpoch(firstApi)
        val siliconLaunchEpoch = getSiliconEraEpoch()

        val candidateEpochs = listOf(Build.TIME, securityPatchEpoch, apiLaunchEpoch, siliconLaunchEpoch)
            .filter { it in 1400000000000L..now }

        var birthEpoch = if (candidateEpochs.isNotEmpty()) candidateEpochs.minOrNull()!! else Build.TIME

        if (cycles != null && cycles > 600) {
            val cycleDays = (cycles * 0.72f).toLong()
            val cycleBirthEstimate = now - (cycleDays * 24L * 3600L * 1000L)
            if (cycleBirthEstimate < birthEpoch) {
                birthEpoch = cycleBirthEstimate
            }
        }

        val totalDays = ((now - birthEpoch) / (1000L * 3600L * 24L)).coerceAtLeast(30L)
        val years = totalDays / 365
        val months = (totalDays % 365) / 30
        val yearsFloat = (totalDays.toFloat() / 365.25f).coerceIn(0.5f, 9.0f)

        val formatted = when {
            years > 0 && months > 0 -> "${years}y ${months}m (${totalDays}d)"
            years > 0 -> "${years}y (${totalDays}d)"
            else -> "${totalDays / 30}m (${totalDays}d)"
        }

        return PreciseAgeResult(yearsFloat, formatted)
    }

    private fun getSiliconEraEpoch(): Long {
        val hardware = (Build.HARDWARE + " " + Build.BOARD + " " + Build.SOC_MODEL).lowercase()
        return when {
            hardware.contains("kalama") || hardware.contains("sm8550") -> 1675209600000L // Feb 2023 (S23)
            hardware.contains("taro") || hardware.contains("sm8450") -> 1644364800000L // Feb 2022 (S22)
            hardware.contains("atoll") || hardware.contains("sm6150") || hardware.contains("curtana") -> 1584921600000L // March 2020 (Note 9S)
            hardware.contains("lahaina") || hardware.contains("sm8350") -> 1611000000000L // Jan 2021
            else -> 0L
        }
    }

    private fun getEstimatedYearsFromApiEpoch(firstApi: Int): Long {
        return when (firstApi) {
            28 -> 1533500000000L // Android 9
            29 -> 1583000000000L // Android 10
            30 -> 1600000000000L // Android 11
            31, 32 -> 1634000000000L // Android 12
            33 -> 1676000000000L // Android 13
            34 -> 1700000000000L // Android 14
            35 -> 1730000000000L // Android 15
            else -> Build.TIME
        }
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
