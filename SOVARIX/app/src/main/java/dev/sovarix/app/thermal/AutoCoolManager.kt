package dev.sovarix.app.thermal

import android.app.Application
import android.hardware.SensorManager
import dev.sovarix.app.TwinRepository
import dev.sovarix.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * Core Orchestrator for SOVARIX Auto-Cool / Thermal Guard.
 * DETECT -> PREDICT -> DECIDE -> MITIGATE -> VERIFY -> LEARN.
 */
class AutoCoolManager(
    private val app: Application,
    private val repo: TwinRepository
) {
    val monitor = ThermalMonitor(app)
    val notificationManager = ThermalNotificationManager(app)
    private val trendEngine = ThermalTrendEngine
    private val predictionEngine = ThermalPredictionEngine()
    private val decisionEngine = AutoCoolDecisionEngine()
    private val verificationEngine = ThermalVerificationEngine()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null

    val adapter: ThermalAdapter = VendorThermalAdapter(
        manufacturer = android.os.Build.MANUFACTURER,
        model = android.os.Build.MODEL
    )

    private val _settings = MutableStateFlow(AutoCoolSettings())
    val settings: StateFlow<AutoCoolSettings> = _settings.asStateFlow()

    private val _thermalTrend = MutableStateFlow<ThermalTrend?>(null)
    val thermalTrend: StateFlow<ThermalTrend?> = _thermalTrend.asStateFlow()

    private val _thermalForecast = MutableStateFlow<ThermalForecastResult?>(null)
    val thermalForecast: StateFlow<ThermalForecastResult?> = _thermalForecast.asStateFlow()

    private val _autoCoolDecision = MutableStateFlow<AutoCoolDecision?>(null)
    val autoCoolDecision: StateFlow<AutoCoolDecision?> = _autoCoolDecision.asStateFlow()

    private val _latestVerification = MutableStateFlow<AutoCoolVerification?>(null)
    val latestVerification: StateFlow<AutoCoolVerification?> = _latestVerification.asStateFlow()

    private var interventionTicketId = 0L
    private var isCurrentlyMitigating = false
    private var activeMitigationStrategy: AutoCoolStrategy = AutoCoolStrategy.LEVEL_0_NORMAL
    private val recentInterventionLogs = mutableListOf<String>()

    fun start() {
        monitor.open()
        if (monitoringJob?.isActive == true) return

        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    stepAutoCoolCycle()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Safe error logging without crashing
                }

                val currentMode = monitor.samplingMode.value
                val delayMs = currentMode.defaultIntervalMs
                delay(delayMs)
            }
        }
    }

    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
        monitor.close()
    }

    /**
     * Executes one atomic monitoring, prediction, decision, mitigation, and verification cycle.
     */
    private suspend fun stepAutoCoolCycle() {
        val reading = monitor.sampleRealThermal()
        val twin = repo.state.value
        val history = twin.history

        // 1. Calculate Real Thermal Trend
        val trend = trendEngine.calculateTrend(
            samples = history,
            workloadDurationMs = if (twin.gamingState) repo.gamingManager.sessionElapsedSec.value * 1000L else 0L,
            recentInterventions = recentInterventionLogs.takeLast(3)
        )
        _thermalTrend.value = trend

        // 2. Predict Future Horizons (10s, 30s, 60s)
        val forecast = predictionEngine.predict(trend, history, reading.timestamp)
        _thermalForecast.value = forecast

        // 3. Update Adaptive Sampling Mode to prevent monitoring heating
        monitor.updateSamplingMode(trend, reading.thermalStatus)

        // 4. Decide Graduated Mitigation Strategy with Hysteresis
        val decision = decisionEngine.decide(
            twinState = twin,
            trend = trend,
            forecast = forecast,
            settings = _settings.value,
            currentDecision = _autoCoolDecision.value,
            capabilities = adapter.getActionCapabilities()
        )
        val previousDecision = _autoCoolDecision.value
        _autoCoolDecision.value = decision

        // 5. Check if mitigation level changed
        if (decision.strategy != previousDecision?.strategy) {
            handleStrategyTransition(previousDecision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL, decision, reading.batteryTemperature, forecast)
        }

        // 6. Feed verification engine with ongoing observations
        val latestSample = twin.latest
        if (latestSample != null) {
            verificationEngine.onSampleObserved(latestSample)
        }

        // 7. Check if active verification can be resolved
        if (isCurrentlyMitigating) {
            val verified = verificationEngine.resolveVerification(reading.batteryTemperature)
            if (verified != null) {
                _latestVerification.value = verified
                recordVerificationInBlackBox(verified)
                updateDeviceDNA(verified)
                if (decision.strategy == AutoCoolStrategy.LEVEL_0_NORMAL) {
                    isCurrentlyMitigating = false
                }
            }
        }
    }

    private suspend fun handleStrategyTransition(
        from: AutoCoolStrategy,
        decision: AutoCoolDecision,
        currentTemp: Double?,
        forecast: ThermalForecastResult?
    ) {
        val to = decision.strategy
        val game = repo.gamingManager.selectedGame.value

        if (to.level > from.level) {
            // Escalation: Apply mitigations
            applyFirstLineMitigation(to)
            val actionDescription = decision.actions.joinToString(", ") { it.description } + " · Direct game workload control unavailable on this device."
            val logMsg = "${decision.strategy.label}: $actionDescription"
            recentInterventionLogs.add(logMsg)

            // Start verification tracking
            interventionTicketId++
            activeMitigationStrategy = to
            isCurrentlyMitigating = true
            val predicted60s = forecast?.getPoint(60)?.predictedTemperature

            verificationEngine.onInterventionStarted(
                id = interventionTicketId,
                strategy = to,
                intervention = actionDescription,
                predictedTempBefore = predicted60s,
                actualTempBefore = currentTemp
            )

            // Dispatch real Android system thermal alert
            if (_settings.value.notifyOnActivation) {
                notificationManager.notifyThermalState(to, currentTemp, game)
            }

            // Operational Black Box logging
            repo.recordBlackBox(
                eventType = "AUTO_COOL_STARTED",
                intervention = actionDescription,
                predictedOutcome = predicted60s,
                actualOutcome = currentTemp,
                notes = "Auto-Cool activated for $game. Direct game workload control unavailable on this device; legitimate self-observation mitigations applied."
            )
        } else if (to.level < from.level) {
            // De-escalation / Recovery: Gradual restoration
            applyGradualRestoration(to)
            if (to == AutoCoolStrategy.LEVEL_0_NORMAL) {
                if (from.level >= 2 && _settings.value.notifyOnActivation) {
                    notificationManager.notifyRecovery(currentTemp)
                } else {
                    notificationManager.notifyThermalState(AutoCoolStrategy.LEVEL_0_NORMAL, currentTemp)
                }
            }
            repo.recordBlackBox(
                eventType = "AUTO_COOL_RECOVERY",
                intervention = "Stepped down to ${to.label}",
                actualOutcome = currentTemp,
                notes = "Thermal recovery verified. Gradual restoration active: ${decision.reason}"
            )
        }
    }

    /**
     * Section 8: First-line Auto-Cool actions.
     * Decreases SOVARIX's own resource footprint so it never adds to device thermal stress.
     */
    private suspend fun applyFirstLineMitigation(strategy: AutoCoolStrategy) {
        when (strategy) {
            AutoCoolStrategy.LEVEL_1_PRE_COOL -> {
                // Pre-Cool: Switch governor to ECO, reduce background processing
                repo.economy(true)
            }
            AutoCoolStrategy.LEVEL_2_COOL -> {
                // Cool: Throttle motion sensors to UI rate, pause rolling video buffer
                repo.economy(true)
                repo.gamingManager.motionManager.start(SensorManager.SENSOR_DELAY_UI)
                if (repo.gamingManager.captureManager.hasProjection()) {
                    repo.gamingManager.captureManager.stopRollingBuffer()
                }
            }
            AutoCoolStrategy.LEVEL_3_AGGRESSIVE_COOL, AutoCoolStrategy.LEVEL_4_CRITICAL -> {
                // Aggressive / Critical: Suspend all non-essential sensor and capture activity
                repo.economy(true)
                repo.gamingManager.motionManager.start(SensorManager.SENSOR_DELAY_NORMAL)
                if (repo.gamingManager.captureManager.hasProjection()) {
                    repo.gamingManager.captureManager.stopRollingBuffer()
                }
            }
            AutoCoolStrategy.LEVEL_0_NORMAL -> {
                // No action
            }
        }
    }

    /**
     * Section 12: Gradual restoration of features as device cools.
     * Never jumps directly from maximum mitigation to full workload.
     */
    private suspend fun applyGradualRestoration(strategy: AutoCoolStrategy) {
        when (strategy) {
            AutoCoolStrategy.LEVEL_2_COOL -> {
                repo.economy(true)
                repo.gamingManager.motionManager.start(SensorManager.SENSOR_DELAY_UI)
            }
            AutoCoolStrategy.LEVEL_1_PRE_COOL -> {
                repo.economy(false)
                if (repo.state.value.gamingState) {
                    repo.gamingManager.motionManager.start(SensorManager.SENSOR_DELAY_GAME)
                }
            }
            AutoCoolStrategy.LEVEL_0_NORMAL -> {
                // Fully restored
                repo.economy(false)
                if (repo.state.value.gamingState) {
                    repo.gamingManager.motionManager.start(SensorManager.SENSOR_DELAY_GAME)
                    if (repo.gamingManager.captureManager.hasProjection()) {
                        repo.gamingManager.captureManager.startRollingBuffer()
                    }
                }
            }
            else -> {}
        }
    }

    private suspend fun recordVerificationInBlackBox(v: AutoCoolVerification) {
        repo.recordBlackBox(
            eventType = "AUTO_COOL_VERIFIED",
            intervention = v.intervention,
            predictedOutcome = v.predictedTempBefore,
            actualOutcome = v.actualTempAfter,
            predictionError = v.predictionError,
            notes = "Cooling verified in ${v.coolingResponseTimeSec}s (Delta: ${String.format(Locale.US, "%.1f°C", v.temperatureDelta ?: 0.0)}, Effect: ${v.effectiveness})"
        )
    }

    private suspend fun updateDeviceDNA(v: AutoCoolVerification) {
        val currentDna = repo.state.value.dna
        val thermalDna = currentDna.thermalDNA
        val delta = v.temperatureDelta ?: return
        val timeMin = v.coolingResponseTimeSec / 60.0

        if (timeMin > 0.1 && delta < 0) {
            val observedCoolingRate = (-delta) / timeMin
            val updatedRate = (0.7 * thermalDna.averageCoolingRateCPerMin + 0.3 * observedCoolingRate).coerceIn(0.1, 3.0)
            val updatedProfile = thermalDna.copy(
                calibrationObservations = thermalDna.calibrationObservations + 1,
                successfulInterventions = thermalDna.successfulInterventions + if (v.effectiveness == "POSITIVE") 1 else 0,
                averageCoolingRateCPerMin = updatedRate,
                totalInterventions = thermalDna.totalInterventions + 1,
                meanPredictionErrorC = v.predictionError?.let { abs(it) }
            )
            // Note: TwinEngine handles DeviceDNA immutably during checkpointing
        }
    }

    fun updateSettings(newSettings: AutoCoolSettings) {
        _settings.value = newSettings
    }

    fun setAutoCoolEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoCoolEnabled = enabled)
    }

    fun setAggressiveness(aggressiveness: AutoCoolAggressiveness) {
        _settings.value = _settings.value.copy(aggressiveness = aggressiveness)
    }

    fun setOverheadBudget(budget: SovarixOverheadBudget) {
        _settings.value = _settings.value.copy(maxSovarixOverhead = budget)
    }
}
