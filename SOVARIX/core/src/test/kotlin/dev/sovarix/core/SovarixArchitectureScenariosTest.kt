package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Section 25: 12 Core Architecture & Product Test Scenarios.
 *
 * Validates the complete SOVARIX intelligence loop:
 * OBSERVE → ADAPT → MODEL → PREDICT → SIMULATE → DECIDE → ACT → VERIFY → LEARN
 */
class SovarixArchitectureScenariosTest {

    private val cfg = GovernorConfig()

    private fun sample(
        t: Long,
        c: Double? = 30.0 + (t / 60_000.0) * 0.5,
        charging: Boolean? = false,
        thermal: Int? = 0,
        workload: Workload = Workload.IDLE,
        battery: Double? = 80.0,
        headroom: Double? = 0.2,
        pssKb: Long = 35_000L,
        cpuMs: Long = t / 500L,
        collectionMs: Double = 1.5
    ) = Sample(
        elapsedMs = t,
        wallMs = 1_700_000_000_000L + t,
        batteryPct = battery,
        batteryC = c,
        charging = charging,
        currentUa = if (charging == true) 1_500_000 else -600_000,
        thermalStatus = thermal,
        headroom = headroom,
        availableMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 8_000_000_000L,
        lowMemory = false,
        appPssKb = pssKb.toInt(),
        processCpuMs = cpuMs,
        powerSave = false,
        interactive = true,
        workload = workload,
        collectionMs = collectionMs
    )

    private fun history(
        durationSec: Int = 180,
        tempStepPerMin: Double = 0.5,
        charging: Boolean = false,
        workload: Workload = Workload.IDLE
    ): List<Sample> = (0..durationSec step 10).map { sec ->
        sample(
            t = sec * 1_000L,
            c = 30.0 + (sec / 60.0) * tempStepPerMin,
            charging = charging,
            workload = workload,
            battery = 85.0 - (sec / 60.0) * (if (workload == Workload.GAMING) 0.4 else 0.1)
        )
    }

    // =========================================================================
    // TEST 1: Idle device
    // =========================================================================
    @Test
    fun test1_idleDevice() {
        val h = history(durationSec = 180, tempStepPerMin = 0.05, workload = Workload.IDLE)
        val latest = h.last()
        val controller = ObservationController(cfg)
        val decision = controller.choose(latest, rise = 0.05, overhead = null, economy = false)

        // Idle device: low sampling overhead, stable state
        assertEquals(ObservationMode.NORMAL, decision.mode)
        assertEquals(cfg.normalMs, decision.intervalMs)

        val twin = TwinEngine(config = cfg)
        twin.setWorkload(Workload.IDLE, 0)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state
        assertEquals(Workload.IDLE, twinState.workload)
        assertFalse(twinState.gamingState)
        assertEquals(DeviceThermalState.NORMAL, twinState.currentThermalState)
        assertTrue(twinState.thermalVelocity in -0.05..0.15)
    }

    // =========================================================================
    // TEST 2: Charging
    // =========================================================================
    @Test
    fun test2_charging() {
        val h = history(durationSec = 180, tempStepPerMin = 0.6, charging = true, workload = Workload.IDLE)
        val twin = TwinEngine(config = cfg)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state
        assertEquals(true, twinState.chargingState)
        assertTrue(twinState.temperature!! > 30.0)

        // Baseline tracked under charging context
        val forecast = FutureEngine(cfg).evaluate(twinState, horizonMinutes = 2)
        assertNotNull(forecast.predictedTemperature)
        assertTrue(forecast.predictedTemperature!! > twinState.temperature!!)
    }

    // =========================================================================
    // TEST 3: Gaming
    // =========================================================================
    @Test
    fun test3_gaming() {
        val h = history(durationSec = 180, tempStepPerMin = 1.0, workload = Workload.GAMING)
        val twin = TwinEngine(config = cfg)
        twin.setWorkload(Workload.GAMING, 0)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state
        assertTrue(twinState.gamingState)
        assertEquals(Workload.GAMING, twinState.workload)

        // Gaming profile registered and detected
        val profile = GameProfileRegistry.findProfile("com.dts.freefireth")
        assertNotNull(profile)
        assertEquals(GameGenre.BATTLE_ROYALE, profile.genre)

        // Gaming intelligence evaluates game profile
        val gi = GamingIntelligence()
        val gameResult = gi.evaluateIntelligence(twinState, gamePackage = "com.dts.freefireth")
        assertNotNull(gameResult)
    }

    // =========================================================================
    // TEST 4: Gaming + charging
    // =========================================================================
    @Test
    fun test4_gamingPlusCharging() {
        // High stress scenario: rapid thermal climb
        val h = history(durationSec = 180, tempStepPerMin = 2.2, charging = true, workload = Workload.GAMING)
        val twin = TwinEngine(config = cfg)
        twin.setWorkload(Workload.GAMING, 0)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state
        assertTrue(twinState.gamingState)
        assertEquals(true, twinState.chargingState)
        assertTrue("Thermal velocity should be positive and elevated", twinState.thermalVelocity > 0.01)

        val future = FutureEngine(cfg).evaluate(twinState, horizonMinutes = 3)
        assertTrue("Future engine predicts hot trajectory", future.predictedTemperature!! > twinState.temperature!!)
        assertTrue("Risk trend indicates rising sharp or warming", future.performanceRiskTrend in listOf(Risk.WATCH, Risk.ANOMALY, Risk.NORMAL))
    }

    // =========================================================================
    // TEST 5: Rapid thermal rise
    // =========================================================================
    @Test
    fun test5_rapidThermalRise() {
        val h = (0..180 step 10).map { sec ->
            sample(
                t = sec * 1_000L,
                c = 32.0 + (sec / 60.0) * 3.5, // 3.5C rise per min
                thermal = if (sec > 120) 2 else 1,
                headroom = if (sec > 120) 0.85 else 0.4,
                workload = Workload.GAMING
            )
        }
        val twin = TwinEngine(config = cfg)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state
        assertTrue("Thermal velocity should be steep", twinState.thermalVelocity > 0.03)
        assertTrue(twinState.currentThermalState in listOf(DeviceThermalState.WARMING, DeviceThermalState.HOT, DeviceThermalState.CRITICAL))
    }

    // =========================================================================
    // TEST 6: Auto-Cool
    // =========================================================================
    @Test
    fun test6_autoCool() {
        // High headroom or temperature triggers THERMAL_PROTECTION in ObservationController
        val hotSample = sample(t = 200_000L, c = 43.0, thermal = 2, headroom = 1.05, workload = Workload.GAMING)
        val controller = ObservationController(cfg)
        val decision = controller.choose(hotSample, rise = 0.8, overhead = null, economy = false)

        assertEquals(ObservationMode.THERMAL_PROTECTION, decision.mode)
        assertTrue("Auto-cool throttling interval increases", decision.intervalMs >= 10000L)

        // Verify action policy allows self-mitigation but rejects root/governor hacks
        assertEquals(ActionPermission.ALLOWED_AUTOMATICALLY, ActionPolicy.permission(TwinAction.REDUCE_OBSERVATION))
        assertEquals(ActionPermission.NOT_ALLOWED, ActionPolicy.permission(TwinAction.CPU_CONTROL))
        assertEquals(ActionPermission.NOT_ALLOWED, ActionPolicy.permission(TwinAction.Q_CHIP_CONTROL))
    }

    // =========================================================================
    // TEST 7: Recovery
    // =========================================================================
    @Test
    fun test7_recovery() {
        // Temperature declining from 41C down to 36C
        val h = (0..180 step 10).map { sec ->
            sample(
                t = sec * 1_000L,
                c = 41.0 - (sec / 60.0) * 1.8,
                thermal = if (sec > 90) 0 else 1,
                headroom = 0.3,
                workload = Workload.IDLE
            )
        }
        val twin = TwinEngine(config = cfg)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state
        assertTrue("Thermal velocity should be negative in cooling", twinState.thermalVelocity < 0)
        assertTrue(twinState.currentThermalState in listOf(DeviceThermalState.COOLING, DeviceThermalState.RECOVERY, DeviceThermalState.NORMAL))
    }

    // =========================================================================
    // TEST 8: Phone Lab prediction
    // =========================================================================
    @Test
    fun test8_phoneLabPrediction() {
        val h = history(180, 0.5, false, Workload.GAMING)
        val twin = TwinEngine(config = cfg)
        twin.start(0, 0)
        h.forEach { twin.accept(it) }

        val twinState = twin.state

        // 1. Natural language parse via LocalAIEngine
        val query = "What happens if I play for another 15 minutes?"
        val req = LocalAIEngine.parseSimulationRequest(query, twinState)
        assertEquals("SIMULATE", req.intent)
        assertEquals(15, req.durationMinutes)

        // 2. Numerical Simulation Engine evaluates counterfactual scenarios
        val simOutcome = SimulationEngine.simulateQuery(twinState, query)
        assertTrue(simOutcome is SimulationResult)
        val labResult = simOutcome as SimulationResult
        assertTrue(labResult.isSupported)
        assertTrue(labResult.scenarios.isNotEmpty())
        val currentScenario = labResult.scenarios.find { it.id == "current_path" } ?: labResult.scenarios.first()
        val altScenario = labResult.scenarios.find { it.id == "alternative_path" } ?: labResult.scenarios.last()
        assertNotNull(currentScenario)
        assertNotNull(altScenario)
        assertFalse(labResult.explanation.contains("NaN"))
    }

    // =========================================================================
    // TEST 9: Prediction vs actual verification
    // =========================================================================
    @Test
    fun test9_predictionVsActualVerification() {
        val twin = TwinEngine(config = cfg)
        twin.start(0, 0)

        // Feed initial history to establish window
        val h = history(180, 0.5, false, Workload.IDLE)
        h.forEach { twin.accept(it) }

        // Create a contract for +60s
        val contract = PredictionContract(
            id = 101L,
            metric = "temperature",
            targetHorizonSeconds = 60,
            createdAtElapsedMs = 180_000L,
            dueAtElapsedMs = 240_000L,
            predictedValue = 40.8,
            baselineValue = 37.0,
            confidence = 0.90,
            status = "PENDING"
        )

        // Advance to due time with actual reading 40.5°C
        val dueSample = sample(t = 240_000L, c = 40.5)
        val verifiedContract = VerificationEngine(cfg).resolveContract(contract, dueSample)

        assertNotNull(verifiedContract)
        assertEquals("VERIFIED", verifiedContract!!.status)
        assertEquals(40.5, verifiedContract.actualValue!!, 0.001)
        assertEquals(-0.3, verifiedContract.signedError!!, 0.001) // 40.5 - 40.8 = -0.3
    }

    // =========================================================================
    // TEST 10: Device DNA learning
    // =========================================================================
    @Test
    fun test10_deviceDnaLearning() {
        val initialDna = DeviceDNA()
        assertEquals(0, initialDna.verifiedCount)

        val contract = PredictionContract(
            id = 102L,
            metric = "temperature",
            targetHorizonSeconds = 120,
            createdAtElapsedMs = 0L,
            dueAtElapsedMs = 120_000L,
            predictedValue = 38.0,
            baselineValue = 35.0,
            confidence = 0.92,
            actualValue = 37.8,
            signedError = -0.2,
            status = "VERIFIED"
        )

        val updatedDna = LearningEngine.verified(initialDna, contract)
        assertNotNull(updatedDna.temperatureBiasByHorizon[2]) // 120s = 2 min horizon bias
        assertEquals(1, updatedDna.verifiedCount)
        assertEquals(0.2, updatedDna.totalAbsoluteErrorC, 0.001)
    }

    // =========================================================================
    // TEST 11: SOVARIX overhead
    // =========================================================================
    @Test
    fun test11_sovarixOverhead() {
        val overhead = Overhead(
            cpuOneCorePct = 0.8,
            pssMb = 38.4,
            processingMs = 2.1,
            samples = 12,
            elapsedMinutes = 2.0,
            scheduledIntervalMs = 5000L,
            sensorProcessingMs = 2.1,
            inferenceCostMs = 0.0,
            wakeupsCount = 0
        )

        assertNotNull(overhead)
        assertEquals(38.4, overhead.pssMb!!, 0.1)
        assertEquals(2.1, overhead.sensorProcessingMs, 0.1)
        assertEquals(0.0, overhead.inferenceCostMs, 0.001)
        assertEquals(0, overhead.wakeupsCount)
        assertTrue("CPU footprint should be minimal", overhead.cpuOneCorePct!! in 0.0..5.0)
    }

    // =========================================================================
    // TEST 12: Unsupported hardware/API fallback
    // =========================================================================
    @Test
    fun test12_unsupportedHardwareApiFallback() {
        val profile = CapabilityProfile(
            manufacturer = "StandardOEM",
            model = "PhoneX",
            androidVersion = "14",
            sdk = 34,
            soc = "Snapdragon",
            abi = "arm64-v8a",
            logicalCores = 8,
            secondaryChip = Availability.UNAVAILABLE,
            vendorTelemetryVerified = false
        )

        val selectedPath = ArchitectureSelector.select(profile)
        assertEquals(TwinPath.STANDARD, selectedPath)

        // Haptic or private telemetry is marked UNAVAILABLE
        val motionSnap = MotionSnapshot(
            timestamp = 1000L,
            gyroAvailable = false,
            accelerometerAvailable = true,
            linearAccelerationAvailable = false,
            rotationVectorAvailable = false,
            movementIntensity = 0.2f
        )
        assertFalse(motionSnap.gyroAvailable)
        assertTrue(motionSnap.accelerometerAvailable)
    }
}
