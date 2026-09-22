package dev.sovarix.app

import android.app.Application
import android.os.SystemClock
import dev.sovarix.app.data.SessionStore
import dev.sovarix.app.hardware.HardwareDetector
import dev.sovarix.core.*
import dev.sovarix.core.WhyEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SovarixApp : Application() {
    lateinit var repository: TwinRepository
        private set
    override fun onCreate() { super.onCreate(); repository = TwinRepository(this) }
}
class TwinRepository(private val app: Application) {
    val hardware = HardwareDetector(context = app).inspect()
    private val store = SessionStore(app)
    private val loaded = store.load()
    private var engine = TwinEngine(initialDNA = loaded.dna, initialEvents = loaded.events, initialVerifications = loaded.verifications)
    private val lock = Mutex()
    private val mutable = MutableStateFlow(engine.state.copy(error = loaded.warning))
    val state = mutable.asStateFlow()

    val blackBox: BlackBox get() = engine.blackBox

    private val _forecastResult = MutableStateFlow<ForecastResult?>(null)
    val forecastResult = _forecastResult.asStateFlow()

    private val _anomalyResult = MutableStateFlow<AnomalyResult?>(null)
    val anomalyResult = _anomalyResult.asStateFlow()

    private val _simulationResult = MutableStateFlow<SimulationResult?>(null)
    val simulationResult = _simulationResult.asStateFlow()

    private val _insights = MutableStateFlow<List<InsightResult>>(emptyList())
    val insights = _insights.asStateFlow()

    private val _blackBoxEvents = MutableStateFlow<List<BlackBoxEvent>>(emptyList())
    val blackBoxEvents = _blackBoxEvents.asStateFlow()

    private val _decision = MutableStateFlow<DecisionResult?>(null)
    val decision = _decision.asStateFlow()

    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    val incidents = _incidents.asStateFlow()

    private val _activeExperiment = MutableStateFlow<Experiment?>(null)
    val activeExperiment = _activeExperiment.asStateFlow()

    private val _experimentHistory = MutableStateFlow<List<Experiment>>(emptyList())
    val experimentHistory = _experimentHistory.asStateFlow()

    private var lastSave = 0L

    suspend fun start() = lock.withLock {
        engine.start(SystemClock.elapsedRealtime(), System.currentTimeMillis())
        mutable.value = engine.state
        _blackBoxEvents.value = engine.blackBox.getAll()
        checkpoint()
    }

    suspend fun accept(sample: Sample, motion: MotionSnapshot? = null): TwinState = lock.withLock {
        engine.accept(sample, motion)
        if (!engine.state.running || sample.elapsedMs - lastSave >= 60_000) checkpoint()
        mutable.value = engine.state
        _forecastResult.value = engine.latestForecast
        _anomalyResult.value = engine.latestAnomaly
        _simulationResult.value = engine.latestSimulation
        _insights.value = engine.latestInsights
        _blackBoxEvents.value = engine.blackBox.getAll()
        _decision.value = engine.latestDecision
        _incidents.value = engine.incidentEngine.getAllIncidents()
        _activeExperiment.value = engine.latestExperiment?.takeIf {
            it.status == ExperimentStatus.RUNNING_BASELINE || it.status == ExperimentStatus.RUNNING_TREATMENT
        }
        _experimentHistory.value = engine.experimentEngine.getAllExperiments()
        engine.state
    }

    suspend fun stop() = lock.withLock {
        engine.stop(System.currentTimeMillis())
        checkpoint()
        mutable.value = engine.state
        _blackBoxEvents.value = engine.blackBox.getAll()
    }

    suspend fun fail(message: String) = lock.withLock {
        engine.fail(message, System.currentTimeMillis())
        checkpoint()
        mutable.value = engine.state
        _blackBoxEvents.value = engine.blackBox.getAll()
    }

    suspend fun workload(value: Workload) = lock.withLock {
        engine.setWorkload(value, System.currentTimeMillis())
        mutable.value = engine.state
        _blackBoxEvents.value = engine.blackBox.getAll()
    }

    suspend fun economy(value: Boolean) = lock.withLock {
        engine.economy = value
        engine.event(System.currentTimeMillis(), "POLICY", if (value) "User selected economy observation" else "User selected adaptive observation")
        mutable.value = engine.state
    }

    val gamingManager = dev.sovarix.app.gaming.GamingSessionManager(app, this)

    suspend fun event(wall: Long, kind: String, message: String) = lock.withLock {
        engine.event(wall, kind, message)
        mutable.value = engine.state
    }

    suspend fun recordBlackBox(
        eventType: String,
        intervention: String? = null,
        gamingContext: GamingMomentResult? = null,
        predictedOutcome: Double? = null,
        actualOutcome: Double? = null,
        predictionError: Double? = null,
        notes: String = ""
    ) = lock.withLock {
        engine.blackBox.record(
            eventType = eventType,
            twinState = engine.state,
            forecast = engine.latestForecast,
            anomaly = engine.latestAnomaly,
            simulation = engine.latestSimulation,
            gamingContext = gamingContext,
            intervention = intervention,
            predictedOutcome = predictedOutcome,
            actualOutcome = actualOutcome,
            predictionError = predictionError,
            notes = notes
        )
        _blackBoxEvents.value = engine.blackBox.getAll()
    }

    suspend fun pin(minutes: Int): Boolean = lock.withLock {
        val ok = engine.pin(minutes, System.currentTimeMillis())
        mutable.value = engine.state
        _blackBoxEvents.value = engine.blackBox.getAll()
        ok
    }

    suspend fun intervention(action: String) = lock.withLock {
        engine.markIntervention(System.currentTimeMillis(), action)
        mutable.value = engine.state
        _blackBoxEvents.value = engine.blackBox.getAll()
    }

    suspend fun queryAI(prompt: String): String = lock.withLock {
        engine.queryAI(prompt)
    }

    suspend fun simulateLever(request: SimulationRequest): SimulationOutcome = lock.withLock {
        val outcome = engine.simulateLever(request)
        if (outcome is SimulationResult) {
            _simulationResult.value = outcome
        }
        outcome
    }

    suspend fun simulateQuery(query: String): SimulationOutcome = lock.withLock {
        val outcome = engine.simulateQuery(query)
        if (outcome is SimulationResult) {
            _simulationResult.value = outcome
        }
        outcome
    }

    // =========================================================
    // BLACK BOX VERIFICATION
    // =========================================================

    fun verifyBlackBoxChain(): Pair<Boolean, String> = engine.blackBox.verifyChain()

    suspend fun clearBlackBox() = lock.withLock {
        engine.blackBox.clear()
        _blackBoxEvents.value = emptyList()
    }

    // =========================================================
    // EXPERIMENT OPERATIONS
    // =========================================================

    private var experimentJob: Job? = null

    suspend fun startExperiment(experimentId: String): Experiment? = lock.withLock {
        experimentJob?.cancel()
        val exp = engine.experimentEngine.startExperiment(experimentId)
        _activeExperiment.value = exp
        if (exp != null) {
            engine.blackBox.record(
                eventType = "EXPERIMENT_STARTED",
                twinState = engine.state,
                notes = "Started experiment: ${exp.title}",
                evidenceLevel = EvidenceLevel.OBSERVATIONAL
            )
            _blackBoxEvents.value = engine.blackBox.getAll()

            // Dedicated active experiment runner: guarantees 1.5s sampling so tests finish in ~30s
            val tel = dev.sovarix.app.telemetry.AndroidTelemetry(app)
            tel.open()
            experimentJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    while (isActive) {
                        delay(1500L)
                        val sample = tel.sample(engine.state.workload)
                        val motion = gamingManager.motionManager.getLatest()
                        accept(sample, motion)
                        val current = engine.experimentEngine.getActiveExperiment()
                        if (current == null || current.status == ExperimentStatus.COMPLETED || current.status == ExperimentStatus.CANCELLED) {
                            _activeExperiment.value = null
                            _experimentHistory.value = engine.experimentEngine.getAllExperiments()
                            break
                        }
                    }
                } finally {
                    tel.close()
                }
            }
        }
        exp
    }

    suspend fun cancelExperiment(): Experiment? = lock.withLock {
        experimentJob?.cancel()
        experimentJob = null
        val exp = engine.experimentEngine.cancelExperiment()
        _activeExperiment.value = null
        _experimentHistory.value = engine.experimentEngine.getAllExperiments()
        if (exp != null) {
            engine.blackBox.record(
                eventType = "EXPERIMENT_CANCELLED",
                twinState = engine.state,
                notes = "Cancelled experiment: ${exp.title}",
                evidenceLevel = EvidenceLevel.UNKNOWN
            )
            _blackBoxEvents.value = engine.blackBox.getAll()
        }
        exp
    }

    fun getAvailableExperiments(): List<Experiment> = engine.experimentEngine.getAvailableExperiments()
    fun getExperimentHistory(): List<Experiment> = engine.experimentEngine.getAllExperiments()

    // =========================================================
    // INCIDENT OPERATIONS
    // =========================================================

    fun getLatestIncident(): Incident? = engine.latestIncident
    fun getAllIncidents(): List<Incident> = engine.incidentEngine.getAllIncidents()

    suspend fun recordIncident(incident: Incident) = lock.withLock {
        engine.incidentEngine.recordIncident(incident)
        _incidents.value = engine.incidentEngine.getAllIncidents()
        engine.blackBox.record(
            eventType = "INCIDENT",
            twinState = engine.state,
            notes = "Gaming Incident: ${incident.type} — ${incident.summary}",
            evidenceLevel = EvidenceLevel.OBSERVATIONAL
        )
        _blackBoxEvents.value = engine.blackBox.getAll()
    }

    // =========================================================
    // EXPORT
    // =========================================================

    suspend fun export(): String = lock.withLock {
        SessionStore.serialize(engine.state, hardware, engine.blackBox.getAll()).toString(2)
    }

    suspend fun exportIncidentReport(incident: Incident): String {
        val report = buildString {
            appendLine("SOVARIX DEVICE INCIDENT REPORT")
            appendLine("================================")
            appendLine()
            appendLine("Device: ${hardware.manufacturer} ${hardware.model}")
            appendLine("Android: ${hardware.androidVersion} (API ${hardware.sdk})")
            appendLine("SoC: ${hardware.soc ?: "UNAVAILABLE"}")
            appendLine()
            appendLine("Incident: ${incident.type}")
            appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(incident.timestamp))}")
            appendLine("Severity: ${incident.severity}")
            appendLine("Deviation: ${incident.deviationFromBaseline?.let { String.format(java.util.Locale.US, "%+.1f°C", it) } ?: "UNKNOWN"}")
            appendLine("Status: ${incident.status}")
            appendLine()
            appendLine("TIMELINE")
            appendLine("--------")
            for (event in incident.timelineEvents) {
                val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))
                appendLine("$t  ${event.label}${event.value?.let { " (${String.format(java.util.Locale.US, "%.1f", it)})" } ?: ""}")
            }
            appendLine()
            appendLine("HYPOTHESES")
            appendLine("----------")
            for (h in incident.hypotheses) {
                appendLine("${h.id}: ${h.description}")
                appendLine("  Supporting: ${h.supportingEvidence.joinToString("; ")}")
                appendLine("  Contradicting: ${h.contradictingEvidence.joinToString("; ").ifEmpty { "None" }}")
                appendLine("  Required experiment: ${h.requiredExperiment ?: "None"}")
                appendLine()
            }
        }
        return report
    }

    suspend fun clear(): Boolean = lock.withLock {
        if (engine.state.running) return@withLock false
        store.clear()
        engine = TwinEngine()
        mutable.value = engine.state
        _blackBoxEvents.value = emptyList()
        _incidents.value = emptyList()
        _activeExperiment.value = null
        _experimentHistory.value = emptyList()
        true
    }

    private suspend fun checkpoint() {
        try {
            val snapshot = engine.state
            withContext(Dispatchers.IO) { store.save(snapshot, engine.blackBox.getAll()) }
            lastSave = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            engine.event(System.currentTimeMillis(), "STORAGE", "History could not be saved: ${e.javaClass.simpleName}. Export before closing.")
        }
    }
}
