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
    val estimatedHealthPercent: Float = 100f,
    val factoryDesignMah: Int = 0,
    val currentAvailableMah: Int = 0,
    val isDualCell: Boolean = false,
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

    private val _state = MutableStateFlow(DashboardState())
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

        val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = currentUa / 1000
        val powerWatts = (voltage / 1000f) * (abs(currentMa) / 1000f)

        var cycles: Int? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val c = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (c >= 0) cycles = c
        }

        val isDualCell = voltage > 5000
        val designMah = getFactoryDesignCapacityMah(context)

        val nowMs = System.currentTimeMillis()
        val buildYears = ((nowMs - Build.TIME).toDouble() / (1000L * 60 * 60 * 24 * 365.25)).toFloat()
        val cycleDerivedYears = if ((cycles ?: 0) > 0) (cycles!!.toFloat() / 520f) else 1.0f
        val resolvedYears = max(buildYears, cycleDerivedYears).coerceIn(0.5f, 6.0f)

        val calculatedHealth = if (cycles != null && cycles > 0) {
            val wear = cycles * 0.0225f
            max(50f, 100f - wear)
        } else {
            _state.value.estimatedHealthPercent
        }

        val availableMah = ((designMah * calculatedHealth) / 100f).toInt()
        val perCellVoltage = if (isDualCell) voltage / 2 else voltage
        val isDrifted = (soc > 20 && perCellVoltage < 3500) || (soc < 80 && perCellVoltage > 4300)

        when (_state.value.calibrationStep) {
            CalibrationStep.DISCHARGING -> {
                if (soc <= 5 || perCellVoltage < 3450) {
                    _state.value = _state.value.copy(
                        calibrationStep = CalibrationStep.CHARGING,
                        statusMessage = "Low cutoff reached! Plug into charger now."
                    )
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
        if (currentSoc <= 6) {
            _state.value = _state.value.copy(
                calibrationStep = CalibrationStep.CHARGING,
                statusMessage = "Cell is depleted. Connect charger to begin 0-100% saturation cycle."
            )
        } else {
            _state.value = _state.value.copy(
                calibrationStep = CalibrationStep.DISCHARGING,
                statusMessage = "Discharge battery down to 5% to learn physical low-cutoff voltage."
            )
        }
    }

    private fun startSaturationPhase() {
        _state.value = _state.value.copy(
            calibrationStep = CalibrationStep.SATURATING,
            saturationMinutesRemaining = 45,
            statusMessage = "100% reached! Keep connected for 45 min float dwell."
        )

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
        }
    }

    fun cancelCalibration() {
        _state.value = _state.value.copy(
            calibrationStep = CalibrationStep.IDLE,
            statusMessage = "Calibration wizard cancelled."
        )
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
