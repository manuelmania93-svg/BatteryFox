package com.batteryfox.app.presentation.viewmodel

import android.app.Application
import android.content.Intent
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
import com.batteryfox.app.core.service.MonitorState
import com.batteryfox.app.core.telemetry.batteryIntents
import com.batteryfox.app.core.engine.CapacityAnalysis
import com.batteryfox.app.core.telemetry.BatteryMonitoringPolicy
import com.batteryfox.app.core.service.BatteryMonitorService
import com.batteryfox.app.core.storage.BatteryPreferences
import com.batteryfox.app.core.storage.HealthHistory
import com.batteryfox.app.core.storage.HealthTrend
import com.batteryfox.app.core.storage.SavedHealthReading
import com.batteryfox.app.core.parser.ImportLimitExceededException
import com.batteryfox.app.core.parser.LatestImportRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.batteryfox.app.core.telemetry.BatteryTelemetryReader
import com.batteryfox.app.core.telemetry.BatteryChargeSampleRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val healthImportedAt: Long? = null,
    val healthHistory: List<SavedHealthReading> = emptyList(),
    val healthTrend: HealthTrend? = null,
    val learnedCapacity: BatteryCapacityEstimate? = null,
    val chargeSampleCount: Int = 0,
    val chargeCounterAvailable: Boolean = false,
    val chargeCounterHistoricallyAvailable: Boolean = false,
    val capacityCollectionMessage: String = "Waiting for a live reading",
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

class BatteryViewModel @JvmOverloads constructor(
    application: Application,
    private val preferences: com.batteryfox.app.domain.repository.BatteryRepository = BatteryPreferences(application)
) : AndroidViewModel(application) {

    private val resistanceTester = InternalResistanceTester(application)
    private val oemLauncher = OemDiagnosticLauncher(application)
    private val bugReportParser = UniversalBugReportParser()
    private val permissionHelper = UsageStatsPermissionHelper(application)
    private val drainEngine = RetrospectiveDrainEngine(application)
    private val reportImport = com.batteryfox.app.domain.usecase.ImportBatteryReport(bugReportParser, preferences)
    private val analyzeCapacity = com.batteryfox.app.domain.usecase.AnalyzeCapacity()
    private var resistanceJob: Job? = null
    private var foregroundSamplingJob: Job? = null
    private val importRunner = LatestImportRunner<Pair<UniversalBugReportParser.ParseResult, com.batteryfox.app.domain.usecase.ImportBatteryReport.Saved>>(viewModelScope)

    private val firstApi by lazy { getFirstApiLevel() }
    private val customOs by lazy { detectCustomOs() }
    private val sampleMutex = Mutex()
    @Volatile private var lastHistoryReadAt = 0L
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            MonitorState.isRunning.collect { running ->
                _state.update { it.copy(isServiceRunning = running) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            sampleMutex.withLock {
                val saved = run {
                    val history = preferences.getHealthHistory()
                    DashboardState(
                        estimatedHealthPercent = preferences.getSavedParsedHealth(),
                        healthSource = preferences.getSavedParsedHealthSource(),
                        healthMeasuredAt = preferences.getSavedParsedHealthMeasuredAt(),
                        healthImportedAt = preferences.getSavedParsedHealthTimestamp(),
                        healthHistory = history,
                        healthTrend = HealthHistory.trend(history),
                        measuredResistanceMilliOhms = preferences.getSavedResistance(),
                        testConfidence = preferences.getSavedConfidence()
                    )
                }
                _state.update { it.copy(
                    estimatedHealthPercent = saved.estimatedHealthPercent, healthSource = saved.healthSource,
                    healthMeasuredAt = saved.healthMeasuredAt, healthImportedAt = saved.healthImportedAt,
                    healthHistory = saved.healthHistory, healthTrend = saved.healthTrend,
                    measuredResistanceMilliOhms = saved.measuredResistanceMilliOhms,
                    testConfidence = saved.testConfidence
                ) }
            }
        }
        loadHistoricalDrain()
    }

    private suspend fun refreshTelemetry(intent: Intent) = withContext(Dispatchers.IO) {
        sampleMutex.withLock {
            val context = getApplication<Application>()

            val telemetry = BatteryTelemetryReader.read(context, intent)
            val soc = telemetry.levelPercent
            val voltage = telemetry.voltageMv
            val temp = telemetry.temperatureCelsius
            val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            val chargeSampleCollection = BatteryChargeSampleRecorder.record(context, intent, includeStoredSamples = false)
            val now = SystemClock.elapsedRealtime()
            val refreshHistory = chargeSampleCollection.samples.isNotEmpty() || lastHistoryReadAt == 0L ||
                now - lastHistoryReadAt >= BatteryMonitoringPolicy.SAMPLE_INTERVAL_MS
            val chargeSamples = if (refreshHistory) {
                lastHistoryReadAt = now
                chargeSampleCollection.samples.ifEmpty { preferences.getChargeSamples() }
            } else null
            val analysis = chargeSamples?.let { analyzeCapacity(it) }

            var cycles: Int? = preferences.getSavedParsedCycles()
            var cycleSource: String? = cycles?.let { "Imported bug report" }
            if (Build.VERSION.SDK_INT >= 34) {
                val c = intent.getIntExtra("android.os.extra.CYCLE_COUNT", -1)
                if (c in 0..100_000) {
                    cycles = c
                    cycleSource = "Android battery broadcast"
                }
            }

            val designMah = preferences.getSavedDesignMah()

            val devModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val firstOs = firstApi?.let { "Android ${getAndroidNameFromApi(it)} (API $it)" } ?: "Not exposed"

            var exactFirstUse: String? = null
            var mfgDateFormatted: String? = null
            if (Build.VERSION.SDK_INT >= 34) {
                val firstUseEpochMs = intent.getLongExtra("android.os.extra.FIRST_USAGE_DATE", -1L)
                if (firstUseEpochMs > 0) {
                    exactFirstUse = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(firstUseEpochMs))
                }
                val mfgEpochMs = intent.getLongExtra("android.os.extra.MANUFACTURING_DATE", -1L)
                if (mfgEpochMs > 0) {
                    mfgDateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(mfgEpochMs))
                }
            }

            val uptimeHours = SystemClock.elapsedRealtime() / (1000L * 3600L)

            val hasUsage = permissionHelper.hasUsageStatsAccess()

            _state.update { current -> current.copy(
                batteryPercent = soc,
                voltageMv = voltage,
                temperatureCelsius = temp,
                currentMa = telemetry.currentMa,
                currentSource = telemetry.currentSource,
                wattage = telemetry.wattage,
                cycleCount = cycles,
                cycleCountSource = cycleSource,
                learnedCapacity = if (analysis != null) analysis.estimate else current.learnedCapacity,
                chargeSampleCount = chargeSamples?.size ?: current.chargeSampleCount,
                chargeCounterAvailable = chargeSampleCollection.counterAvailable,
                chargeCounterHistoricallyAvailable = chargeSamples?.isNotEmpty() ?: current.chargeCounterHistoricallyAvailable,
                capacityCollectionMessage = analysis?.let { analyzeCapacity.progress(it) } ?: current.capacityCollectionMessage,
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
                hasUsagePermission = hasUsage
            ) }
        }
    }

    fun startForegroundSampling() {
        if (foregroundSamplingJob?.isActive == true) return
        lastHistoryReadAt = 0L
        foregroundSamplingJob = viewModelScope.launch {
            batteryIntents(getApplication()).collect { intent -> refreshTelemetry(intent) }
        }
    }

    fun stopForegroundSampling() {
        cancelResistanceStressTest()
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
                _state.update { it.copy(
                    hasUsagePermission = true,
                    topHistoricalDrainers = list
                ) }
            } else {
                _state.update { it.copy(hasUsagePermission = false) }
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
            _state.update { it.copy(statusMessage = "Monitor service stop requested.") }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                _state.update { it.copy(statusMessage = "Monitor service starting.") }
            } catch (error: SecurityException) {
                _state.update { it.copy(
                    statusMessage = "Monitor service could not start: ${error.localizedMessage ?: "permission denied"}"
                ) }
            } catch (error: IllegalStateException) {
                _state.update { it.copy(
                    statusMessage = "Monitor service could not start: ${error.localizedMessage ?: "Android blocked the request"}"
                ) }
            }
        }
    }

    fun setStatusMessage(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    fun runResistanceStressTest() {
        if (resistanceJob?.isActive == true) return
        _state.update { it.copy(isTestingResistance = true, statusMessage = null) }
        resistanceJob = viewModelScope.launch {
            try {
                resistanceTester.executeMultiSampleTest().onSuccess { data ->
                    withContext(Dispatchers.IO) {
                        sampleMutex.withLock {
                            coroutineContext.ensureActive()
                            preferences.saveStressTestResult(data.finalResistanceMilliOhms, data.confidence.name)
                        }
                    }
                    _state.update { it.copy(
                        measuredResistanceMilliOhms = data.finalResistanceMilliOhms,
                        testConfidence = data.confidence.name,
                        statusMessage = "Experimental resistance reading complete (${data.confidence.name} sample consistency)"
                    ) }
                }.onFailure { error ->
                    _state.update { it.copy(statusMessage = error.message ?: "Stress test failed") }
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(statusMessage = "Experimental test cancelled; no new result saved.") }
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(statusMessage = error.message ?: "Stress test failed") }
            } finally {
                _state.update { it.copy(isTestingResistance = false) }
            }
        }
    }

    fun cancelResistanceStressTest() { resistanceJob?.cancel() }

    /**
     * Imports a bug report. Only one import runs at a time: starting another cancels the previous
     * one, and only the latest import may update state or storage.
     */
    fun parseBugReportUri(uri: Uri) {
        _state.update { it.copy(isParsingBugReport = true, statusMessage = "Parsing dump...") }
        val context = getApplication<Application>()
        importRunner.start(
            work = {
                withContext(Dispatchers.IO) {
                    val job = coroutineContext[Job]
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val parsed = reportImport.parse(stream, checkCancelled = { job?.ensureActive() })
                        sampleMutex.withLock {
                            job?.ensureActive()
                            parsed to reportImport.save(parsed)
                        }
                    } ?: throw IllegalStateException("Unable to open bug report stream.")
                }
            },
            onSuccess = { (parsed, saved) -> commitImport(parsed, saved) },
            onFailure = { e ->
                val reason = when (e) {
                    is ImportLimitExceededException -> "File rejected: ${e.message}"
                    else -> "Parse failed: ${e.localizedMessage ?: "Invalid file"}"
                }
                _state.update { it.copy(isParsingBugReport = false, statusMessage = reason) }
            }
        )
    }

    private fun commitImport(parseResult: UniversalBugReportParser.ParseResult,
        saved: com.batteryfox.app.domain.usecase.ImportBatteryReport.Saved) {
        val outcome = saved.outcome
        when (outcome) {
            HealthHistory.AddOutcome.DUPLICATE -> {
                _state.update { it.copy(
                    isParsingBugReport = false,
                    statusMessage = "This report was already imported; nothing changed."
                ) }
            }
            HealthHistory.AddOutcome.BEFORE_RESET -> {
                _state.update { it.copy(
                    isParsingBugReport = false,
                    statusMessage = "This report was captured before the battery reset, so it was not imported."
                ) }
            }
            HealthHistory.AddOutcome.ADDED -> {
                val history = saved.history
                _state.update { it.copy(
                    isParsingBugReport = false,
                    estimatedHealthPercent = saved.health,
                    healthSource = saved.source,
                    healthMeasuredAt = saved.measuredAt,
                    healthImportedAt = saved.importedAt,
                    healthHistory = history,
                    healthTrend = HealthHistory.trend(history),
                    cycleCount = parseResult.report.cycleCount,
                    cycleCountSource = parseResult.report.cycleCount?.let { "Imported bug report" },
                    factoryDesignMah = parseResult.report.designCapacityMah,
                    currentAvailableMah = parseResult.report.currentCapacityMah,
                    statusMessage = if (parseResult.report.healthPercent != null) {
                        "Found health data in ${parseResult.telemetry.recognizedVendor} report"
                    } else {
                        "Report parsed, but no valid battery-health/capacity measurement was found"
                    }
                ) }
            }
        }
    }

    /** The user replaced the battery (or wants a clean start): drop the old battery's data. */
    fun resetForNewBattery() {
        cancelResistanceStressTest()
        importRunner.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            sampleMutex.withLock {
                preferences.resetForNewBattery()
                lastHistoryReadAt = 0L
                _state.update { it.copy(
                    isParsingBugReport = false,
                    estimatedHealthPercent = null,
                    healthSource = null,
                    healthMeasuredAt = null,
                    healthImportedAt = null,
                    healthHistory = emptyList(),
                    healthTrend = null,
                    cycleCount = null,
                    cycleCountSource = null,
                    factoryDesignMah = null,
                    currentAvailableMah = null,
                    learnedCapacity = null,
                    chargeSampleCount = 0,
                    chargeCounterAvailable = false,
                    chargeCounterHistoricallyAvailable = false,
                    capacityCollectionMessage = "Collecting new battery evidence",
                    measuredResistanceMilliOhms = null,
                    testConfidence = null,
                    statusMessage = "Battery history reset. Reports captured before now will be ignored."
                ) }
            }
        }
    }

    fun launchOemMenu(): Boolean {
        val launched = oemLauncher.launchHighestPriorityDiagnostic()
        _state.update { it.copy(
            statusMessage = if (launched) {
                "Opened an available device diagnostic."
            } else {
                "No compatible OEM diagnostic menu could be opened on this phone."
            }
        ) }
        return launched
    }
}
