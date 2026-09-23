package dev.sovarix.core

/**
 * Section 1: Real hardware thermal capabilities discovery.
 * Strictly reflects actual public Android API availability.
 */
data class ThermalCapabilities(
    val thermalStatusAvailable: Boolean,
    val thermalHeadroomAvailable: Boolean,
    val thermalForecastAvailable: Boolean,
    val cpuHeadroomAvailable: Boolean,
    val gpuHeadroomAvailable: Boolean,
    val batteryTemperatureAvailable: Boolean
) {
    companion object {
        val DEFAULT = ThermalCapabilities(
            thermalStatusAvailable = true,
            thermalHeadroomAvailable = false,
            thermalForecastAvailable = false,
            cpuHeadroomAvailable = false,
            gpuHeadroomAvailable = false,
            batteryTemperatureAvailable = true
        )
    }
}

/**
 * Section 2: Adaptive Thermal Sampling modes.
 * Ensures thermal monitoring never contributes to device heating.
 */
enum class AdaptiveSamplingMode(val defaultIntervalMs: Long, val label: String) {
    STABLE(20_000L, "STABLE (20s)"),
    RISING(10_000L, "RISING (10s)"),
    HOT(5_000L, "HOT (5s)"),
    CRITICAL(15_000L, "CRITICAL (Event-Driven)")
}

/**
 * Section 4: Real-time thermal trend metrics.
 * Measures rate of change (velocity & acceleration), not just instantaneous temperature.
 */
data class ThermalTrend(
    val currentTemperature: Double?,
    val temperatureVelocity: Double, // °C / minute
    val temperatureAcceleration: Double, // °C / minute²
    val rollingAverageTemperature: Double?,
    val thermalHeadroom: Double?,
    val thermalHeadroomTrend: Double?, // Rate of change in headroom / min
    val cpuHeadroom: Double?,
    val gpuHeadroom: Double?,
    val batteryTemperature: Double?,
    val workloadDurationMs: Long,
    val recentInterventions: List<String> = emptyList(),
    val sampleCount: Int = 0
) {
    val isRisingFast: Boolean get() = temperatureVelocity >= 0.4
    val isCooling: Boolean get() = temperatureVelocity <= -0.2
}

/**
 * Section 5: Calibrated confidence levels based on real data span.
 */
enum class ThermalConfidence {
    LOW, MEDIUM, HIGH
}

/**
 * Prediction for a specific future horizon (10s, 30s, 60s).
 */
data class ThermalForecastPoint(
    val horizonSeconds: Int,
    val predictedTemperature: Double?,
    val confidence: ThermalConfidence,
    val thermalRisk: Risk,
    val modelVersion: String = "v1.0-online-trend"
)

/**
 * Section 5: Forecaster output containing validated 10s, 30s, and 60s predictions.
 */
data class ThermalForecastResult(
    val timestamp: Long,
    val points: List<ThermalForecastPoint>,
    val modelDescription: String = "Lightweight online regression & exponential smoothing"
) {
    fun getPoint(seconds: Int): ThermalForecastPoint? = points.find { it.horizonSeconds == seconds }
}

/**
 * Section 10: Graduated mitigation levels.
 */
enum class AutoCoolStrategy(val level: Int, val label: String) {
    LEVEL_0_NORMAL(0, "NORMAL"),
    LEVEL_1_PRE_COOL(1, "PRE-COOL"),
    LEVEL_2_COOL(2, "COOL"),
    LEVEL_3_AGGRESSIVE_COOL(3, "AGGRESSIVE COOL"),
    LEVEL_4_CRITICAL(4, "CRITICAL")
}

/**
 * Section 6: Auto-Cool Decision state.
 */
enum class AutoCoolState {
    NO_ACTION,
    WATCH,
    PRE_COOL,
    COOL,
    AGGRESSIVE_COOL,
    RECOVERY
}

/**
 * Section 7 & 8: Legitimate public Android cooling actions.
 */
enum class CoolingAction(val description: String, val isSovarixSelfMitigation: Boolean) {
    REDUCE_SOVARIX_TELEMETRY_RATE("Reduce SOVARIX telemetry sampling rate", true),
    REDUCE_MOTION_SAMPLING_RATE("Throttle motion sensors to UI rate", true),
    PAUSE_ROLLING_SCREEN_BUFFER("Pause in-memory rolling video capture buffer", true),
    REDUCE_MOMENT_DETECTION_COMPLEXITY("Limit highlight detection heuristics", true),
    PAUSE_LOCAL_AI("Pause background on-device intelligence processing", true),
    SWITCH_RESOURCE_GOVERNOR_ECO("Switch SOVARIX ResourceGovernor to ECO mode", true),
    ADJUST_UI_RENDERING_LOW_POWER("Throttle UI animations to minimal power", true),
    REQUEST_USER_DISPLAY_REFRESH_ADVICE("Advise user to adjust display refresh rate", false),
    REQUEST_USER_GAME_GRAPHICS_ADVICE("Advise user to lower in-game graphics settings", false),
    EMERGENCY_STOP_SESSION_ADVICE("Recommend pausing game and allowing device cooldown", false)
}

/**
 * Section 7: Discovery of legitimate controls supported on this Android device.
 */
data class CoolingActionCapabilities(
    val canReduceSovarixWorkload: Boolean = true,
    val canReduceCaptureRate: Boolean = true,
    val canStopNonEssentialSovarixProcessing: Boolean = true,
    val canAdjustOwnRendering: Boolean = true,
    val canAdjustOwnFrameRate: Boolean = true,
    val canUseSupportedGamePerformanceAPI: Boolean = false,
    val canRequestUserSystemSetting: Boolean = true,
    val canUseOfficialOEMSDK: Boolean = false
)

/**
 * Section 6: Decision output from the Auto-Cool Decision Engine.
 */
data class AutoCoolDecision(
    val strategy: AutoCoolStrategy,
    val state: AutoCoolState,
    val reason: String,
    val actions: List<CoolingAction>,
    val timestamp: Long,
    val consecutiveRecoverySamples: Int = 0
)

/**
 * Section 13: Predicted vs Actual Verification record.
 */
data class AutoCoolVerification(
    val id: Long,
    val timestamp: Long,
    val intervention: String,
    val strategy: AutoCoolStrategy,
    val predictedTempBefore: Double?,
    val actualTempBefore: Double?,
    val actualTempPeak: Double?,
    val actualTempAfter: Double?,
    val temperatureDelta: Double?,
    val predictionError: Double?,
    val coolingResponseTimeSec: Long,
    val effectiveness: String // "POSITIVE", "NEUTRAL", "NEGATIVE", "PENDING"
)

enum class AutoCoolAggressiveness {
    BALANCED,
    PERFORMANCE,
    COOLING_FIRST
}

enum class SovarixOverheadBudget {
    LOW,
    BALANCED,
    HIGH
}

/**
 * Section 16: User controls and preferences.
 */
data class AutoCoolSettings(
    val autoCoolEnabled: Boolean = true,
    val thermalProtectionEnabled: Boolean = true,
    val aggressiveness: AutoCoolAggressiveness = AutoCoolAggressiveness.BALANCED,
    val gamingAutoCool: Boolean = true,
    val notifyOnActivation: Boolean = true,
    val maxSovarixOverhead: SovarixOverheadBudget = SovarixOverheadBudget.LOW
)

/**
 * Section 14: Device-specific learned thermal DNA profile.
 */
data class ThermalDNAProfile(
    val calibrationObservations: Int = 0,
    val successfulInterventions: Int = 0,
    val averageCoolingRateCPerMin: Double = 0.5,
    val heatingRateByWorkload: Map<String, Double> = emptyMap(),
    val learnedRecoveryHoldSec: Long = 45L,
    val totalInterventions: Int = 0,
    val meanPredictionErrorC: Double? = null
)
