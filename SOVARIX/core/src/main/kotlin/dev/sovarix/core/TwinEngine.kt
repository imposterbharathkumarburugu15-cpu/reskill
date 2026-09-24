package dev.sovarix.core

import kotlinx.coroutines.*

/** Single-writer engine. Orchestrates parallel intelligence modules consuming shared TwinState. */
class TwinEngine(
    val config: GovernorConfig = GovernorConfig(),
    initialDNA: DeviceDNA = DeviceDNA(),
    initialEvents: List<Event> = emptyList(),
    initialVerifications: List<Verification> = emptyList(),
    val blackBox: BlackBox = BlackBox()
) {
    private val controller = ObservationController(config)
    private val forecastEngine = ForecastEngine(config)
    private val futureEngine = FutureEngine(config)
    private val anomalyEngine = AnomalyEngine
    private val simulationEngine = SimulationEngine
    private val gamingIntelligence = GamingIntelligence()
    private val insightEngine = InsightEngine
    private val decisionEngine = DecisionEngine
    private val verificationEngine = VerificationEngine(config)
    val incidentEngine = IncidentEngine()
    private val hypothesisEngine = HypothesisEngine
    val experimentEngine = ExperimentEngine()

    private val history = ArrayDeque<Sample>()
    private val summaries = mutableListOf<Sample>()
    private val changedTickets = mutableSetOf<Long>()
    private var startedAt = 0L
    private var count = 0
    private var lastSummaryAt = Long.MIN_VALUE
    private var sessionStartTemperatureC: Double? = null
    private var eventId = initialEvents.maxOfOrNull { it.id } ?: 0L
    private var ticketId = initialVerifications.maxOfOrNull { it.id } ?: 0L
    private var contractId = 0L

    var state = TwinState(
        dna = initialDNA,
        events = initialEvents.takeLast(200),
        verifications = initialVerifications.takeLast(60)
    )
        private set

    var latestForecast: ForecastResult? = null
        private set
    var latestAnomaly: AnomalyResult? = null
        private set
    var latestSimulation: SimulationResult? = null
        private set
    var latestGaming: GamingMomentResult? = null
        private set
    var latestInsights: List<InsightResult> = emptyList()
        private set
    var latestDecision: DecisionResult? = null
        private set
    var latestIncident: Incident? = null
        private set
    var latestExperiment: Experiment? = null
        private set

    var economy = false
    var workload = Workload.UNSPECIFIED

    fun start(elapsed: Long, wall: Long) {
        if (state.running) return
        history.clear(); summaries.clear(); changedTickets.clear(); controller.reset(); count = 0
        lastSummaryAt = Long.MIN_VALUE; startedAt = elapsed; sessionStartTemperatureC = null
        state = state.copy(
            running = true,
            latest = null,
            history = emptyList(),
            forecasts = emptyList(),
            pending = emptyList(),
            error = null,
            temperatureTrend = null,
            batteryTrend = null,
            overhead = null,
            anomaly = Anomaly(Risk.UNKNOWN, listOf("Collecting first measurement.")),
            workload = workload,
            gamingState = (workload == Workload.GAMING)
        )
        event(wall, "SESSION", "Observation started. Workload labels are user-declared.")
        blackBox.record(
            eventType = "SESSION_START",
            twinState = state,
            notes = "Observation session started"
        )
    }

    fun setWorkload(value: Workload, wall: Long) {
        if (workload != value) {
            workload = value; changedTickets.addAll(state.pending.map { it.id })
            history.clear()
            state = state.copy(
                workload = value,
                gamingState = (value == Workload.GAMING),
                history = emptyList(),
                forecasts = emptyList(),
                temperatureTrend = null,
                batteryTrend = null
            )
            event(wall, "CONTEXT", "User selected ${value.name.lowercase()}. New forecasts need a fresh stable window.")
            blackBox.record(
                eventType = "WORKLOAD_CHANGE",
                twinState = state,
                intervention = "Set workload to ${value.name}",
                notes = "Workload changed by user"
            )
        }
    }

    fun event(wall: Long, kind: String, message: String) {
        state = state.copy(events = (state.events + Event(++eventId, wall, kind, message)).takeLast(200))
    }

    /**
     * Accepts a new device telemetry sample and optional physical motion snapshot.
     * 1. Constructs immutable TwinState representing observable device reality.
     * 2. Executes parallel intelligence modules (FutureEngine, AnomalyEngine, SimulationEngine, GamingIntelligence).
     * 3. Correlates multi-domain signals via InsightEngine.
     * 4. Evaluates decisions via DecisionEngine & ActionPolicy.
     * 5. Resolves predictions via VerificationEngine.
     * 6. Calibrates DeviceDNA via LearningEngine.
     * 7. Preserves full historical evidence in BlackBox.
     */
    fun accept(sample: Sample, motion: MotionSnapshot? = null): TwinState {
        if (!state.running) return state
        val previous = history.lastOrNull()
        if (previous != null && sample.elapsedMs <= previous.elapsedMs) return state
        val s = sample.copy(workload = workload)
        history.addLast(s); while (history.size > config.historyCapacity) history.removeFirst()
        count++
        if (lastSummaryAt == Long.MIN_VALUE || s.elapsedMs - lastSummaryAt >= 60_000) {
            summaries += s; lastSummaryAt = s.elapsedMs
        }
        val list = history.toList()
        val thermal = Statistics.fit(list, config) { it.batteryC }
        val battery = Statistics.fit(list, config) { it.batteryPct }
        val cpu = previous?.let { prev ->
            val elapsed = s.elapsedMs - prev.elapsedMs
            (100.0 * (s.processCpuMs - prev.processCpuMs) / elapsed).takeIf { it >= 0 && it.isFinite() }
        }
        if (sessionStartTemperatureC == null && s.batteryC != null) {
            sessionStartTemperatureC = s.batteryC
        }
        val tempDelta = if (sessionStartTemperatureC != null && s.batteryC != null) {
            s.batteryC - sessionStartTemperatureC!!
        } else null

        val overhead = Overhead(
            cpuOneCorePct = cpu,
            pssMb = s.appPssKb?.div(1024.0),
            processingMs = s.collectionMs,
            samples = count,
            elapsedMinutes = (s.elapsedMs - startedAt) / 60_000.0,
            scheduledIntervalMs = state.policy.intervalMs,
            baselineTemperatureC = sessionStartTemperatureC,
            currentTemperatureC = s.batteryC,
            temperatureDeltaC = tempDelta,
            sensorProcessingMs = s.collectionMs
        )
        val policy = controller.choose(s, thermal?.slopePerMinute, overhead, economy)

        val thermalVel = thermal?.slopePerMinute ?: 0.0
        val thermalAcc = if (list.size >= 6) {
            val half = list.size / 2
            val oldHalf = list.subList(0, half)
            val newHalf = list.subList(half, list.size)
            val oldSlope = Statistics.fit(oldHalf, config) { it.batteryC }?.slopePerMinute ?: 0.0
            val newSlope = Statistics.fit(newHalf, config) { it.batteryC }?.slopePerMinute ?: 0.0
            newSlope - oldSlope
        } else 0.0
        val battDrain = -(battery?.slopePerMinute ?: 0.0).coerceAtLeast(0.0)

        val devThermalState = when {
            (s.thermalStatus ?: 0) >= 4 || (s.batteryC != null && s.batteryC >= 45.0) -> DeviceThermalState.CRITICAL
            (s.thermalStatus ?: 0) >= 3 || (s.batteryC != null && s.batteryC >= 42.0) -> DeviceThermalState.HOT
            policy.mode == ObservationMode.THERMAL_PROTECTION -> DeviceThermalState.COOLING
            s.workload == Workload.RECOVERY -> DeviceThermalState.RECOVERY
            (s.batteryC != null && s.batteryC >= 39.5) -> DeviceThermalState.WARMING
            else -> DeviceThermalState.NORMAL
        }

        // 1. Single Source of Device Reality: Immutable TwinState snapshot
        val baseTwinState = TwinState(
            timestamp = s.wallMs,
            battery = s.batteryPct,
            chargingState = s.charging,
            temperature = s.batteryC,
            thermalState = s.thermalStatus,
            thermalHeadroom = s.headroom,
            cpuState = cpu,
            memoryPressure = s.lowMemory,
            availableMemoryBytes = s.availableMemoryBytes,
            totalMemoryBytes = s.totalMemoryBytes,
            workload = workload,
            gamingState = (workload == Workload.GAMING),
            gyroState = motion,
            accelerationState = motion,
            motionIntensity = motion?.movementIntensity ?: 0f,
            performanceState = if ((s.thermalStatus ?: 0) >= 2) "THROTTLING_RISK" else "STABLE",
            confidence = 1.0,
            samplingRate = 1000.0 / policy.intervalMs.coerceAtLeast(1000),
            sensorAvailability = mapOf(
                "battery" to (s.batteryPct != null),
                "thermal" to (s.thermalStatus != null),
                "headroom" to (s.headroom != null),
                "motion" to (motion != null)
            ),
            thermalVelocity = thermalVel,
            thermalAcceleration = thermalAcc,
            batteryDrainRate = battDrain,
            anomalyState = latestAnomaly?.type ?: "NOMINAL",
            confidenceInfo = "Calibrated on physical device history (${state.dna.sessions} sessions)",
            currentThermalState = devThermalState,
            predictionContracts = state.predictionContracts,
            causalChains = state.causalChains,
            latest = s,
            history = list,
            policy = policy,
            temperatureTrend = thermal,
            batteryTrend = battery,
            dna = state.dna,
            overhead = overhead.copy(scheduledIntervalMs = policy.intervalMs),
            running = state.running,
            pending = state.pending,
            verifications = state.verifications,
            events = state.events,
            error = state.error
        )

        // 2. PARALLEL INTELLIGENCE MODULES
        // Independently consume baseTwinState; no sequential dependency.
        val forecastRes = futureEngine.evaluate(baseTwinState)
        val anomalyRes = anomalyEngine.evaluate(baseTwinState)
        val simulationRes = simulationEngine.evaluate(baseTwinState)
        val gamingRes = gamingIntelligence.evaluateIntelligence(baseTwinState)

        latestForecast = forecastRes
        latestAnomaly = anomalyRes
        latestSimulation = simulationRes
        latestGaming = gamingRes

        // 3. INSIGHT / EVENT CORRELATION LAYER
        val insights = insightEngine.correlate(baseTwinState, forecastRes, anomalyRes, simulationRes, gamingRes)
        latestInsights = insights

        // 4. DECISION ENGINE LAYER
        val decision = decisionEngine.decide(baseTwinState, forecastRes, anomalyRes, simulationRes, insights)
        latestDecision = decision

        val autopilotDecision = AutopilotDecisionEngine.evaluate(baseTwinState, state.deviceGoal, state.dna.behaviorModel)

        // 5. PREDICTION CONTRACT & VERIFICATION LOOP (Section 4)
        var contracts = state.predictionContracts.toMutableList()
        val pred60 = forecastRes.forecast60s?.value ?: forecastRes.forecasts.firstOrNull { it.horizonMinutes == 1 }?.temperature?.value
        if (config.autoEnqueueVerification && pred60 != null && contracts.none { it.targetHorizonSeconds == 60 && it.status == "PENDING" } && s.batteryC != null) {
            contracts.add(
                PredictionContract(
                    id = ++contractId,
                    metric = "temperature",
                    targetHorizonSeconds = 60,
                    createdAtElapsedMs = s.elapsedMs,
                    dueAtElapsedMs = s.elapsedMs + 60_000L,
                    predictedValue = pred60,
                    baselineValue = s.batteryC,
                    confidence = forecastRes.confidence,
                    modelVersion = "v2.0-statistical-timeseries",
                    actionContext = "Nominal monitoring",
                    workload = s.workload,
                    charging = s.charging
                )
            )
        }

        // Auto-enqueue a 2-minute prediction ticket if none is currently pending and a valid projection exists
        var pendingTickets = state.pending
        val twoMinForecast = forecastRes.forecasts.firstOrNull { it.horizonMinutes == 2 }
        if (config.autoEnqueueVerification && twoMinForecast?.temperature != null && pendingTickets.none { it.forecast.horizonMinutes == 2 } && pendingTickets.size < 3) {
            val autoTicket = PredictionTicket(
                id = ++ticketId,
                forecast = twoMinForecast,
                workload = s.workload,
                charging = s.charging,
                action = "Autonomous 2-min validation",
                baselineTemperature = s.batteryC,
                dueElapsedMs = s.elapsedMs + 2 * 60_000L
            )
            pendingTickets = pendingTickets + autoTicket
        }

        pendingTickets.forEach { if (it.workload != s.workload || it.charging != s.charging) changedTickets += it.id }
        val resolved = pendingTickets.mapNotNull { verificationEngine.resolve(it, s, it.id in changedTickets) }
        val resolvedResults = pendingTickets.mapNotNull { verificationEngine.resolveResult(it, s, it.id in changedTickets) }

        // Resolve PredictionContracts against real physical device sample
        val updatedContracts = contracts.map { contract ->
            if (contract.status == "PENDING") {
                val contextChanged = contract.workload != s.workload || contract.charging != s.charging
                verificationEngine.resolveContract(contract, s, contextChanged) ?: contract
            } else contract
        }

        // 6. LEARNING ENGINE (Section 4, 11)
        var dna = state.dna
        resolvedResults.forEach {
            dna = LearningEngine.verified(dna, it)
            changedTickets.remove(it.ticketId)
        }
        updatedContracts.filter { it.status == "VERIFIED" && !state.predictionContracts.any { old -> old.id == it.id && old.status == "VERIFIED" } }.forEach { verifiedContract ->
            dna = LearningEngine.verified(dna, verifiedContract)
            blackBox.record(
                eventType = "PREDICTION_VERIFIED",
                twinState = baseTwinState,
                predictedOutcome = verifiedContract.predictedValue,
                actualOutcome = verifiedContract.actualValue,
                predictionError = verifiedContract.signedError,
                notes = "Horizon ${verifiedContract.targetHorizonSeconds}s: Predicted ${String.format(java.util.Locale.US, "%.1f°C", verifiedContract.predictedValue)} vs Actual ${String.format(java.util.Locale.US, "%.1f°C", verifiedContract.actualValue ?: 0.0)} (Error ${String.format(java.util.Locale.US, "%+.1f°C", verifiedContract.signedError ?: 0.0)})"
            )
            event(s.wallMs, "VERIFIED", "Horizon ${verifiedContract.targetHorizonSeconds}s verified. Error: ${String.format(java.util.Locale.US, "%+.1f°C", verifiedContract.signedError ?: 0.0)}")
        }

        // 7. INCIDENT ENGINE — detect and reconstruct device anomalies
        val incident = incidentEngine.evaluate(anomalyRes, baseTwinState, blackBox.getAll())
        if (incident != null) {
            val hypotheses = hypothesisEngine.generateHypotheses(incident, baseTwinState, overhead)
            latestIncident = incident.copy(hypotheses = hypotheses)
            blackBox.record(
                eventType = "INCIDENT",
                twinState = baseTwinState,
                anomaly = anomalyRes,
                notes = "Incident #${incident.id}: ${incident.type} — ${incident.summary}",
                evidenceLevel = EvidenceLevel.OBSERVATIONAL
            )
        }

        // 8. CAUSAL MEMORY ENGINE (Section 10)
        val discoveredChains = CausalMemoryEngine.discover(baseTwinState, incident, decision)
        val updatedCausalChains = (state.causalChains + discoveredChains).distinctBy { it.id }.takeLast(20)
        if (discoveredChains.isNotEmpty()) {
            discoveredChains.forEach { chain ->
                blackBox.record(
                    eventType = "CAUSAL_DISCOVERY",
                    twinState = baseTwinState,
                    notes = "${chain.trigger} -> ${chain.stages.joinToString(" -> ")}"
                )
            }
        }

        // 9. EXPERIMENT ENGINE — feed observations during active experiments
        val activeExp = experimentEngine.getActiveExperiment()
        if (activeExp != null) {
            val updatedExp = experimentEngine.observe(s, overhead)
            latestExperiment = updatedExp
            if (updatedExp?.status == ExperimentStatus.COMPLETED) {
                // Update DeviceDNA with experiment results
                val causal = experimentEngine.toCausalRelationships()
                dna = dna.copy(
                    causalRelationships = causal,
                    experimentCount = dna.experimentCount + 1
                )
                blackBox.record(
                    eventType = "EXPERIMENT_COMPLETED",
                    twinState = baseTwinState,
                    notes = "Experiment ${updatedExp.id} completed: ${updatedExp.result?.conclusion ?: "No result"}",
                    evidenceLevel = updatedExp.result?.evidenceLevel ?: EvidenceLevel.UNKNOWN
                )
            }
        }

        // 10. HISTORICAL EVIDENCE LAYER (BLACK BOX)
        // Record cycle summary & verification outcomes
        val primaryVerification = resolvedResults.firstOrNull()
        blackBox.record(
            eventType = if (primaryVerification != null) "VERIFICATION" else "OBSERVATION",
            twinState = baseTwinState,
            forecast = forecastRes,
            anomaly = anomalyRes,
            simulation = simulationRes,
            gamingContext = if (gamingRes.detected) gamingRes else null,
            predictedOutcome = primaryVerification?.predictedC ?: forecastRes.predictedTemperature,
            actualOutcome = primaryVerification?.actualC ?: s.batteryC,
            predictionError = primaryVerification?.signedErrorC,
            notes = if (primaryVerification != null) "Prediction verified: ${primaryVerification.status}" else "Cycle observation"
        )

        // Emit notifications / events into state ledger
        if (policy.mode != state.policy.mode) event(s.wallMs, "GOVERNOR", "${policy.mode}: ${policy.reason}")
        if (anomalyRes.risk != state.anomaly.risk) event(s.wallMs, "STATE", "${anomalyRes.risk}: ${anomalyRes.evidence.firstOrNull() ?: ""}")
        if (previous?.charging != null && previous.charging != s.charging) event(s.wallMs, "CHARGING", "Charging state changed. Forecast context reset.")
        resolved.forEach { event(s.wallMs, "PROOF", "${it.horizonMinutes} minute forecast: ${it.status}") }
        if (summaries.lastOrNull()?.elapsedMs == s.elapsedMs) event(s.wallMs, "SUMMARY", "Battery ${s.batteryPct ?: "unavailable"}%; battery temp ${s.batteryC ?: "unavailable"}°C; thermal ${s.thermalStatus ?: "unknown"}.")

        // Update active TwinState snapshot
        state = baseTwinState.copy(
            productState = autopilotDecision.productState,
            autopilotDecision = autopilotDecision,
            anomaly = Anomaly(anomalyRes.risk, anomalyRes.evidence),
            anomalyIndicators = anomalyRes.indicators,
            forecasts = forecastRes.forecasts,
            pending = pendingTickets.filterNot { ticket -> resolved.any { it.id == ticket.id } },
            verifications = (state.verifications + resolved).takeLast(60),
            predictionContracts = updatedContracts.takeLast(30),
            causalChains = updatedCausalChains,
            dna = dna.copy(causalChains = updatedCausalChains),
            events = state.events
        )

        if (policy.stopSession || s.elapsedMs - startedAt >= config.sessionLimitMs) {
            stop(s.wallMs, if (policy.stopSession) "Critical thermal status" else "One-hour session limit reached")
        }
        return state
    }

    fun setDeviceGoal(goal: DeviceGoal) {
        state = state.copy(deviceGoal = goal)
    }

    fun setRepairBaseline(comparison: RepairBaselineComparison) {
        state = state.copy(dna = state.dna.copy(repairBaseline = comparison))
    }

    /** Coroutine-based concurrent execution of parallel intelligence modules */
    suspend fun acceptAsync(sample: Sample, motion: MotionSnapshot? = null): TwinState = coroutineScope {
        withContext(Dispatchers.Default) {
            accept(sample, motion)
        }
    }

    /** Dispatches an on-device Local AI query (never in telemetry hot path) */
    fun queryAI(prompt: String): String = LocalAIEngine.handleQuery(
        query = prompt,
        state = state,
        forecast = latestForecast,
        anomaly = latestAnomaly,
        blackBoxEvents = blackBox.getAll()
    )

    /** Evaluates a specific controllable lever on a copy of TwinState */
    fun simulateLever(request: SimulationRequest): SimulationOutcome {
        val outcome = simulationEngine.simulateLever(state, request)
        if (outcome is SimulationResult) {
            latestSimulation = outcome
        }
        return outcome
    }

    /** Evaluates a natural language or variable simulation request */
    fun simulateQuery(query: String): SimulationOutcome {
        val outcome = simulationEngine.simulateQuery(state, query)
        if (outcome is SimulationResult) {
            latestSimulation = outcome
        }
        return outcome
    }

    fun pin(minutes: Int, wall: Long, action: String = "No intervention — forecast validation"): Boolean {
        val s = state.latest ?: return false
        if (!state.running || state.pending.size >= 3 || s.workload != workload || history.isEmpty()) return false
        val f = forecastEngine.forecast(history.toList(), minutes, state.dna)
        if (f.temperature == null && f.battery == null) return false
        val ticket = PredictionTicket(++ticketId, f, workload, s.charging, action, s.batteryC)
        state = state.copy(pending = state.pending + ticket)
        event(wall, "PREDICTION", "Pinned $minutes minute forecast. $action")
        blackBox.record(
            eventType = "PIN_PREDICTION",
            twinState = state,
            forecast = latestForecast,
            intervention = action,
            predictedOutcome = f.temperature?.value,
            notes = "Pinned $minutes min forecast for verification"
        )
        return true
    }

    fun markIntervention(wall: Long, action: String) {
        changedTickets.addAll(state.pending.map { it.id })
        event(wall, "INTERVENTION", action + " Before/after observations are not proof of causation.")
        blackBox.record(
            eventType = "INTERVENTION",
            twinState = state,
            intervention = action,
            notes = "Manual intervention logged"
        )
    }

    fun stop(wall: Long, reason: String = "User stopped session") {
        if (!state.running) return
        val canceled = state.pending.map { t ->
            Verification(
                t.id, wall, t.forecast.horizonMinutes,
                t.forecast.temperature?.value, null, null, t.forecast.battery?.value, null, t.action,
                "CANCELLED — session ended", true, t.baselineTemperature, t.forecast.temperatureBiasApplied
            )
        }
        val startTemp = sessionStartTemperatureC
        val endTemp = state.latest?.batteryC
        val startBatt = history.firstOrNull()?.batteryPct
        val endBatt = state.latest?.batteryPct
        val durMins = if (startedAt > 0) ((state.latest?.elapsedMs ?: startedAt) - startedAt) / 60_000.0 else 0.0

        val verification = state.autoCoolVerification
        val updatedBehavior = state.dna.behaviorModel.updateWithSession(
            sessionWorkload = workload,
            durationMinutes = durMins,
            startTempC = startTemp,
            endTempC = endTemp,
            startBatteryPct = startBatt,
            endBatteryPct = endBatt,
            wasRecording = (state.overhead?.gamingCaptureState == "RECORDING" || state.overhead?.gamingCaptureState == "BUFFERING"),
            wasCharging = state.chargingState == true,
            interventionApplied = (state.autoCoolDecision?.strategy?.level ?: 0) > 0,
            postInterventionCoolingRate = verification?.temperatureDelta?.let { delta ->
                val sec = verification.coolingResponseTimeSec
                if (sec > 0) (delta / (sec / 60.0)) else null
            } ?: state.dna.thermalDNA.averageCoolingRateCPerMin
        )
        val sessionDNA = if (summaries.isNotEmpty()) LearningEngine.completedSession(state.dna, summaries) else state.dna
        val finalDNA = sessionDNA.copy(behaviorModel = updatedBehavior)

        state = state.copy(
            running = false,
            pending = emptyList(),
            verifications = (state.verifications + canceled).takeLast(60),
            dna = finalDNA,
            policy = ObservationPolicy(ObservationMode.LOW_POWER, config.lowPowerMs, "Session stopped", false)
        )
        event(wall, "SESSION", reason)
        blackBox.record(
            eventType = "SESSION_STOP",
            twinState = state,
            notes = reason
        )
    }

    fun fail(message: String, wall: Long) {
        stop(wall, message)
        state = state.copy(error = message)
    }
}

