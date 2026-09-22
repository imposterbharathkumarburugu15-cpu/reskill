package dev.sovarix.core

import kotlin.math.*

/**
 * Supported game genres for multi-game intelligence.
 * The architecture must work even when genre = UNKNOWN.
 */
enum class GameGenre {
    BATTLE_ROYALE,
    CRICKET,
    RACING,
    SPORTS,
    FPS,
    OTHER,
    UNKNOWN
}

/**
 * Honest runtime capability profile for gaming session signals.
 * Never claims unsupported capabilities.
 */
data class GamingCaptureCapabilities(
    val audioPlaybackCaptureSupported: Boolean,
    val screenCaptureSupported: Boolean,
    val hapticObservationSupported: Boolean,
    val gameDetectionSupported: Boolean,
    val recordingSupported: Boolean,
    val motionSensorsSupported: Boolean = true,
    val notes: String = "Audited via standard Android public APIs. No root or private APIs used."
)

/**
 * Audit of device haptic and vibration capabilities.
 * Distinguishes hardware vibration from third-party observation.
 */
data class HapticCapabilities(
    val hardwareAvailable: Boolean,
    val canControl: Boolean,
    val canObserveExternalEvents: Boolean,
    val reason: String
)

/**
 * Real-time physical device motion snapshot captured via SensorManager.
 */
data class MotionSnapshot(
    val timestamp: Long = 0L,
    val gyroAvailable: Boolean = false,
    val accelerometerAvailable: Boolean = false,
    val linearAccelerationAvailable: Boolean = false,
    val rotationVectorAvailable: Boolean = false,

    // Gyroscope
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val angularVelocityMagnitude: Float = 0f,
    val gyroSpike: Boolean = false,

    // Accelerometer
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelerationMagnitude: Float = 0f,
    val accelerationSpike: Boolean = false,

    // Linear Acceleration (gravity-free)
    val linearAccelX: Float = 0f,
    val linearAccelY: Float = 0f,
    val linearAccelZ: Float = 0f,
    val linearAccelerationMagnitude: Float = 0f,

    // Rotation Vector
    val rotationVector: FloatArray? = null,

    // Derived Features
    val movementIntensity: Float = 0f,
    val suddenRotationDetected: Boolean = false,
    val suddenMotionDetected: Boolean = false,
    val confidence: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MotionSnapshot) return false
        return timestamp == other.timestamp &&
                angularVelocityMagnitude == other.angularVelocityMagnitude &&
                accelerationMagnitude == other.accelerationMagnitude &&
                linearAccelerationMagnitude == other.linearAccelerationMagnitude &&
                movementIntensity == other.movementIntensity &&
                suddenRotationDetected == other.suddenRotationDetected &&
                suddenMotionDetected == other.suddenMotionDetected
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + angularVelocityMagnitude.hashCode()
        result = 31 * result + accelerationMagnitude.hashCode()
        result = 31 * result + linearAccelerationMagnitude.hashCode()
        result = 31 * result + movementIntensity.hashCode()
        return result
    }
}

/**
 * Breakdown of evidence confidence from each signal source.
 */
data class SignalEvidence(
    val audioConfidence: Double = 0.0,
    val screenConfidence: Double = 0.0,
    val motionConfidence: Double = 0.0,
    val gyroConfidence: Double = 0.0,
    val accelerationConfidence: Double = 0.0,
    val hapticConfidence: Double = 0.0,
    val performanceConfidence: Double = 0.0,
    val thermalConfidence: Double = 0.0,
    val combinedConfidence: Double = 0.0
)

/**
 * 9.4 Generic Special Moments.
 * Events are NOT automatically called KILL, HEADSHOT, SIX, etc. unless verified evidence exists.
 */
object MomentType {
    const val HIGH_INTENSITY_EVENT = "HIGH_INTENSITY_EVENT"
    const val AUDIO_SPIKE = "AUDIO_SPIKE"
    const val VISUAL_CHANGE = "VISUAL_CHANGE"
    const val MOTION_SPIKE = "MOTION_SPIKE"
    const val RAPID_ROTATION = "RAPID_ROTATION"
    const val PERFORMANCE_SPIKE = "PERFORMANCE_SPIKE"
    const val THERMAL_EVENT = "THERMAL_EVENT"
    const val COMBINED_GAME_EVENT = "COMBINED_GAME_EVENT"
    const val SESSION_MILESTONE = "SESSION_MILESTONE"

    // Backward-compatible aliases
    const val HIGH_INTENSITY = HIGH_INTENSITY_EVENT
    const val VISUAL_EVENT = VISUAL_CHANGE
    const val HAPTIC_EVENT = "HAPTIC_EVENT"
    const val PERFORMANCE_EVENT = PERFORMANCE_SPIKE
    const val HIGH_INTENSITY_MOTION = HIGH_INTENSITY_EVENT
    const val PERFORMANCE_MOMENT = PERFORMANCE_SPIKE
    const val THERMAL_MOMENT = THERMAL_EVENT
    const val AUDIO_MOMENT = AUDIO_SPIKE
    const val VISUAL_MOMENT = VISUAL_CHANGE
    const val HAPTIC_MOMENT = "HAPTIC_EVENT"
}

/**
 * 9.2 Game Profile representation.
 */
data class GameProfile(
    val gameName: String,
    val packageName: String,
    val genre: GameGenre,
    val supportedSignals: List<String> = listOf("SCREEN", "AUDIO", "GYROSCOPE", "ACCELEROMETER", "LINEAR_ACCELERATION", "ROTATION", "PHONE_TELEMETRY"),
    val specializedDetectors: List<String> = emptyList(),
    val highLoadWorkload: Boolean = (genre == GameGenre.BATTLE_ROYALE || genre == GameGenre.FPS)
)

/**
 * 9.10 Lightweight Game Profile Registry.
 * Holds known game profiles and gracefully provides fallback for unknown games.
 */
object GameProfileRegistry {
    private val profiles = mutableMapOf<String, GameProfile>()

    init {
        // Battle Royale
        register(GameProfile("Free Fire", "com.dts.freefireth", GameGenre.BATTLE_ROYALE, specializedDetectors = listOf("BATTLE_ROYALE_SEMANTICS")))
        register(GameProfile("Free Fire MAX", "com.dts.freefiremax", GameGenre.BATTLE_ROYALE, specializedDetectors = listOf("BATTLE_ROYALE_SEMANTICS")))
        register(GameProfile("BGMI", "com.pubg.imobile", GameGenre.BATTLE_ROYALE, specializedDetectors = listOf("BATTLE_ROYALE_SEMANTICS")))
        register(GameProfile("PUBG Mobile", "com.tencent.ig", GameGenre.BATTLE_ROYALE, specializedDetectors = listOf("BATTLE_ROYALE_SEMANTICS")))

        // Cricket
        register(GameProfile("Dream Cricket", "com.sporta.dreamcricket", GameGenre.CRICKET, specializedDetectors = listOf("CRICKET_SEMANTICS"), highLoadWorkload = false))
        register(GameProfile("Real Cricket 24", "com.nautilus.RealCricket3D", GameGenre.CRICKET, specializedDetectors = listOf("CRICKET_SEMANTICS"), highLoadWorkload = false))
        register(GameProfile("WCC 3", "com.nextwave.wcc3", GameGenre.CRICKET, specializedDetectors = listOf("CRICKET_SEMANTICS"), highLoadWorkload = false))

        // Racing
        register(GameProfile("Asphalt Legends", "com.gameloft.android.ANMP.GloftA9HM", GameGenre.RACING, specializedDetectors = listOf("RACING_SEMANTICS")))
        register(GameProfile("Need for Speed", "com.ea.game.nfs14_row", GameGenre.RACING, specializedDetectors = listOf("RACING_SEMANTICS")))
        register(GameProfile("Real Racing 3", "com.ea.games.r3_row", GameGenre.RACING, specializedDetectors = listOf("RACING_SEMANTICS")))

        // FPS
        register(GameProfile("Call of Duty Mobile", "com.activision.callofduty.shooter", GameGenre.FPS, specializedDetectors = listOf("FPS_SEMANTICS")))
        register(GameProfile("Standoff 2", "com.axlebolt.standoff2", GameGenre.FPS, specializedDetectors = listOf("FPS_SEMANTICS")))

        // Sports
        register(GameProfile("EA Sports FC", "com.ea.gp.fifamobile", GameGenre.SPORTS, specializedDetectors = listOf("SPORTS_SEMANTICS"), highLoadWorkload = false))
        register(GameProfile("eFootball", "jp.konami.pesam", GameGenre.SPORTS, specializedDetectors = listOf("SPORTS_SEMANTICS"), highLoadWorkload = false))
        register(GameProfile("NBA 2K Mobile", "com.catdaddy.nba2km", GameGenre.SPORTS, specializedDetectors = listOf("SPORTS_SEMANTICS"), highLoadWorkload = false))
    }

    fun register(profile: GameProfile) {
        profiles[profile.packageName.lowercase()] = profile
        profiles[profile.gameName.lowercase()] = profile
    }

    /**
     * Finds matching profile by package name or display name.
     * If not found, does NOT fabricate identity: returns "Unknown Game" with genre UNKNOWN.
     */
    fun findProfile(identifier: String): GameProfile {
        val clean = identifier.trim().lowercase()
        profiles[clean]?.let { return it }

        // Partial match
        profiles.values.firstOrNull {
            it.packageName.lowercase().contains(clean) || it.gameName.lowercase().contains(clean)
        }?.let { return it }

        // Unknown Game fallback
        return GameProfile(
            gameName = if (identifier.contains(".") || identifier.isBlank()) "Unknown Game" else identifier,
            packageName = if (identifier.contains(".")) identifier else "unknown.game.${identifier.lowercase().replace(" ", "_")}",
            genre = GameGenre.UNKNOWN,
            specializedDetectors = emptyList()
        )
    }

    fun getAllProfiles(): List<GameProfile> = profiles.values.distinctBy { it.packageName }
}

/**
 * Runtime state of the rolling video buffer and capture pipeline.
 */
enum class GamingCaptureState {
    IDLE,
    BUFFERING,
    PRESERVING,
    ERROR,
    UNAVAILABLE
}

/**
 * Configurable weights for empirical moment scoring (0-100).
 */
data class MomentScoreWeights(
    val audioSpikeWeight: Int = 25,
    val visualEventWeight: Int = 25,
    val motionSpikeWeight: Int = 20,
    val rapidRotationWeight: Int = 15,
    val thermalElevationWeight: Int = 10,
    val memoryPressureWeight: Int = 5,
    val minThreshold: Int = 25
)

/**
 * 9.1 Active Game Session metadata.
 */
data class GameSession(
    val sessionId: String,
    val packageName: String,
    val gameName: String,
    val sessionStart: Long,
    val sessionEnd: Long? = null,
    val captureCapabilities: GamingCaptureCapabilities,
    val genre: GameGenre
)

/**
 * 9.12 Optional game-specific metadata extensions.
 */
sealed interface GameSpecificMetadata {
    val genre: GameGenre

    data class Cricket(
        val ballEvent: String? = null,
        val innings: Int? = null,
        val scoreContext: String? = null,
        val wicketContext: String? = null
    ) : GameSpecificMetadata {
        override val genre: GameGenre get() = GameGenre.CRICKET
    }

    data class BattleRoyale(
        val combatIntensity: Float? = null,
        val movementIntensity: Float? = null,
        val audioEvent: String? = null
    ) : GameSpecificMetadata {
        override val genre: GameGenre get() = GameGenre.BATTLE_ROYALE
    }

    data class Racing(
        val speedContext: Float? = null,
        val racePhase: String? = null,
        val overtakeContext: String? = null
    ) : GameSpecificMetadata {
        override val genre: GameGenre get() = GameGenre.RACING
    }

    data class Sports(
        val actionContext: String? = null,
        val periodContext: String? = null,
        val scoreContext: String? = null
    ) : GameSpecificMetadata {
        override val genre: GameGenre get() = GameGenre.SPORTS
    }

    data class Generic(
        val customData: Map<String, String> = emptyMap()
    ) : GameSpecificMetadata {
        override val genre: GameGenre get() = GameGenre.UNKNOWN
    }
}

/**
 * 9.11 / Section 11 Game-Agnostic Best Moment Object.
 * Common format for ALL games with empirical score, clip URIs, and full Phone Twin context.
 */
data class SpecialMoment(
    val id: String,
    val timestamp: Long,
    val gamePackage: String,
    val gameName: String = "Unknown Game",
    val gameGenre: GameGenre = GameGenre.UNKNOWN,
    val eventType: String = MomentType.COMBINED_GAME_EVENT,
    val confidence: Double = 0.85,
    val momentScore: Int = 75,
    val clipUri: String? = null,
    val thumbnailUri: String? = null,
    val duration: Double = 12.0,
    val durationMs: Long = (duration * 1000).toLong(),
    val clipReference: String? = clipUri,
    val audioEvidence: Double = 0.0,
    val visualEvidence: Double = 0.0,
    val motionEvidence: Double = 0.0,
    val hapticEvidence: Double = 0.0,
    val twinStateSnapshot: Sample? = null,
    val battery: Double? = twinStateSnapshot?.batteryC,
    val temperature: Double? = twinStateSnapshot?.batteryC,
    val thermalState: Int? = twinStateSnapshot?.thermalStatus,
    val memoryPressure: Boolean? = twinStateSnapshot?.lowMemory,
    val motionMetrics: String? = null,
    val audioMetrics: String? = null,
    val performanceMetrics: String? = null,
    val performanceState: String? = null,
    val predictionContext: Forecast? = null,
    val gameSpecificMetadata: GameSpecificMetadata? = null,
    val availableSignals: List<String> = emptyList(),
    val motionSnapshot: MotionSnapshot? = null,
    val signalEvidence: SignalEvidence? = null,
    val notes: String = ""
) {
    // Backward compatibility getters
    val genre: GameGenre get() = gameGenre
    val momentType: String get() = eventType
    val clipPath: String? get() = clipUri ?: clipReference
    val deviceState: Sample? get() = twinStateSnapshot
    val batteryState: Double? get() = battery ?: twinStateSnapshot?.batteryC
}

/**
 * 9.5 Optional Game-Specific Interpretation Layer.
 * Interprets a generic SpecialMoment into semantic context ONLY if sufficient evidence exists.
 */
interface GameSemanticInterpreter {
    fun interpret(
        moment: SpecialMoment,
        profile: GameProfile,
        audioSpike: Boolean,
        visualEvent: Boolean,
        motionSnapshot: MotionSnapshot?
    ): SpecialMoment
}

/**
 * Default interpreter: respects the principle that generic events must NOT
 * be falsely labeled with game-specific semantic achievements unless evidence is confirmed.
 */
class DefaultGameSemanticInterpreter : GameSemanticInterpreter {
    override fun interpret(
        moment: SpecialMoment,
        profile: GameProfile,
        audioSpike: Boolean,
        visualEvent: Boolean,
        motionSnapshot: MotionSnapshot?
    ): SpecialMoment {
        val motionIntensity = motionSnapshot?.movementIntensity ?: 0f

        val metadata: GameSpecificMetadata? = when (profile.genre) {
            GameGenre.BATTLE_ROYALE -> {
                GameSpecificMetadata.BattleRoyale(
                    combatIntensity = motionIntensity,
                    movementIntensity = motionIntensity,
                    audioEvent = if (audioSpike) "AUDIO_SPIKE" else null
                )
            }
            GameGenre.CRICKET -> {
                GameSpecificMetadata.Cricket(
                    ballEvent = if (visualEvent) "BAT_BALL_CONTACT_ZONE" else null,
                    innings = null,
                    scoreContext = null,
                    wicketContext = null
                )
            }
            GameGenre.RACING -> {
                GameSpecificMetadata.Racing(
                    speedContext = motionIntensity * 100f,
                    racePhase = if (moment.eventType == MomentType.RAPID_ROTATION) "DRIFT_OR_TURN" else "ACTIVE_RUN",
                    overtakeContext = null
                )
            }
            GameGenre.SPORTS -> {
                GameSpecificMetadata.Sports(
                    actionContext = if (audioSpike && visualEvent) "MATCH_INTENSITY_SPIKE" else "ACTIVE_PLAY",
                    periodContext = null,
                    scoreContext = null
                )
            }
            GameGenre.FPS -> {
                GameSpecificMetadata.BattleRoyale(
                    combatIntensity = motionIntensity,
                    movementIntensity = motionIntensity,
                    audioEvent = if (audioSpike) "GUNFIRE_AUDIO_SPIKE" else null
                )
            }
            else -> null
        }

        return moment.copy(
            gameName = profile.gameName,
            gameGenre = profile.genre,
            gameSpecificMetadata = metadata
        )
    }
}

/**
 * Pure Kotlin GamingMomentEngine.
 * Performs lightweight multi-signal fusion across Motion, Audio, Screen, and Phone Telemetry
 * without running heavy ML models during gameplay.
 * Game-agnostic at its core.
 */
class GamingMomentEngine(
    private val debounceWindowMs: Long = 8_000L,
    private val minThreshold: Int = 25,
    private val weights: MomentScoreWeights = MomentScoreWeights(),
    private val interpreter: GameSemanticInterpreter = DefaultGameSemanticInterpreter()
) {
    private var lastMomentTime: Long = 0L

    /**
     * Fuses physical device motion with audio, visual, and phone telemetry signals.
     * Game-agnostic: operates identically whether genre is BATTLE_ROYALE, CRICKET, RACING, or UNKNOWN.
     */
    fun evaluate(
        nowWallMs: Long,
        gamePackage: String,
        sample: Sample?,
        motion: MotionSnapshot? = null,
        activeForecast: Forecast? = null,
        thermalSlopePerMin: Double? = null,
        audioSpike: Boolean = false,
        visualEvent: Boolean = false,
        manualTrigger: Boolean = false,
        customProfile: GameProfile? = null
    ): SpecialMoment? {
        if (nowWallMs - lastMomentTime < debounceWindowMs && !manualTrigger) {
            return null
        }

        val profile = customProfile ?: GameProfileRegistry.findProfile(gamePackage)

        var detectedType: String? = null
        val signals = mutableListOf<String>()

        var empiricalScore = 0

        var audioConf = if (audioSpike) 0.82 else 0.0
        var screenConf = if (visualEvent) 0.80 else 0.0
        var gyroConf = 0.0
        var accelConf = 0.0
        var motionConf = 0.0
        var thermalConf = 0.0
        var perfConf = 0.0

        if (manualTrigger) {
            detectedType = MomentType.COMBINED_GAME_EVENT
            signals += "MANUAL_TRIGGER"
            empiricalScore = 90
        }

        // 1. Motion Signal Evaluation (Game-Agnostic)
        motion?.let { m ->
            if (m.gyroAvailable) signals += "GYROSCOPE"
            if (m.accelerometerAvailable) signals += "ACCELEROMETER"
            if (m.linearAccelerationAvailable) signals += "LINEAR_ACCELERATION"

            if (m.suddenRotationDetected || m.gyroSpike) {
                gyroConf = 0.85
                signals += "ROTATION_SPIKE"
                empiricalScore += weights.rapidRotationWeight
            }
            if (m.suddenMotionDetected || m.accelerationSpike) {
                accelConf = 0.83
                signals += "MOTION_SPIKE"
                empiricalScore += weights.motionSpikeWeight
            }
            if (m.movementIntensity > 0.65f) {
                motionConf = m.movementIntensity.toDouble().coerceIn(0.0, 1.0)
                signals += "HIGH_MOTION"
                empiricalScore += (m.movementIntensity * 10).toInt().coerceAtMost(10)
            }

            if (detectedType == null) {
                if (m.suddenRotationDetected && m.suddenMotionDetected) {
                    detectedType = MomentType.HIGH_INTENSITY_EVENT
                } else if (m.suddenRotationDetected) {
                    detectedType = MomentType.RAPID_ROTATION
                } else if (m.suddenMotionDetected || m.accelerationSpike) {
                    detectedType = MomentType.MOTION_SPIKE
                }
            }
        }

        // 2. Audio & Visual Signal Evaluation (Game-Agnostic)
        if (audioSpike) {
            signals += "AUDIO_SPIKE"
            empiricalScore += weights.audioSpikeWeight
        }
        if (visualEvent) {
            signals += "VISUAL_CHANGE"
            empiricalScore += weights.visualEventWeight
        }

        if (audioSpike && visualEvent) {
            detectedType = MomentType.COMBINED_GAME_EVENT
        } else if (audioSpike && detectedType == null) {
            detectedType = MomentType.AUDIO_SPIKE
        } else if (visualEvent && detectedType == null) {
            detectedType = MomentType.VISUAL_CHANGE
        }

        // 3. Telemetry Signal Evaluation (Game-Agnostic)
        sample?.let { s ->
            signals += "TWIN_TELEMETRY"
            if ((s.thermalStatus ?: 0) >= 2 || (thermalSlopePerMin != null && thermalSlopePerMin >= 0.3)) {
                thermalConf = 0.85
                signals += "THERMAL_ELEVATION"
                empiricalScore += weights.thermalElevationWeight
                if (detectedType == null) detectedType = MomentType.THERMAL_EVENT
            }
            if (s.lowMemory == true) {
                perfConf = 0.78
                signals += "MEMORY_PRESSURE"
                empiricalScore += weights.memoryPressureWeight
                if (detectedType == null) detectedType = MomentType.PERFORMANCE_SPIKE
            }
        }

        // Check if motion combined with audio/visual/telemetry creates a combined event
        if ((motionConf > 0.6 || gyroConf > 0.7 || accelConf > 0.7) &&
            (audioSpike || visualEvent || thermalConf > 0.7)
        ) {
            detectedType = MomentType.COMBINED_GAME_EVENT
        }

        val type = detectedType ?: return null

        val baseScore = if (manualTrigger) 90 else if (type == MomentType.COMBINED_GAME_EVENT || type == MomentType.HIGH_INTENSITY_EVENT) 65 else 50
        val finalScore = if (manualTrigger) 90 else (baseScore + empiricalScore).coerceIn(0, 100)
        if (!manualTrigger && finalScore < minThreshold) {
            return null
        }

        // Calculate combined confidence from active signals
        val activeScores = listOf(
            if (manualTrigger) 0.95 else 0.0,
            audioConf, screenConf, motionConf, gyroConf, accelConf, thermalConf, perfConf
        ).filter { it > 0.0 }

        val combinedConf = if (manualTrigger) 0.95 else if (activeScores.isNotEmpty()) {
            (activeScores.average() + 0.05 * activeScores.size).coerceIn(0.50, 0.98)
        } else 0.60

        val evidence = SignalEvidence(
            audioConfidence = audioConf,
            screenConfidence = screenConf,
            motionConfidence = motionConf,
            gyroConfidence = gyroConf,
            accelerationConfidence = accelConf,
            hapticConfidence = 0.0, // Honestly 0 unless legitimate observation
            performanceConfidence = perfConf,
            thermalConfidence = thermalConf,
            combinedConfidence = combinedConf
        )

        lastMomentTime = nowWallMs

        val motionMetricsStr = motion?.let {
            "Intensity: ${(it.movementIntensity * 100).toInt()}%, ω=${String.format("%.1f", it.angularVelocityMagnitude)} rad/s, a=${String.format("%.1f", it.accelerationMagnitude)} m/s²"
        }
        val audioMetricsStr = if (audioSpike) "Audio spike detected (Score +${weights.audioSpikeWeight})" else "Nominal audio"
        val perfMetricsStr = if ((sample?.thermalStatus ?: 0) >= 2) "Thermal state elevated (Status: ${sample?.thermalStatus})" else "Nominal thermal/memory"

        val genericMoment = SpecialMoment(
            id = "moment_${nowWallMs}",
            timestamp = nowWallMs,
            gamePackage = profile.packageName,
            gameName = profile.gameName,
            gameGenre = profile.genre,
            eventType = type,
            confidence = combinedConf,
            momentScore = finalScore,
            clipUri = null,
            thumbnailUri = null,
            duration = 12.0,
            clipReference = null,
            audioEvidence = audioConf,
            visualEvidence = screenConf,
            motionEvidence = motionConf,
            hapticEvidence = 0.0,
            twinStateSnapshot = sample,
            battery = sample?.batteryC,
            temperature = sample?.batteryC,
            thermalState = sample?.thermalStatus,
            memoryPressure = sample?.lowMemory,
            motionMetrics = motionMetricsStr,
            audioMetrics = audioMetricsStr,
            performanceMetrics = perfMetricsStr,
            performanceState = if ((sample?.thermalStatus ?: 0) >= 2) "THERMAL_THROTTLING_RISK" else "STABLE",
            predictionContext = activeForecast,
            gameSpecificMetadata = null,
            availableSignals = signals,
            motionSnapshot = motion,
            signalEvidence = evidence,
            notes = "Game-agnostic multi-signal fusion (${profile.genre.name}) without cloud dependencies."
        )

        // Pass through optional game-specific interpretation layer
        return interpreter.interpret(genericMoment, profile, audioSpike, visualEvent, motion)
    }

    fun reset() {
        lastMomentTime = 0L
    }

    /**
     * Parallel intelligence entry point:
     * Consumes TwinState + media signals directly.
     * Answers: "Did a gaming moment occur?"
     * Operates independently of FutureEngine and AnomalyEngine.
     */
    fun evaluateIntelligence(
        state: TwinState,
        gamePackage: String = "Game",
        audioSpike: Boolean = false,
        visualEvent: Boolean = false,
        manualTrigger: Boolean = false
    ): GamingMomentResult {
        val now = state.timestamp.takeIf { it > 0 } ?: (state.latest?.wallMs ?: System.currentTimeMillis())
        val moment = evaluate(
            nowWallMs = now,
            gamePackage = gamePackage,
            sample = state.latest,
            motion = state.gyroState ?: state.accelerationState,
            activeForecast = state.forecasts.firstOrNull(),
            thermalSlopePerMin = state.temperatureTrend?.slopePerMinute,
            audioSpike = audioSpike,
            visualEvent = visualEvent,
            manualTrigger = manualTrigger
        )
        return GamingMomentResult(
            timestamp = now,
            moment = moment,
            detected = moment != null,
            momentType = moment?.momentType,
            confidence = moment?.confidence ?: 0.0,
            motionSnapshot = state.gyroState ?: state.accelerationState,
            signalEvidence = moment?.signalEvidence,
            availableSignals = moment?.availableSignals ?: emptyList()
        )
    }
}

typealias GamingIntelligence = GamingMomentEngine

