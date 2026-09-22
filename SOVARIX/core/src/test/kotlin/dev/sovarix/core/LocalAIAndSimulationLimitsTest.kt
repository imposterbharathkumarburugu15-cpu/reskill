package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class LocalAIAndSimulationLimitsTest {

    private fun createSample(t: Long = 60_000L, c: Double = 37.5) = Sample(
        elapsedMs = t,
        wallMs = 1_700_000_000_000L + t,
        batteryPct = 80.0,
        batteryC = c,
        charging = false,
        currentUa = -700_000,
        thermalStatus = 1,
        headroom = 0.5,
        availableMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 8_000_000_000L,
        lowMemory = false,
        appPssKb = 30_000,
        processCpuMs = 500L,
        powerSave = false,
        interactive = true,
        workload = Workload.GAMING,
        collectionMs = 2.5
    )

    private fun createTwinState(): TwinState {
        val sample = createSample()
        val history = (0..180 step 10).map { createSample(it * 1000L, 36.0 + it * 0.01) }
        return TwinState(
            timestamp = sample.wallMs,
            battery = sample.batteryPct,
            temperature = sample.batteryC,
            thermalState = sample.thermalStatus,
            workload = Workload.GAMING,
            latest = sample,
            history = history,
            running = true
        )
    }

    @Test
    fun localAIConvertsWhatIfQuestionsIntoStructuredSimulationRequests() {
        // 1. FPS Reduction query
        val fpsIntent = LocalAIEngine.parseIntent("What happens if I reduce the FPS to 60?")
        assertEquals(LocalAIIntentType.SIMULATE, fpsIntent.type)
        assertNotNull(fpsIntent.simulationRequest)
        assertEquals(ControllableLever.FPS_CAP, fpsIntent.simulationRequest?.lever)
        assertEquals("60", fpsIntent.simulationRequest?.targetValue)

        // 2. Workload Reduction query
        val workloadIntent = LocalAIEngine.parseIntent("What if I lower workload to 50%?")
        assertEquals(LocalAIIntentType.SIMULATE, workloadIntent.type)
        assertEquals(ControllableLever.WORKLOAD_INTENSITY, workloadIntent.simulationRequest?.lever)
        assertEquals("50%", workloadIntent.simulationRequest?.targetValue)

        // 3. App Dimming query
        val dimIntent = LocalAIEngine.parseIntent("Can you simulate dimming the screen to 20%?")
        assertEquals(LocalAIIntentType.SIMULATE, dimIntent.type)
        assertEquals(ControllableLever.APP_DIMMING, dimIntent.simulationRequest?.lever)

        // 4. Resolution Scale query
        val resIntent = LocalAIEngine.parseIntent("What if we drop resolution to 720p?")
        assertEquals(LocalAIIntentType.SIMULATE, resIntent.type)
        assertEquals(ControllableLever.RESOLUTION_SCALE, resIntent.simulationRequest?.lever)
        assertEquals("720p", resIntent.simulationRequest?.targetValue)
    }

    @Test
    fun localAIAndSimulationEngineExplicitlyRejectUnsupportedVariables() {
        val twin = createTwinState()

        // 1. CPU Overclocking request
        val overclockIntent = LocalAIEngine.parseIntent("What if I overclock the CPU to 3.2 GHz?")
        assertEquals(LocalAIIntentType.UNSUPPORTED_SIMULATION, overclockIntent.type)
        assertTrue(overclockIntent.unsupportedVariable!!.contains("Overclocking"))

        val overclockOutcome = SimulationEngine.simulateQuery(twin, "overclock the CPU to 3.2 GHz")
        assertTrue("Must be SimulationUnsupported", overclockOutcome is SimulationUnsupported)
        assertFalse(overclockOutcome.isSupported)
        val unsupported = overclockOutcome as SimulationUnsupported
        assertTrue(unsupported.reason.contains("cannot be controlled or reliably modeled"))

        // 2. Kernel Governor request
        val govIntent = LocalAIEngine.parseIntent("Change kernel CPU governor to performance mode")
        assertEquals(LocalAIIntentType.UNSUPPORTED_SIMULATION, govIntent.type)

        // 3. Q-chip overclocking
        val qChipIntent = LocalAIEngine.parseIntent("Enable Q-chip turbo boost")
        assertEquals(LocalAIIntentType.UNSUPPORTED_SIMULATION, qChipIntent.type)

        // 4. Local AI explanation of rejection
        val rejectionExplanation = LocalAIEngine.handleQuery("Can you overclock the CPU to 3.2GHz?", twin)
        assertTrue(rejectionExplanation.contains("Simulation Rejected"))
        assertTrue(rejectionExplanation.contains("Supported controllable levers"))
    }

    @Test
    fun simulationOperatesOnCopyAndNeverMutatesRealTwinState() {
        val twin = createTwinState()
        val originalTemp = twin.temperature

        val request = SimulationRequest(ControllableLever.FPS_CAP, "60")
        val outcome = SimulationEngine.simulateLever(twin, request)

        assertTrue(outcome is SimulationResult)
        val result = outcome as SimulationResult
        assertTrue(result.isSupported)
        assertEquals(ControllableLever.FPS_CAP, result.activeLever)
        assertEquals("60", result.requestedValue)
        assertEquals(2, result.scenarios.size)

        // Counterfactual scenario shows lower or equal temperature
        val baseline = result.scenarios.first { it.id == "scenario_a" }
        val simulated = result.scenarios.first { it.id.startsWith("scenario_lever") }
        assertTrue(simulated.predictedTemperatureC!! <= baseline.predictedTemperatureC!!)

        // CRITICAL: Real TwinState was NEVER mutated
        assertEquals(originalTemp, twin.temperature)
        assertEquals(originalTemp, twin.latest?.batteryC)
    }

    @Test
    fun localAIGeneratesGroundedNumericalExplanationsWithoutInventingValues() {
        val twin = createTwinState()
        val request = SimulationRequest(ControllableLever.FPS_CAP, "60")
        val outcome = SimulationEngine.simulateLever(twin, request)

        val explanation = LocalAIEngine.explainSimulation(outcome)
        assertTrue(explanation.contains("What-If Explanation"))
        assertTrue(explanation.contains("FPS Cap to 60"))
        assertTrue(explanation.contains("Current Trajectory:"))
        assertTrue(explanation.contains("Counterfactual Projection:"))
        assertTrue(explanation.contains("Evaluated on a cloned copy of TwinState"))

        // Query handle integration
        val aiResponse = LocalAIEngine.handleQuery("What happens if I reduce the FPS to 60?", twin)
        assertTrue(aiResponse.contains("What-If Explanation"))
        assertTrue(aiResponse.contains("FPS Cap to 60"))
    }

    @Test
    fun anomalyLayerAdheresStrictlyToDirectiveOutputModel() {
        val twin = createTwinState()
        val anomaly = AnomalyEngine.evaluate(twin)

        // Section 6 fields check
        assertNotNull(anomaly.type)
        assertNotNull(anomaly.severity)
        assertNotNull(anomaly.observedValue)
        assertEquals(twin.latest?.batteryC, anomaly.observedValue)
        assertTrue(anomaly.timestamp > 0)

        // Explanation matches actual evidence
        val explanation = LocalAIEngine.explainAnomaly(anomaly)
        assertTrue(explanation.contains("Anomaly Analysis"))
        assertTrue(explanation.contains("Observed Battery Temp:"))
    }

    @Test
    fun localAISummarizesBlackBoxChronologicalLedger() {
        val blackBox = BlackBox()
        val twin = createTwinState()

        blackBox.record(
            eventType = "PIN_PREDICTION",
            twinState = twin,
            predictedOutcome = 38.5,
            notes = "Pinned 2m forecast"
        )
        blackBox.record(
            eventType = "VERIFICATION",
            twinState = twin,
            predictedOutcome = 38.5,
            actualOutcome = 38.1,
            predictionError = -0.4,
            notes = "Verified within tolerance"
        )

        val summary = LocalAIEngine.summarizeBlackBox(blackBox.getAll())
        assertTrue(summary.contains("Black Box Ledger Summary"))
        assertTrue(summary.contains("Total Events: 2"))
        assertTrue(summary.contains("Predictions Verified: 1"))
        assertTrue(summary.contains("0.40°C"))
    }
}
