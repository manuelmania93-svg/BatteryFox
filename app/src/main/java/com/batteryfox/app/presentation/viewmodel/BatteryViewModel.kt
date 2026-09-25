package com.batteryfox.app.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryfox.app.core.engine.InternalResistanceTester
import com.batteryfox.app.core.oem.OemDiagnosticLauncher
import com.batteryfox.app.core.parser.UniversalBugReportParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

data class DashboardState(
    val batteryPercent: Int = 0,
    val voltageMv: Int = 0,
    val temperatureCelsius: Float = 0f,
    val currentMa: Int = 0,
    val wattage: Float = 0f,
    val cycleCount: Int? = null,
    val estimatedHealthPercent: Float = 100f,
    val factoryDesignMah: Int = 0,
    val currentAvailableMah: Int = 0,
    val isDualCell: Boolean = false,
    val isTestingResistance: Boolean = false,
    val isParsingBugReport: Boolean = false,
    val measuredResistanceMilliOhms: Float? = null,
    val testConfidence: String? = null,
    val statusMessage: String? = null,
    val calibrationDriftDetected: Boolean = false
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val resistanceTester = InternalResistanceTester(application)
    private val oemLauncher = OemDiagnosticLauncher(application)
    private val bugReportParser = UniversalBugReportParser()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refreshTelemetry()
    }

    fun refreshTelemetry() {
        val context = getApplication<Application>()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val soc = if (scale > 0) (level * 100) / scale else 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

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

        val calculatedHealth = if (cycles != null && cycles > 0) {
            val wear = cycles * 0.0225f
            max(50f, 100f - wear)
        } else {
            _state.value.estimatedHealthPercent
        }

        val availableMah = ((designMah * calculatedHealth) / 100f).toInt()
        val perCellVoltage = if (isDualCell) voltage / 2 else voltage
        val isDrifted = (soc > 20 && perCellVoltage < 3500) || (soc < 80 && perCellVoltage > 4300)

        _state.value = _state.value.copy(
            batteryPercent = soc,
            voltageMv = voltage,
            temperatureCelsius = temp,
            currentMa = currentMa,
            wattage = powerWatts,
            cycleCount = cycles,
            factoryDesignMah = designMah,
            currentAvailableMah = availableMah,
            isDualCell = isDualCell,
            estimatedHealthPercent = calculatedHealth,
            calibrationDriftDetected = isDrifted
        )
    }

    private fun getFactoryDesignCapacityMah(context: Context): Int {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfileInstance = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val getAveragePowerMethod = powerProfileClass.getMethod("getAveragePower", String::class.java)
            val cap = getAveragePowerMethod.invoke(powerProfileInstance, "battery.capacity") as Double
            cap.toInt()
        } catch (e: Exception) {
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
