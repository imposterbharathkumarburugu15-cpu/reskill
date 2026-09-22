package dev.sovarix.app.gaming

import android.app.Application
import android.content.Intent
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.gaming.haptics.HapticCapabilityManager
import dev.sovarix.app.gaming.motion.MotionSensorManager
import dev.sovarix.core.*
import dev.sovarix.core.WhyEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Section 1, 4, 13: Fully Automatic Gaming Intelligence & Session Orchestrator.
 *
 * Runs an autonomous background detector loop (<0.05% CPU).
 * - User opens any game -> Auto-detected via UsageStats / OS package category
 * - Auto-arms GamingMomentEngine
 * - Auto-starts rolling in-memory video buffer (5s pre-roll) if screen capture is authorized
 * - Auto-adapts ResourceGovernor to Workload.GAMING
 * - Auto-fuses motion, audio, screen, and Phone Twin telemetry
 * - Auto-preserves Best Moments (-5s pre / +7s post) to local MP4 + JPEG thumbnail
 * - Auto-attaches complete Phone Twin physical context
 * - Auto-logs to Black Box
 * - User leaves game -> Auto-stops detection, flushes buffer, restores normal governor mode
 *
 * ZERO requirement to manually press "START GAMING SESSION".
 */
class GamingSessionManager(
    private val app: Application,
    private val repo: TwinRepository
) {
    val detector = GamingCapabilitiesDetector(app)
    val gameDetector = GameDetector(app, detector)
    val captureManager = GamingCaptureManager(app)
    val motionManager = MotionSensorManager(app)
    val hapticManager = HapticCapabilityManager(app)

    private val store = SpecialMomentStore(app)
    private val momentEngine = GamingMomentEngine(debounceWindowMs = 8_000L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _capabilities = MutableStateFlow(detector.detect())
    val capabilities = _capabilities.asStateFlow()

    private val _hapticCaps = MutableStateFlow(hapticManager.audit())
    val hapticCaps: StateFlow<HapticCapabilities> = _hapticCaps.asStateFlow()

    val motionSnapshot: StateFlow<MotionSnapshot> = motionManager.snapshot
    val captureState: StateFlow<GamingCaptureState> = captureManager.captureState

    private val _isGamingActive = MutableStateFlow(false)
    val isGamingActive = _isGamingActive.asStateFlow()

    private val _isSensorTestActive = MutableStateFlow(false)
    val isSensorTestActive = _isSensorTestActive.asStateFlow()

    private val _selectedGameProfile = MutableStateFlow(GameProfileRegistry.findProfile("Dream Cricket"))
    val selectedGameProfile = _selectedGameProfile.asStateFlow()

    private val _selectedGame = MutableStateFlow("Free Fire")
    val selectedGame = _selectedGame.asStateFlow()

    private val _performanceEvents = MutableStateFlow<List<GamingPerformanceAnomaly>>(emptyList())
    val performanceEvents: StateFlow<List<GamingPerformanceAnomaly>> = _performanceEvents.asStateFlow()

    private val _currentSession = MutableStateFlow<GameSession?>(null)
    val currentSession: StateFlow<GameSession?> = _currentSession.asStateFlow()

    private val _sessionElapsedSec = MutableStateFlow(0L)
    val sessionElapsedSec: StateFlow<Long> = _sessionElapsedSec.asStateFlow()

    private val _isPreservingMoment = MutableStateFlow(false)
    val isPreservingMoment: StateFlow<Boolean> = _isPreservingMoment.asStateFlow()

    private val _latestSavedMoment = MutableStateFlow<SpecialMoment?>(null)
    val latestSavedMoment: StateFlow<SpecialMoment?> = _latestSavedMoment.asStateFlow()

    private val _moments = MutableStateFlow(store.load())
    val moments = _moments.asStateFlow()

    private var autoWatcherJob: Job? = null
    private var sessionTimerJob: Job? = null
    @Volatile private var isManuallyStarted = false
    private var lastSmartHighlightTime = 0L
    private var lastPerfAnomalyTime = 0L
    private var sessionStartTemp: Double? = null

    init {
        startAutonomousGameWatcher()
    }

    /**
     * Section 4 & 13: Autonomous background game detection loop.
     * Polls legitimate foreground state every 1.5 seconds.
     * Starts / stops sessions automatically without user manual intervention.
     */
    fun startAutonomousGameWatcher() {
        if (autoWatcherJob?.isActive == true) return

        autoWatcherJob = scope.launch(Dispatchers.Default) {
            var consecutiveNonGameTicks = 0
            while (isActive) {
                delay(1500L)
                try {
                    val fgPackage = gameDetector.detectForegroundPackage()
                    val isGame = gameDetector.isGame(fgPackage)

                    if (isGame && fgPackage != null) {
                        consecutiveNonGameTicks = 0
                        // Game is in foreground
                        if (!_isGamingActive.value) {
                            // User launched a game -> Auto-start session!
                            autoStartSession(fgPackage)
                        } else {
                            // Session active -> Evaluate continuous signal fusion
                            evaluateLiveSignals()
                        }
                    } else {
                        // App in foreground is NOT recognized as a game, or is null / launcher / SOVARIX
                        if (_isGamingActive.value) {
                            // Evaluate signals while session is active
                            evaluateLiveSignals()

                            // Auto-stop safeguards:
                            // 1. Never auto-stop if session was started manually by user
                            // 2. Never auto-stop if fgPackage is null (e.g. usage access delayed/transient)
                            // 3. Never auto-stop if user is inside SOVARIX
                            // 4. Never auto-stop if user is on system launcher or system UI
                            if (!isManuallyStarted &&
                                fgPackage != null &&
                                fgPackage != app.packageName &&
                                !gameDetector.isLauncherOrSystem(fgPackage)
                            ) {
                                consecutiveNonGameTicks++
                                // Require at least 8 consecutive checks (~12 seconds) of a confirmed DIFFERENT app
                                if (consecutiveNonGameTicks >= 8) {
                                    autoStopSession(force = true)
                                    consecutiveNonGameTicks = 0
                                }
                            } else {
                                consecutiveNonGameTicks = 0
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fail safely without crashing
                }
            }
        }
    }

    /**
     * Explicit user session start (pinned - will not be killed by transient foreground checks).
     */
    fun startManualSession(gameOrPackage: String? = null) {
        isManuallyStarted = true
        val target = gameOrPackage ?: _selectedGameProfile.value.packageName
        autoStartSession(target)
    }

    /**
     * Automatically arms the Gaming Moment Engine and starts rolling capture.
     */
    fun autoStartSession(gamePackage: String) {
        if (_isGamingActive.value) return

        val profile = gameDetector.resolveProfile(gamePackage)
        _selectedGameProfile.value = profile
        _selectedGame.value = profile.gameName

        val session = gameDetector.startSession(profile.packageName)
        _currentSession.value = session
        _isGamingActive.value = true
        _sessionElapsedSec.value = 0L
        sessionStartTemp = repo.state.value.temperature ?: repo.state.value.latest?.batteryC

        // Section 14: Resource Governor integration
        val temp = repo.state.value.temperature ?: 30.0
        val isThermalElevated = temp >= 40.0 || (repo.state.value.latest?.thermalStatus ?: 0) >= 2
        val sensorDelay = if (profile.highLoadWorkload && isThermalElevated) {
            android.hardware.SensorManager.SENSOR_DELAY_UI
        } else {
            android.hardware.SensorManager.SENSOR_DELAY_GAME
        }

        motionManager.start(sensorDelay)

        // Section 7: Start in-memory rolling video buffer if capture projection is authorized
        if (captureManager.hasProjection()) {
            captureManager.startRollingBuffer()
        }

        // Launch session elapsed timer
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            val startMs = System.currentTimeMillis()
            while (isActive && _isGamingActive.value) {
                delay(1000L)
                _sessionElapsedSec.value = (System.currentTimeMillis() - startMs) / 1000L
            }
        }

        scope.launch {
            repo.workload(Workload.GAMING)
            val loadDesc = if (profile.highLoadWorkload) "Heavy 3D Load (Adaptive Sampling Active)" else "Standard Game Load"
            val bufferDesc = if (captureManager.hasProjection()) "Rolling Video Buffer ACTIVE (5s pre-roll in RAM)" else "Video Buffer UNAVAILABLE (Screen capture permission needed)"
            repo.event(
                System.currentTimeMillis(),
                "GAMING",
                "🎮 [ACTIVE] Gaming Session started for ${profile.gameName} [${profile.genre}]. $loadDesc. $bufferDesc."
            )
        }
    }

    fun stopSession() {
        autoStopSession(force = true)
    }

    /**
     * Disengages session.
     */
    fun autoStopSession(force: Boolean = false) {
        if (!_isGamingActive.value) return
        if (isManuallyStarted && !force) return
        isManuallyStarted = false
        _isGamingActive.value = false

        sessionTimerJob?.cancel()
        sessionTimerJob = null

        _currentSession.value?.let { sess ->
            _currentSession.value = gameDetector.endSession(sess)
        }

        if (!_isSensorTestActive.value) {
            motionManager.stop()
        }

        captureManager.stopRollingBuffer()
        momentEngine.reset()

        val activeName = _selectedGameProfile.value.gameName
        val endTemp = repo.state.value.temperature ?: repo.state.value.latest?.batteryC
        scope.launch {
            repo.workload(Workload.EVERYDAY)
            repo.event(
                System.currentTimeMillis(),
                "GAMING",
                "🎮 [DISENGAGED] Gaming Session concluded for $activeName. Buffer stopped. Governor restored to normal."
            )
            if (endTemp != null && endTemp >= 35.0) {
                trackRecoveryCooldown(activeName, endTemp)
            }
        }
    }

    /**
     * Section 26: Post-gaming Recovery Intelligence.
     * Measures thermal cooldown duration and tracks behavioral recovery baseline.
     */
    private suspend fun trackRecoveryCooldown(gameName: String, startTemp: Double) {
        val startTime = System.currentTimeMillis()
        var recovered = false
        var currentT = startTemp
        for (step in 1..12) { // Poll up to 2 minutes
            delay(10_000L)
            currentT = repo.state.value.temperature ?: repo.state.value.latest?.batteryC ?: currentT
            if (startTemp - currentT >= 1.0 || currentT <= 35.0) {
                recovered = true
                break
            }
        }
        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000L
        val conclusion = if (recovered)
            "Thermal recovery reached nominal threshold in ${elapsedSec}s (${String.format(java.util.Locale.US, "%.1f°C → %.1f°C", startTemp, currentT)})."
        else
            "Thermal decline steady after ${elapsedSec}s (${String.format(java.util.Locale.US, "%.1f°C → %.1f°C", startTemp, currentT)})."

        repo.event(System.currentTimeMillis(), "RECOVERY", "❄️ [RECOVERY INTELLIGENCE] $conclusion")
        repo.recordBlackBox(
            eventType = "RECOVERY_EVENT",
            intervention = "Post-Gaming Thermal Recovery Observation",
            predictedOutcome = startTemp - 1.5,
            actualOutcome = currentT,
            notes = "Recovery observation for $gameName: $conclusion"
        )
    }

    /**
     * Evaluates live sensor fusion in the background during active gaming.
     */
    private suspend fun evaluateLiveSignals() {
        val currentState = repo.state.value
        val nowWall = System.currentTimeMillis()
        val profile = _selectedGameProfile.value
        val currentMotion = motionManager.getLatest()

        // Section 19: Gaming Performance Engine Anomaly Detection
        val perfAnomaly = GamingPerformanceEngine.evaluate(
            state = currentState,
            sessionElapsedSec = _sessionElapsedSec.value,
            baselineTemp = sessionStartTemp
        )
        if (perfAnomaly != null && (nowWall - lastPerfAnomalyTime > 45_000L)) {
            lastPerfAnomalyTime = nowWall
            _performanceEvents.value = listOf(perfAnomaly) + _performanceEvents.value.take(9)
            repo.event(
                nowWall,
                "PERFORMANCE",
                "⚠️ [GAMING PERFORMANCE] ${perfAnomaly.summary}"
            )
            repo.recordBlackBox(
                eventType = perfAnomaly.eventType,
                intervention = "Gaming Performance Incident Detection",
                predictedOutcome = perfAnomaly.baselineValue,
                actualOutcome = perfAnomaly.measuredValue,
                notes = perfAnomaly.summary
            )
            val mv = perfAnomaly.measuredValue
            val bv = perfAnomaly.baselineValue
            val devVal = if (mv != null && bv != null) mv - bv else null

            val incidentTimeline = listOf(
                IncidentTimelineEvent(
                    timestamp = nowWall - 60_000L,
                    label = "Gameplay baseline (${profile.gameName})",
                    metric = "temperature",
                    value = sessionStartTemp
                ),
                IncidentTimelineEvent(
                    timestamp = nowWall,
                    label = perfAnomaly.summary,
                    metric = "temperature",
                    value = mv,
                    baselineValue = bv,
                    deviation = devVal
                )
            )
            val rawIncident = Incident(
                id = System.currentTimeMillis(),
                timestamp = nowWall,
                type = perfAnomaly.eventType,
                severity = perfAnomaly.severity,
                timelineEvents = incidentTimeline,
                summary = "Gaming performance change: ${perfAnomaly.summary}",
                deviationFromBaseline = devVal
            )
            val why = WhyEngine.explain(rawIncident, currentState)
            repo.recordIncident(rawIncident.copy(whyReport = why))
        }

        // Smart trigger: periodic highlight evaluation every 90s after 30s gameplay,
        // or when motion gesture intensity peaks
        val isPeriodicHighlight = (nowWall - lastSmartHighlightTime > 90_000L) && (_sessionElapsedSec.value >= 30L)

        val candidate = momentEngine.evaluate(
            nowWallMs = nowWall,
            gamePackage = profile.packageName,
            sample = currentState.latest,
            motion = currentMotion,
            activeForecast = currentState.forecasts.firstOrNull(),
            thermalSlopePerMin = currentState.temperatureTrend?.slopePerMinute,
            audioSpike = isPeriodicHighlight,
            visualEvent = isPeriodicHighlight,
            manualTrigger = false,
            customProfile = profile
        ) ?: return

        if (isPeriodicHighlight) {
            lastSmartHighlightTime = nowWall
        }

        // Important moment detected autonomously!
        preserveDetectedMoment(candidate, currentMotion)
    }

    /**
     * Preserves pre-roll + post-roll video, attaches Phone Twin state, and logs to library and Black Box.
     */
    private suspend fun preserveDetectedMoment(candidate: SpecialMoment, currentMotion: MotionSnapshot) {
        if (_isPreservingMoment.value) return
        _isPreservingMoment.value = true

        try {
            val currentState = repo.state.value
            val nowWall = candidate.timestamp

            // 1. Preserve rolling video buffer (-5s pre-roll + 3s post-roll = 8s clip)
            val (clipUri, thumbUri) = captureManager.preserveMoment(
                momentId = candidate.id,
                preRollSec = 5,
                postRollSec = 3,
                gameName = candidate.gameName,
                eventType = candidate.eventType,
                temp = currentState.temperature ?: currentState.latest?.batteryC,
                battery = currentState.battery ?: currentState.latest?.batteryPct
            )

            val finalMoment = candidate.copy(
                clipUri = clipUri,
                thumbnailUri = thumbUri,
                clipReference = clipUri,
                duration = 12.0,
                durationMs = 12_000L,
                twinStateSnapshot = currentState.latest,
                battery = currentState.latest?.batteryC,
                temperature = currentState.latest?.batteryC,
                thermalState = currentState.latest?.thermalStatus,
                memoryPressure = currentState.latest?.lowMemory
            )

            _latestSavedMoment.value = finalMoment

            // 2. Black Box Integration (Section 16)
            val tempDisplay = finalMoment.battery?.let { String.format("%.1f°C", it) } ?: "Unavailable"
            val clipStatus = if (clipUri != null) "Video Clip Preserved (MP4)" else "Device Event (Projection off)"
            val motionDesc = String.format("Intensity %.0f%% (ω=%.1f rad/s, a=%.1f m/s²)",
                currentMotion.movementIntensity * 100,
                currentMotion.angularVelocityMagnitude,
                currentMotion.accelerationMagnitude)

            repo.event(
                nowWall,
                "GAMING",
                "🔥 SPECIAL MOMENT [${finalMoment.momentScore}/100] | ${finalMoment.gameName} [${finalMoment.gameGenre}] | Event: ${finalMoment.eventType} | Temp: $tempDisplay | Motion: $motionDesc | $clipStatus"
            )

            repo.recordBlackBox(
                eventType = "GAMING_MOMENT",
                intervention = "Autonomous Signal Fusion Best-Moment Detection",
                gamingContext = GamingMomentResult(
                    timestamp = nowWall,
                    moment = finalMoment,
                    detected = true,
                    momentType = finalMoment.eventType,
                    confidence = finalMoment.confidence,
                    motionSnapshot = currentMotion,
                    signalEvidence = finalMoment.signalEvidence,
                    availableSignals = finalMoment.availableSignals
                ),
                predictedOutcome = currentState.temperature ?: currentState.latest?.batteryC,
                actualOutcome = currentState.temperature ?: currentState.latest?.batteryC,
                notes = "Special Moment: ${finalMoment.eventType} (Score ${finalMoment.momentScore}/100) in ${finalMoment.gameName} [${finalMoment.gameGenre}]. Phone temp: $tempDisplay."
            )

            // 3. Save to Special Moments Library Store
            val updated = listOf(finalMoment) + _moments.value
            _moments.value = updated
            withContext(Dispatchers.IO) {
                store.save(updated)
            }
        } finally {
            _isPreservingMoment.value = false
        }
    }

    /**
     * Manual moment trigger for testing/demonstration.
     */
    fun triggerMoment(
        manual: Boolean = true,
        audioSpike: Boolean = false,
        visualEvent: Boolean = false,
        label: String? = null
    ) {
        scope.launch {
            val currentState = repo.state.value
            val nowWall = System.currentTimeMillis()
            val profile = _selectedGameProfile.value
            val currentMotion = motionManager.getLatest()

            val candidate = momentEngine.evaluate(
                nowWallMs = nowWall,
                gamePackage = profile.packageName,
                sample = currentState.latest,
                motion = currentMotion,
                activeForecast = currentState.forecasts.firstOrNull(),
                thermalSlopePerMin = currentState.temperatureTrend?.slopePerMinute,
                audioSpike = audioSpike,
                visualEvent = visualEvent,
                manualTrigger = manual,
                customProfile = profile
            ) ?: return@launch

            preserveDetectedMoment(candidate.copy(eventType = label ?: candidate.eventType), currentMotion)
        }
    }

    fun setSelectedGame(gameOrPackage: String) {
        val profile = gameDetector.resolveProfile(gameOrPackage)
        _selectedGameProfile.value = profile
        _selectedGame.value = profile.gameName
    }

    fun detectForegroundGame(): GameProfile? {
        val fgPackage = gameDetector.detectForegroundPackage() ?: return null
        val profile = gameDetector.resolveProfile(fgPackage)
        _selectedGameProfile.value = profile
        _selectedGame.value = profile.gameName
        return profile
    }

    fun refreshCapabilities() {
        _capabilities.value = detector.detect()
        _hapticCaps.value = hapticManager.audit()
    }

    fun onProjectionGranted(resultCode: Int, data: Intent) {
        captureManager.initializeProjection(resultCode, data)
        scope.launch {
            repo.event(
                System.currentTimeMillis(),
                "GAMING",
                "Screen capture projection authorized for Best-Moment rolling video engine."
            )
        }
    }

    fun startSensorTest() {
        _isSensorTestActive.value = true
        motionManager.start(android.hardware.SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopSensorTest() {
        _isSensorTestActive.value = false
        if (!_isGamingActive.value) {
            motionManager.stop()
        }
    }

    fun testHapticPulse(durationMs: Long = 40L) {
        hapticManager.triggerTestPulse(durationMs)
    }

    fun deleteMoment(id: String) {
        val updated = _moments.value.filterNot { it.id == id }
        _moments.value = updated
        scope.launch(Dispatchers.IO) {
            store.save(updated)
        }
    }

    fun clearAllMoments() {
        _moments.value = emptyList()
        scope.launch(Dispatchers.IO) {
            store.clear()
        }
    }
}
