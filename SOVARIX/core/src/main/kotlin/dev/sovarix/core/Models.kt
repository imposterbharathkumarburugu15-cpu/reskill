package dev.sovarix.core

enum class ObservationMode { LOW_POWER, NORMAL, HIGH_ACTIVITY, THERMAL_PROTECTION, RECOVERY }
enum class Workload { UNSPECIFIED, IDLE, EVERYDAY, GAMING, RECOVERY }
enum class Risk { UNKNOWN, NORMAL, WATCH, ANOMALY }
enum class Availability { AVAILABLE, UNAVAILABLE, UNKNOWN }
enum class TwinPath { STANDARD, ADVANCED }

/**
 * Section 7: Calibrated device thermal states.
 */
enum class DeviceThermalState(val label: String) {
    NORMAL("NORMAL"),
    WARMING("WARMING"),
    HOT("HOT"),
    PRE_COOL("PRE-COOL"),
    COOLING("COOLING"),
    RECOVERY("RECOVERY"),
    CRITICAL("CRITICAL")
}

/**
 * Section 3: Concrete multi-horizon numerical prediction record.
 */
data class PredictionRecord(
    val metric: String, // "temperature", "battery", "thermal_state"
    val predictedValue: Double,
    val predictionTime: Long,
    val horizonSeconds: Int,
    val confidence: Double,
    val modelVersion: String = "v2.0-adaptive-timeseries",
    val riskTrend: Risk = Risk.NORMAL
)

/**
 * Section 4: Closed-loop Prediction Contract.
 * Whenever SOVARIX makes a meaningful prediction, it stores the contract,
 * waits for actual elapsed time, and evaluates: PREDICTED vs ACTUAL vs ERROR.
 */
data class PredictionContract(
    val id: Long,
    val metric: String = "temperature",
    val targetHorizonSeconds: Int,
    val createdAtElapsedMs: Long,
    val dueAtElapsedMs: Long,
    val predictedValue: Double,
    val baselineValue: Double?,
    val confidence: Double,
    val modelVersion: String = "v2.0-adaptive-timeseries",
    val actionContext: String = "No intervention",
    val workload: Workload = Workload.UNSPECIFIED,
    val charging: Boolean? = null,
    val actualValue: Double? = null,
    val signedError: Double? = null,
    val status: String = "PENDING", // PENDING, VERIFIED, MISSED, CANCELLED
    val resolvedAtWallMs: Long? = null
) {
    val errorAbsolute: Double? get() = signedError?.let { kotlin.math.abs(it) }
}

/**
 * Section 10: Causal Memory representation.
 * Stores behavioral relationships and multi-stage sequences discovered on THIS device.
 * e.g., GAMING + HIGH BRIGHTNESS -> HIGH WORKLOAD -> THERMAL RISE -> PERFORMANCE RISK -> AUTO-COOL -> TEMPERATURE RECOVERY
 */
data class CausalChain(
    val id: String,
    val timestamp: Long,
    val trigger: String,
    val stages: List<String>,
    val measuredDeltaC: Double,
    val recoveryTimeSec: Long,
    val frequency: Int = 1,
    val confidence: Double = 0.85,
    val learnedConclusion: String = ""
)

/**
 * Section 5: Structured simulation request generated from natural language intent.
 */
data class StructuredSimulationRequest(
    val intent: String = "SIMULATE",
    val durationMinutes: Int = 15,
    val workload: Workload = Workload.GAMING,
    val lever: ControllableLever = ControllableLever.WORKLOAD_INTENSITY,
    val targetValue: String = "50%",
    val rawPrompt: String = ""
)

data class CapabilityProfile(
    val manufacturer: String, val model: String, val androidVersion: String, val sdk: Int,
    val soc: String?, val abi: String, val logicalCores: Int,
    val gpu: String? = null, val npu: Availability = Availability.UNKNOWN,
    val secondaryChip: Availability = Availability.UNKNOWN,
    val secondaryChipName: String? = null, val vendorTelemetryVerified: Boolean = false,
    val evidence: String = "No verified public vendor interface is installed. Chip presence is unknown.",
    val gyroAvailable: Boolean = false,
    val accelerometerAvailable: Boolean = false,
    val linearAccelerationAvailable: Boolean = false,
    val rotationVectorAvailable: Boolean = false,
    val hapticHardwareAvailable: Boolean = false,
    val hapticObservationSupported: Boolean = false
)

object ArchitectureSelector {
    fun select(p: CapabilityProfile): TwinPath =
        if (p.secondaryChip == Availability.AVAILABLE && p.vendorTelemetryVerified) TwinPath.ADVANCED else TwinPath.STANDARD
}

data class Sample(
    val elapsedMs: Long, val wallMs: Long,
    val batteryPct: Double?, val batteryC: Double?, val charging: Boolean?,
    val currentUa: Int?, val thermalStatus: Int?, val headroom: Double?,
    val availableMemoryBytes: Long?, val totalMemoryBytes: Long?, val lowMemory: Boolean?,
    val appPssKb: Int?, val processCpuMs: Long, val powerSave: Boolean,
    val interactive: Boolean, val workload: Workload,
    val collectionMs: Double = 0.0
) {
    val memoryUsedFraction: Double? get() {
        val total = totalMemoryBytes?.takeIf { it > 0 } ?: return null
        val free = availableMemoryBytes?.takeIf { it in 0..total } ?: return null
        return 1.0 - free.toDouble() / total
    }
}

/** Engineering defaults, not scientifically validated device limits. */
data class GovernorConfig(
    val normalMs: Long = 10_000, val lowPowerMs: Long = 30_000,
    val highMs: Long = 5_000, val recoveryMs: Long = 20_000,
    val maxBurstMs: Long = 30_000, val recoveryHoldMs: Long = 60_000,
    val batteryFloor: Double = 15.0, val rapidRiseCPerMin: Double = 0.4,
    val collectorBudgetMs: Double = 75.0, val cpuBudgetOneCorePct: Double = 5.0,
    val memoryBudgetMb: Double = 180.0, val minimumHistoryMs: Long = 120_000,
    val maxGapMs: Long = 90_000, val historyCapacity: Int = 360,
    val sessionLimitMs: Long = 60 * 60 * 1_000L,
    val autoEnqueueVerification: Boolean = false
) { init { require(highMs >= 5_000 && normalMs >= highMs && lowPowerMs >= normalMs)
    require(recoveryMs >= highMs && minimumHistoryMs > 0 && historyCapacity >= 24) } }

data class ObservationPolicy(val mode: ObservationMode, val intervalMs: Long, val reason: String,
    val allowExplanation: Boolean, val stopSession: Boolean = false)
data class Trend(val slopePerMinute: Double, val latestEstimate: Double, val residualSd: Double,
    val sampleCount: Int, val spanMs: Long, val xMean: Double, val sxx: Double)
data class Projection(val value: Double, val heuristicBand: Double, val samples: Int,
    val historyMinutes: Double, val clamped: Boolean)
data class Forecast(val createdElapsedMs: Long, val horizonMinutes: Int,
    val temperature: Projection?, val battery: Projection?, val memory: Projection?,
    val evidence: String, val temperatureBiasApplied: Double = 0.0)
data class Anomaly(val risk: Risk, val evidence: List<String>)
data class Event(val id: Long, val wallMs: Long, val kind: String, val message: String)
data class PredictionTicket(val id: Long, val forecast: Forecast, val workload: Workload,
    val charging: Boolean?, val action: String, val baselineTemperature: Double?,
    val dueElapsedMs: Long = forecast.createdElapsedMs + forecast.horizonMinutes * 60_000L)
data class Verification(val id: Long, val wallMs: Long, val horizonMinutes: Int,
    val predictedC: Double?, val actualC: Double?, val signedErrorC: Double?,
    val predictedBattery: Double?, val actualBattery: Double?, val action: String,
    val status: String, val contextChanged: Boolean, val baselineTemperature: Double?,
    val biasApplied: Double = 0.0)
data class Baseline(val count: Int = 0, val meanC: Double = 0.0, val m2: Double = 0.0) {
    val sd: Double get() = if (count > 1) kotlin.math.sqrt(m2 / (count - 1)) else 0.0
    fun add(v: Double): Baseline {
        val delta = v - meanC; val n = count + 1; val mean = meanC + delta / n
        return Baseline(n, mean, m2 + delta * (v - mean))
    }
}
data class DeviceDNA(val sessions: Int = 0, val observations: Int = 0,
    val baselineByContext: Map<String, Baseline> = emptyMap(),
    val temperatureBiasByHorizon: Map<Int, Double> = emptyMap(),
    val verifiedCount: Int = 0, val totalAbsoluteErrorC: Double = 0.0,
    val causalRelationships: List<CausalRelationship> = emptyList(),
    val causalChains: List<CausalChain> = emptyList(),
    val experimentCount: Int = 0,
    val behavioralFingerprint: Map<String, Baseline> = emptyMap(),
    val thermalDNA: ThermalDNAProfile = ThermalDNAProfile(),
    val behaviorModel: DeviceBehaviorModel = DeviceBehaviorModel(),
    val repairBaseline: RepairBaselineComparison? = null
) {
    val meanAbsoluteErrorC get() = if (verifiedCount > 0) totalAbsoluteErrorC / verifiedCount else null
    val maturity: String get() = when {
        sessions < 3 -> "EARLY DEVICE BASELINE"
        sessions < 10 -> "DEVELOPING BASELINE"
        sessions < 30 -> "ESTABLISHED BASELINE"
        else -> "MATURE DEVICE MODEL"
    }
}
data class Overhead(
    val cpuOneCorePct: Double?,
    val pssMb: Double?,
    val processingMs: Double,
    val samples: Int,
    val elapsedMinutes: Double,
    val scheduledIntervalMs: Long,
    val baselineTemperatureC: Double? = null,
    val currentTemperatureC: Double? = null,
    val temperatureDeltaC: Double? = null,
    val batteryImpactEstimatePctPerHour: Double? = null,
    val sensorProcessingMs: Double = 0.0,
    val gamingCaptureState: String? = null,
    val gamingBufferMemoryMb: Double? = null,
    val gamingBufferFps: Int? = null,
    val gamingSensorRateHz: Int? = null,
    val inferenceCostMs: Double = 0.0,
    val wakeupsCount: Int = 0
)
data class TwinState(
    // 1. Observable physical device reality (Single Source of Truth)
    val timestamp: Long = 0L,
    val battery: Double? = null,
    val chargingState: Boolean? = null,
    val temperature: Double? = null,
    val thermalState: Int? = null,
    val thermalHeadroom: Double? = null,
    val cpuState: Double? = null,
    val memoryPressure: Boolean? = null,
    val storageState: Long? = null,
    val availableMemoryBytes: Long? = null,
    val totalMemoryBytes: Long? = null,
    val workload: Workload = Workload.UNSPECIFIED,
    val gamingState: Boolean = false,
    val gyroState: MotionSnapshot? = null,
    val accelerationState: MotionSnapshot? = null,
    val motionIntensity: Float = 0f,
    val performanceState: String = "NOMINAL",
    val anomalyIndicators: List<String> = emptyList(),
    val confidence: Double = 1.0,
    val productState: ProductState = ProductState.AWARE,
    val deviceGoal: DeviceGoal = DeviceGoal.KEEP_PHONE_COOL,
    val autopilotDecision: AutopilotDecision? = null,

    // Section 2: Truthful physical device fields & aliases
    val deviceTemperature: Double? = null,
    val cpuInformation: String? = null,
    val gpuInformation: String? = "UNAVAILABLE (No public GPU telemetry API)",
    val sensorAvailability: Map<String, Boolean> = emptyMap(),
    val samplingRate: Double = 0.1,
    val thermalVelocity: Double = 0.0,
    val thermalAcceleration: Double = 0.0,
    val batteryDrainRate: Double = 0.0,
    val anomalyState: String = "NOMINAL",
    val confidenceInfo: String = "Calibrated on physical device history",
    val currentThermalState: DeviceThermalState = DeviceThermalState.NORMAL,

    // Contracts & Causal Memory
    val predictionContracts: List<PredictionContract> = emptyList(),
    val causalChains: List<CausalChain> = emptyList(),

    // 2. Operational context & history snapshots
    val latest: Sample? = null,
    val history: List<Sample> = emptyList(),
    val policy: ObservationPolicy = ObservationPolicy(ObservationMode.LOW_POWER, 30_000, "Session stopped", false),
    val anomaly: Anomaly = Anomaly(Risk.UNKNOWN, listOf("Start a session to collect real evidence.")),
    val temperatureTrend: Trend? = null,
    val batteryTrend: Trend? = null,
    val forecasts: List<Forecast> = emptyList(),
    val pending: List<PredictionTicket> = emptyList(),
    val verifications: List<Verification> = emptyList(),
    val events: List<Event> = emptyList(),
    val dna: DeviceDNA = DeviceDNA(),
    val overhead: Overhead? = null,
    val thermalTrend: ThermalTrend? = null,
    val thermalForecast: ThermalForecastResult? = null,
    val autoCoolDecision: AutoCoolDecision? = null,
    val autoCoolVerification: AutoCoolVerification? = null,
    val autoCoolSettings: AutoCoolSettings = AutoCoolSettings(),
    val running: Boolean = false,
    val error: String? = null
) {
    // Backwards & Forward compatibility aliases per Section 2
    val batteryPercentage: Double? get() = battery
    val batteryTemperature: Double? get() = temperature
    val thermalStatus: Int? get() = thermalState
    val availableRAM: Long? get() = availableMemoryBytes
}

// =========================================================
// PARALLEL INTELLIGENCE RESULT MODELS
// =========================================================

/** Result produced independently by FutureEngine */
data class ForecastResult(
    val timestamp: Long = 0L,
    val horizonMinutes: Int = 2,
    val predictedTemperature: Double? = null,
    val predictedBattery: Double? = null,
    val predictedMemory: Double? = null,
    val confidence: Double = 0.0,
    val risk: Risk = Risk.UNKNOWN,
    val evidence: String = "",
    val forecasts: List<Forecast> = emptyList(),
    val temperatureBiasApplied: Double = 0.0,
    val forecast10s: Projection? = null,
    val forecast30s: Projection? = null,
    val forecast60s: Projection? = null,
    val forecast3m: Projection? = null,
    val forecast5m: Projection? = null,
    val forecast15m: Projection? = null,
    val recoveryTrajectory: Projection? = null,
    val predictedThermalState: DeviceThermalState = DeviceThermalState.NORMAL,
    val performanceRiskTrend: Risk = Risk.NORMAL,
    val predictionRecords: List<PredictionRecord> = emptyList()
)

/**
 * Result produced independently by AnomalyEngine.
 * Adheres strictly to Directive Section 6: type, severity, observedValue, baselineValue, deviation, timestamp.
 */
data class AnomalyResult(
    val type: String = "NOMINAL",
    val severity: Risk = Risk.UNKNOWN,
    val observedValue: Double? = null,
    val baselineValue: Double? = null,
    val deviation: Double? = null,
    val timestamp: Long = 0L,
    val indicators: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val score: Double = 0.0,
    val contextKey: String = ""
) {
    constructor(
        timestamp: Long,
        risk: Risk,
        score: Double = 0.0,
        indicators: List<String> = emptyList(),
        evidence: List<String> = emptyList(),
        baselineDeviation: Double? = null,
        contextKey: String = "",
        type: String = "NOMINAL"
    ) : this(
        type = type,
        severity = risk,
        observedValue = null,
        baselineValue = null,
        deviation = baselineDeviation,
        timestamp = timestamp,
        indicators = indicators,
        evidence = evidence,
        score = score,
        contextKey = contextKey
    )

    // Backward-compatible accessors
    val risk: Risk get() = severity
    val baselineDeviation: Double? get() = deviation
}

// =========================================================
// CONCRETE WHAT-IF SIMULATION MODELS (SECTION 7)
// =========================================================

sealed interface SimulationOutcome {
    val timestamp: Long
    val isSupported: Boolean
}

data class SimulationUnsupported(
    val variable: String,
    val reason: String,
    override val timestamp: Long = 0L,
    override val isSupported: Boolean = false
) : SimulationOutcome

typealias SimulationUnavailable = SimulationUnsupported

enum class ControllableLever(val label: String, val unit: String, val supportedValues: List<String>) {
    FPS_CAP("FPS Cap", "FPS", listOf("60", "90", "120")),
    WORKLOAD_INTENSITY("Workload Intensity", "%", listOf("25%", "50%", "75%", "100%")),
    RESOLUTION_SCALE("Resolution Scale", "Scale", listOf("720p", "1080p")),
    APP_DIMMING("App Window Dimming", "%", listOf("20%", "50%", "100%")),
    UNSUPPORTED("Unmodeled System Variable", "", emptyList())
}

data class SimulationRequest(
    val lever: ControllableLever,
    val targetValue: String,
    val horizonMinutes: Int = 2
)

/** Counterfactual scenario evaluated on a copy of TwinState */
data class SimulationScenario(
    val id: String,
    val name: String,
    val assumedTrendMultiplier: Double,
    val predictedTemperatureC: Double?,
    val predictedBatteryPct: Double?,
    val thermalRisk: Risk,
    val performanceRisk: Risk,
    val explanation: String,
    val actionClassification: String = "MODEL ONLY" // MODEL ONLY, EXECUTABLE, USER_CONFIRMATION_REQUIRED, UNSUPPORTED
)

/** Result produced independently by SimulationEngine */
data class SimulationResult(
    override val timestamp: Long = 0L,
    val baseForecast: Forecast? = null,
    val scenarios: List<SimulationScenario> = emptyList(),
    val recommendedScenario: SimulationScenario? = null,
    val activeLever: ControllableLever? = null,
    val requestedValue: String? = null,
    val unsupportedRequest: SimulationUnsupported? = null,
    override val isSupported: Boolean = true,
    val explanation: String = "SIMULATION — COUNTERFACTUAL ANALYSIS ON A CLONED COPY OF TWIN STATE. REAL DEVICE IS UNCHANGED."
) : SimulationOutcome

/** Result produced independently by Gaming Intelligence */
data class GamingMomentResult(
    val timestamp: Long = 0L,
    val moment: SpecialMoment? = null,
    val detected: Boolean = false,
    val momentType: String? = null,
    val confidence: Double = 0.0,
    val motionSnapshot: MotionSnapshot? = null,
    val signalEvidence: SignalEvidence? = null,
    val availableSignals: List<String> = emptyList()
)

// =========================================================
// CORRELATION & INSIGHT LAYER
// =========================================================

enum class InsightCategory { THERMAL, PERFORMANCE, GAMING, BATTERY, SYSTEM }

/** Correlated synthesis produced by InsightEngine */
data class InsightResult(
    val id: String,
    val timestamp: Long,
    val category: InsightCategory,
    val title: String,
    val summary: String,
    val supportingEvidence: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val severity: Risk = Risk.NORMAL,
    val recommendedAction: TwinAction? = null,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.OBSERVATIONAL
) {
    // Backward-compatible accessors
    val evidenceSources: List<String> get() = supportingEvidence
    val riskLevel: Risk get() = severity
    val actionSuggestion: String? get() = recommendedAction?.name
}

// =========================================================
// EVIDENCE CLASSIFICATION (SECTION 46)
// =========================================================

enum class EvidenceLevel {
    UNKNOWN,
    OBSERVATIONAL,
    CORRELATED,
    INTERVENTION_SUPPORTED,
    STRONGER_EXPERIMENTAL,
    SIMULATION_ONLY
}

// =========================================================
// INCIDENT & FORENSICS LAYER (SECTIONS 8-9)
// =========================================================

enum class IncidentStatus { INVESTIGATING, EXPLAINED, UNRESOLVED, ARCHIVED }

data class IncidentTimelineEvent(
    val timestamp: Long,
    val label: String,
    val metric: String? = null,
    val value: Double? = null,
    val baselineValue: Double? = null,
    val deviation: Double? = null
)

data class Incident(
    val id: Long,
    val timestamp: Long,
    val type: String,
    val severity: Risk,
    val timelineEvents: List<IncidentTimelineEvent> = emptyList(),
    val hypotheses: List<Hypothesis> = emptyList(),
    val whyReport: WhyReport? = null,
    val status: IncidentStatus = IncidentStatus.INVESTIGATING,
    val evidenceCount: Int = 0,
    val deviationFromBaseline: Double? = null,
    val resolvedAt: Long? = null,
    val summary: String = ""
)

// =========================================================
// WHY ENGINE LAYER (SECTION 21)
// =========================================================

data class PossibleContributor(
    val title: String,
    val evidenceRating: String, // "strong", "moderate", "weak", "present"
    val explanation: String
)

data class WhyReport(
    val title: String = "WHY DID PERFORMANCE CHANGE?",
    val observedFacts: List<String> = emptyList(),
    val possibleContributors: List<PossibleContributor> = emptyList(),
    val supportingEvidence: List<String> = emptyList(),
    val contradictingEvidence: List<String> = emptyList(),
    val unknownFactors: List<String> = emptyList(),
    val recommendedTest: String? = null
)

// =========================================================
// GAMING PERFORMANCE ANOMALY (SECTION 19)
// =========================================================

data class GamingPerformanceAnomaly(
    val eventType: String, // "PERFORMANCE_EVENT", "THERMAL_EVENT", "HEADROOM_DROP", "RECOVERY_EVENT", "BATTERY_DRAIN_EVENT"
    val timestamp: Long,
    val summary: String,
    val severity: Risk,
    val measuredValue: Double? = null,
    val baselineValue: Double? = null,
    val evidence: List<String> = emptyList()
)

// =========================================================
// HYPOTHESIS LAYER (SECTION 10)
// =========================================================

enum class HypothesisStatus { PROPOSED, TESTING, SUPPORTED, CONTRADICTED, INCONCLUSIVE }

data class Hypothesis(
    val id: String,
    val incidentId: Long,
    val description: String,
    val supportingEvidence: List<String> = emptyList(),
    val contradictingEvidence: List<String> = emptyList(),
    val requiredExperiment: String? = null,
    val risk: Risk = Risk.UNKNOWN,
    val cost: String = "LOW",
    val status: HypothesisStatus = HypothesisStatus.PROPOSED
)

// =========================================================
// EXPERIMENT LAYER (SECTIONS 11, 12, 14, 45)
// =========================================================

enum class ExperimentStatus {
    PLANNED, AWAITING_CONSENT, RUNNING_BASELINE, RUNNING_TREATMENT, COMPLETED, CANCELLED, UNSUPPORTED, FAILED
}

data class ExperimentObservation(
    val timestamp: Long,
    val phase: String, // "BASELINE" or "TREATMENT"
    val temperature: Double? = null,
    val cpuPct: Double? = null,
    val memoryPssMb: Double? = null,
    val batteryPct: Double? = null,
    val thermalStatus: Int? = null,
    val custom: Map<String, Double> = emptyMap()
)

data class ExperimentResult(
    val experimentId: String,
    val controlMeanTemp: Double? = null,
    val treatmentMeanTemp: Double? = null,
    val controlMeanCpu: Double? = null,
    val treatmentMeanCpu: Double? = null,
    val controlMeanMemory: Double? = null,
    val treatmentMeanMemory: Double? = null,
    val temperatureEffectSize: Double? = null,
    val cpuEffectSize: Double? = null,
    val memoryEffectSize: Double? = null,
    val controlSamples: Int = 0,
    val treatmentSamples: Int = 0,
    val conclusion: String = "",
    val evidenceLevel: EvidenceLevel = EvidenceLevel.UNKNOWN,
    val uncertainty: String = ""
)

data class Experiment(
    val id: String,
    val title: String,
    val question: String,
    val hypothesisId: String? = null,
    val independentVariable: String,
    val controlCondition: String,
    val treatmentCondition: String,
    val baselineDurationMs: Long = 60_000,
    val treatmentDurationMs: Long = 60_000,
    val measurements: List<String> = listOf("temperature", "cpu", "memory", "battery"),
    val safetyConstraints: List<String> = listOf("Abort on thermal status >= 3", "Abort on battery < 10%"),
    val consentRequired: Boolean = true,
    val status: ExperimentStatus = ExperimentStatus.PLANNED,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val baselineObservations: List<ExperimentObservation> = emptyList(),
    val treatmentObservations: List<ExperimentObservation> = emptyList(),
    val result: ExperimentResult? = null,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.UNKNOWN
)

// =========================================================
// CAUSAL RELATIONSHIP (SECTION 15)
// =========================================================

data class CausalRelationship(
    val cause: String,
    val effect: String,
    val observationalSessions: Int = 0,
    val controlledExperiments: Int = 0,
    val evidenceStrength: EvidenceLevel = EvidenceLevel.UNKNOWN,
    val effectEstimate: Double? = null,
    val uncertainty: String = "",
    val lastUpdated: Long = 0L
)

// =========================================================
// HISTORICAL EVIDENCE LAYER (BLACK BOX)
// =========================================================

/** Immutable operational snapshot preserved in Black Box with tamper-evident SHA-256 hash chaining */
data class BlackBoxEvent(
    val id: Long,
    val timestamp: Long,
    val eventType: String,
    val twinStateSnapshot: TwinState,
    val forecast: ForecastResult? = null,
    val anomaly: AnomalyResult? = null,
    val simulation: SimulationResult? = null,
    val gamingContext: GamingMomentResult? = null,
    val intervention: String? = null,
    val predictedOutcome: Double? = null,
    val actualOutcome: Double? = null,
    val predictionError: Double? = null,
    val notes: String = "",
    val previousHash: String = "GENESIS",
    val currentHash: String = "",
    val source: String = "TWIN_ENGINE",
    val evidenceLevel: EvidenceLevel = EvidenceLevel.OBSERVATIONAL
)

// =========================================================
// DECISION & VERIFICATION RESULTS
// =========================================================

data class DecisionResult(
    val timestamp: Long = 0L,
    val recommendation: String = "",
    val recommendedAction: TwinAction? = null,
    val permission: ActionPermission = ActionPermission.ALLOWED_AUTOMATICALLY,
    val rationale: String = ""
)

data class VerificationResult(
    val ticketId: Long,
    val timestamp: Long,
    val horizonMinutes: Int,
    val predictedC: Double?,
    val actualC: Double?,
    val signedErrorC: Double?,
    val predictedBattery: Double?,
    val actualBattery: Double?,
    val action: String,
    val status: String,
    val contextChanged: Boolean,
    val baselineTemperature: Double? = null,
    val biasApplied: Double = 0.0,
    val confidence: Double = 1.0
)
