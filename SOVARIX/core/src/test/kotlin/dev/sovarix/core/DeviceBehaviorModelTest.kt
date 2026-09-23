package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class DeviceBehaviorModelTest {

    @Test
    fun testInitialBehaviorModelAndInsufficientDataInsight() {
        val model = DeviceBehaviorModel()
        val insights = model.generateInsights()

        assertTrue(insights.isNotEmpty())
        assertTrue(insights.first().contains("Still learning your device"))
    }

    @Test
    fun testBehaviorModelUpdatesWithGamingAndRecordingSessions() {
        var model = DeviceBehaviorModel()

        // Feed 3 gaming sessions
        for (i in 1..3) {
            model = model.updateWithSession(
                sessionWorkload = Workload.GAMING,
                durationMinutes = 30.0,
                startTempC = 37.0,
                endTempC = 41.5,
                startBatteryPct = 90.0,
                endBatteryPct = 82.0,
                wasRecording = true,
                wasCharging = false,
                interventionApplied = true,
                postInterventionCoolingRate = -0.35
            )
        }

        assertTrue("Gaming sessions count should be 3", model.gamingSessionsCount >= 3)
        assertTrue("Recording sessions count should be 3", model.recordingSessionsCount >= 3)
        assertTrue("Total observations should be 3", model.totalObservations >= 3)
        assertNotNull("Recording thermal delta should be calculated", model.recordingThermalDeltaCPerMin)
        assertNotNull("Intervention recovery rate should be recorded", model.interventionRecoveryRateCPerMin)

        val scores = model.computeDNAScores()
        assertTrue("Thermal score in range", scores.thermalResponse in 0.0..1.0)
        assertTrue("Endurance score in range", scores.gamingEndurance in 0.0..1.0)
        assertTrue("Recovery score in range", scores.recoveryBehavior in 0.0..1.0)
        assertTrue("Battery score in range", scores.batteryResponse in 0.0..1.0)
    }

    @Test
    fun testChargingGamingMultiplier() {
        var model = DeviceBehaviorModel()

        // Feed 2 charging gaming sessions with high heating
        for (i in 1..2) {
            model = model.updateWithSession(
                sessionWorkload = Workload.GAMING,
                durationMinutes = 20.0,
                startTempC = 36.0,
                endTempC = 43.0, // High heating while charging
                startBatteryPct = 50.0,
                endBatteryPct = 70.0,
                wasRecording = false,
                wasCharging = true,
                interventionApplied = false,
                postInterventionCoolingRate = null
            )
        }

        assertNotNull(model.chargingGamingThermalMultiplier)
        assertTrue("Charging while gaming should show elevated heating multiplier", (model.chargingGamingThermalMultiplier ?: 0.0) >= 1.0)
    }

    @Test
    fun testAutopilotDecisionEngineGoals() {
        val baseState = TwinState(
            temperature = 42.5,
            thermalState = 3,
            workload = Workload.GAMING,
            gamingState = true
        )
        val model = DeviceBehaviorModel()

        // KEEP_PHONE_COOL under high thermal state -> PROTECT with self-throttling
        val coolDecision = AutopilotDecisionEngine.evaluate(baseState, DeviceGoal.KEEP_PHONE_COOL, model)
        assertEquals(ProductState.PROTECT, coolDecision.productState)
        assertTrue(coolDecision.permittedActions.contains(AutopilotAction.THROTTLE_SOVARIX_SENSORS))
        assertTrue(coolDecision.permittedActions.contains(AutopilotAction.SWITCH_ECO_GOVERNOR))

        // PRESERVE_BATTERY -> switches to ECO governor
        val lowBattState = baseState.copy(battery = 15.0)
        val battDecision = AutopilotDecisionEngine.evaluate(lowBattState, DeviceGoal.PRESERVE_BATTERY, model)
        assertTrue(battDecision.permittedActions.contains(AutopilotAction.SWITCH_ECO_GOVERNOR))
        assertTrue(battDecision.permittedActions.contains(AutopilotAction.PAUSE_VIDEO_BUFFER))
    }

    @Test
    fun testMultiHorizonForecastEvaluator() {
        val history = (1..20).map { i ->
            Sample(
                elapsedMs = i * 10_000L,
                wallMs = 1700000000000L + i * 10_000L,
                batteryPct = 80.0 - i * 0.1,
                batteryC = 37.0 + i * 0.15,
                charging = false,
                currentUa = -500_000,
                thermalStatus = 1,
                headroom = 0.5,
                availableMemoryBytes = 4_000_000_000L,
                totalMemoryBytes = 8_000_000_000L,
                lowMemory = false,
                appPssKb = 45000,
                processCpuMs = i * 50L,
                powerSave = false,
                interactive = true,
                workload = Workload.GAMING
            )
        }
        val state = TwinState(
            temperature = 40.0,
            thermalState = 1,
            history = history,
            workload = Workload.GAMING,
            gamingState = true
        )

        val result = ForecastEngine().evaluate(state)
        assertNotNull("10s forecast should be populated", result.forecast10s)
        assertNotNull("30s forecast should be populated", result.forecast30s)
        assertNotNull("60s forecast should be populated", result.forecast60s)
        assertTrue("Temperature projections should be non-null", result.forecast10s?.value != null)
    }

    @Test
    fun testCounterfactualSimulationsInSimulationEngine() {
        val state = TwinState(
            temperature = 39.0,
            battery = 80.0,
            workload = Workload.GAMING,
            gamingState = true
        )

        // Query 1: "What happens if I play for another hour?"
        val outcomeHour = SimulationEngine.simulateQuery(state, "What happens if I play for another hour?")
        assertTrue(outcomeHour is SimulationResult)
        val hourRes = outcomeHour as SimulationResult
        assertEquals(2, hourRes.scenarios.size)
        assertEquals("current_path", hourRes.scenarios[0].id)
        assertEquals("alternative_path", hourRes.scenarios[1].id)
        assertTrue("Alternative path should have higher temperature", (hourRes.scenarios[1].predictedTemperatureC ?: 0.0) > 39.0)

        // Query 2: "What if I record while gaming?"
        val outcomeRecord = SimulationEngine.simulateQuery(state, "What if I record while gaming?")
        assertTrue(outcomeRecord is SimulationResult)
        val recRes = outcomeRecord as SimulationResult
        assertTrue(recRes.scenarios.any { it.name.contains("Screen Recording") })

        // Query 3: Unsupported query should be cleanly rejected without fabricating
        val outcomeUnsupp = SimulationEngine.simulateQuery(state, "What if I overclock the GPU?")
        assertTrue(outcomeUnsupp is SimulationUnsupported)
    }

    @Test
    fun testRepairBaselineComparison() {
        val comparison = RepairBaselineComparison(
            baselineCapturedAt = 1000L,
            baselineHeatingRateCPerMin = 0.28,
            baselineCoolingRateCPerMin = 0.25,
            baselineBatteryDrainPctPerHour = 15.0,
            postRepairCapturedAt = 2000L,
            postRepairHeatingRateCPerMin = 0.26, // Improved
            postRepairCoolingRateCPerMin = 0.26,
            postRepairBatteryDrainPctPerHour = 14.5
        )

        assertTrue(comparison.isVerified)
        assertTrue(comparison.evaluationSummary.contains("stable and matches or improves upon baseline"))
    }
}
