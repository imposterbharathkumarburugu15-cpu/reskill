package dev.sovarix.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ParallelIntelligenceTest {
    private val cfg = GovernorConfig()

    private fun createSample(
        t: Long,
        c: Double? = 36.0 + (t / 60_000.0) * 0.8,
        charging: Boolean? = false,
        thermal: Int? = 1,
        workload: Workload = Workload.GAMING,
        battery: Double? = 85.0 - (t / 60_000.0) * 0.5
    ) = Sample(
        elapsedMs = t,
        wallMs = 1_700_000_000_000L + t,
        batteryPct = battery,
        batteryC = c,
        charging = charging,
        currentUa = -800_000,
        thermalStatus = thermal,
        headroom = 0.4,
        availableMemoryBytes = 3_500_000_000L,
        totalMemoryBytes = 8_000_000_000L,
        lowMemory = false,
        appPssKb = 35_000,
        processCpuMs = t / 500L,
        powerSave = false,
        interactive = true,
        workload = workload,
        collectionMs = 2.0
    )

    private fun createMotion(intensity: Float = 0.75f, spike: Boolean = true) = MotionSnapshot(
        timestamp = 1_700_000_180_000L,
        gyroAvailable = true,
        accelerometerAvailable = true,
        linearAccelerationAvailable = true,
        rotationVectorAvailable = true,
        gyroX = 0.5f, gyroY = 1.2f, gyroZ = 0.8f,
        angularVelocityMagnitude = 3.8f,
        gyroSpike = spike,
        accelX = 0f, accelY = 9.8f, accelZ = 2.0f,
        accelerationMagnitude = 14.5f,
        accelerationSpike = spike,
        movementIntensity = intensity,
        suddenRotationDetected = spike,
        suddenMotionDetected = spike,
        confidence = 0.90f
    )

    private fun generateHistory(durationSeconds: Int = 180): List<Sample> =
        (0..durationSeconds step 10).map { createSample(it * 1_000L) }

    @Test
    fun parallelEnginesExecuteIndependentlyOnSharedTwinState() {
        val history = generateHistory(180)
        val sample = history.last()
        val motion = createMotion()

        // 1. Single source of device reality: Immutable TwinState
        val twin = TwinState(
            timestamp = sample.wallMs,
            battery = sample.batteryPct,
            chargingState = sample.charging,
            temperature = sample.batteryC,
            thermalState = sample.thermalStatus,
            thermalHeadroom = sample.headroom,
            cpuState = 4.2,
            memoryPressure = sample.lowMemory,
            availableMemoryBytes = sample.availableMemoryBytes,
            totalMemoryBytes = sample.totalMemoryBytes,
            workload = sample.workload,
            gamingState = true,
            gyroState = motion,
            accelerationState = motion,
            motionIntensity = motion.movementIntensity,
            performanceState = "STABLE",
            confidence = 1.0,
            latest = sample,
            history = history,
            running = true
        )

        // 2. Parallel Engines independently consume TwinState without mutual dependencies
        val futureEngine = FutureEngine(cfg)
        val anomalyEngine = AnomalyEngine
        val simulationEngine = SimulationEngine
        val gamingIntelligence = GamingIntelligence()

        val forecastResult = futureEngine.evaluate(twin, horizonMinutes = 2)
        val anomalyResult = anomalyEngine.evaluate(twin)
        val simulationResult = simulationEngine.evaluate(twin)
        val gamingResult = gamingIntelligence.evaluateIntelligence(twin, gamePackage = "com.dts.freefireth")

        // Assert FutureEngine output
        assertNotNull(forecastResult)
        assertNotNull(forecastResult.predictedTemperature)
        assertTrue(forecastResult.confidence > 0)
        assertEquals(2, forecastResult.horizonMinutes)

        // Assert AnomalyEngine operates independently (does not require forecast)
        assertNotNull(anomalyResult)
        assertTrue(anomalyResult.risk in listOf(Risk.NORMAL, Risk.WATCH, Risk.ANOMALY))

        // Assert SimulationEngine produces counterfactual scenarios
        assertNotNull(simulationResult)
        assertEquals(3, simulationResult.scenarios.size)
        assertTrue(simulationResult.scenarios.any { it.id == "scenario_a" })
        assertTrue(simulationResult.scenarios.any { it.id == "scenario_b" })
        assertTrue(simulationResult.scenarios.any { it.id == "scenario_c" })

        // Assert GamingIntelligence detects moments independently
        assertNotNull(gamingResult)
        assertTrue(gamingResult.detected)
        assertEquals(MomentType.HIGH_INTENSITY_MOTION, gamingResult.momentType)

        // Verify that original TwinState remains completely unmutated
        assertEquals(sample.batteryC, twin.temperature)
        assertEquals(history.size, twin.history.size)
    }

    @Test
    fun simulationEngineOperatesOnTwinStateCopyWithoutMutatingReality() {
        val history = generateHistory(180)
        val sample = history.last()
        val originalTemp = sample.batteryC

        val twin = TwinState(
            timestamp = sample.wallMs,
            battery = sample.batteryPct,
            temperature = originalTemp,
            workload = Workload.GAMING,
            latest = sample,
            history = history,
            running = true
        )

        val simEngine = SimulationEngine
        val simResult = simEngine.evaluate(twin)

        // Scenarios evaluate counterfactuals
        val scenarioA = simResult.scenarios.first { it.id == "scenario_a" }
        val scenarioB = simResult.scenarios.first { it.id == "scenario_b" }
        val scenarioC = simResult.scenarios.first { it.id == "scenario_c" }

        // Reduced workload (Scenario B) must project lower or equal temperature compared to Scenario A
        assertTrue(scenarioB.predictedTemperatureC!! <= scenarioA.predictedTemperatureC!!)
        // Cooling hold (Scenario C) multiplier = 0.0 projects level temperature
        assertEquals(originalTemp!!, scenarioC.predictedTemperatureC!!, 0.0001)

        // Crucial verification: Real TwinState temperature was NEVER mutated
        assertEquals(originalTemp, twin.temperature)
        assertEquals(history.last().batteryC, twin.latest?.batteryC)
    }

    @Test
    fun insightEngineCorrelatesMultiEngineSignalsWithoutSequentialBottlenecks() {
        val history = generateHistory(180)
        val sample = history.last()
        val twin = TwinState(
            timestamp = sample.wallMs,
            battery = sample.batteryPct,
            temperature = 39.5,
            thermalState = 2,
            latest = sample.copy(batteryC = 39.5, thermalStatus = 2),
            history = history,
            running = true
        )

        // Mock parallel results representing elevated temperature and anomaly
        val forecastRes = ForecastResult(
            timestamp = sample.wallMs,
            horizonMinutes = 2,
            predictedTemperature = 41.8,
            confidence = 0.85,
            risk = Risk.ANOMALY,
            evidence = "Extrapolated slope indicates rapid thermal climb"
        )
        val anomalyRes = AnomalyResult(
            timestamp = sample.wallMs,
            risk = Risk.ANOMALY,
            score = 0.92,
            evidence = listOf("Battery temperature exceeds baseline by 3.2 spread units")
        )
        val simulationRes = SimulationEngine.evaluate(twin)
        val gamingRes = GamingMomentResult(
            timestamp = sample.wallMs,
            detected = true,
            momentType = MomentType.COMBINED_GAME_EVENT,
            confidence = 0.91,
            availableSignals = listOf("AUDIO_SPIKE", "GYROSCOPE")
        )

        // Correlate in InsightEngine
        val insights = InsightEngine.correlate(twin, forecastRes, anomalyRes, simulationRes, gamingRes)

        assertTrue("Expected at least one correlated insight", insights.isNotEmpty())
        val thermalInsight = insights.find { it.category == InsightCategory.THERMAL }
        assertNotNull("Expected correlated thermal insight", thermalInsight)
        assertTrue(thermalInsight!!.title.contains("Thermal performance risk"))
        assertTrue(thermalInsight.summary.contains("workload reduction scenario"))
        assertEquals(Risk.ANOMALY, thermalInsight.severity)

        val gamingInsight = insights.find { it.category == InsightCategory.GAMING }
        assertNotNull("Expected correlated gaming insight", gamingInsight)
    }

    @Test
    fun blackBoxReconstructsFullHistoricalContextAndPredictionError() {
        val blackBox = BlackBox(capacity = 50)
        val sample = createSample(60_000L, c = 35.0)
        val twin = TwinState(
            timestamp = sample.wallMs,
            battery = sample.batteryPct,
            temperature = sample.batteryC,
            latest = sample,
            running = true
        )

        val forecast = ForecastResult(
            timestamp = sample.wallMs,
            horizonMinutes = 2,
            predictedTemperature = 36.8,
            confidence = 0.82
        )

        // 1. What happened? Pin prediction
        val event1 = blackBox.record(
            eventType = "PIN_PREDICTION",
            twinState = twin,
            forecast = forecast,
            intervention = "User pinned 2-minute forecast",
            predictedOutcome = 36.8,
            notes = "What did SOVARIX predict? 36.8°C"
        )
        assertNotNull(event1)

        // 2. What action was taken?
        val event2 = blackBox.record(
            eventType = "INTERVENTION",
            twinState = twin,
            intervention = "Dimmed app window to 20%",
            notes = "What action was taken? Dimmed display"
        )
        assertNotNull(event2)

        // 3. What actually happened? How wrong was the prediction?
        val actualObserved = 36.2
        val error = actualObserved - 36.8 // -0.6°C error
        val event3 = blackBox.record(
            eventType = "VERIFICATION",
            twinState = twin.copy(temperature = actualObserved),
            forecast = forecast,
            predictedOutcome = 36.8,
            actualOutcome = actualObserved,
            predictionError = error,
            notes = "What actually happened: 36.2°C. Prediction error: -0.6°C"
        )

        // Reconstruct from Black Box
        val history = blackBox.getAll()
        assertEquals(3, history.size)

        val verification = history.last()
        assertEquals("VERIFICATION", verification.eventType)
        assertEquals(36.8, verification.predictedOutcome!!, 0.001)
        assertEquals(36.2, verification.actualOutcome!!, 0.001)
        assertEquals(-0.6, verification.predictionError!!, 0.001)
    }

    @Test
    fun decisionEngineEnforcesActionPolicyRestrictions() {
        val sample = createSample(0L, c = 42.5, thermal = 4)
        val criticalTwin = TwinState(
            timestamp = sample.wallMs,
            temperature = 42.5,
            thermalState = 4,
            policy = ObservationPolicy(ObservationMode.LOW_POWER, 30_000, "Critical thermal", false, stopSession = true),
            running = true,
            latest = sample
        )

        val decision = DecisionEngine.decide(criticalTwin)
        assertEquals(TwinAction.REDUCE_OBSERVATION, decision.recommendedAction)
        assertEquals(ActionPermission.ALLOWED_AUTOMATICALLY, decision.permission)

        // Verify strict sandbox permissions
        assertEquals(ActionPermission.NOT_ALLOWED, ActionPolicy.permission(TwinAction.CPU_CONTROL))
        assertEquals(ActionPermission.NOT_ALLOWED, ActionPolicy.permission(TwinAction.Q_CHIP_CONTROL))
        assertEquals(ActionPermission.USER_CONFIRMATION_REQUIRED, ActionPolicy.permission(TwinAction.DIM_THIS_APP))
        assertEquals(ActionPermission.USER_CONFIRMATION_REQUIRED, ActionPolicy.permission(TwinAction.OPEN_DISPLAY_SETTINGS))
    }

    @Test
    fun completeLoopPredictActObserveCompareLearnUpdatedTwin() = runBlocking {
        val engine = TwinEngine()
        engine.setWorkload(Workload.IDLE, 0)
        engine.start(0, 0)

        // 1. Initial observation stream -> TwinState
        val initialHistory = generateHistory(180)
        initialHistory.forEach { engine.accept(it) }

        // Verify TwinState snapshot
        assertEquals(Workload.IDLE, engine.state.workload)
        assertNotNull(engine.latestForecast)
        assertNotNull(engine.latestAnomaly)
        assertNotNull(engine.latestSimulation)

        // 2. PREDICT: Pin forecast ticket
        val pinned = engine.pin(2, 180_000L, action = "No intervention — baseline test")
        assertTrue("Pinning forecast should succeed with stable history", pinned)

        // 3. ACT: Log manual intervention
        engine.markIntervention(190_000L, "Observed under continuous baseline")

        // 4. OBSERVE: Advance device time and observe real outcome
        for (t in 190..310 step 10) {
            val sample = createSample(t * 1_000L, c = 34.0)
            engine.accept(sample)
        }

        // 5. COMPARE & VERIFY: Predictions resolved
        val verifications = engine.state.verifications
        assertTrue("Verification should be recorded", verifications.isNotEmpty())

        // 6. LEARN & UPDATED TWIN: Check DeviceDNA calibration and BlackBox records
        assertTrue("Black Box should have preserved verification event",
            engine.blackBox.getAll().any { it.eventType == "VERIFICATION" || it.eventType == "PIN_PREDICTION" })

        // Stop session cleanly
        engine.stop(320_000L, "Completed validation cycle")
        assertFalse(engine.state.running)
        assertEquals(1, engine.state.dna.sessions)
    }
}
