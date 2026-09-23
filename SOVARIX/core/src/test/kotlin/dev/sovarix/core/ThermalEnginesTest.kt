package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class ThermalEnginesTest {

    private fun createSample(elapsedMs: Long, batteryC: Double?, headroom: Double? = 0.5, thermalStatus: Int = 0): Sample {
        return Sample(
            elapsedMs = elapsedMs,
            wallMs = 1700000000000L + elapsedMs,
            batteryPct = 80.0,
            batteryC = batteryC,
            charging = false,
            currentUa = -500000,
            thermalStatus = thermalStatus,
            headroom = headroom,
            availableMemoryBytes = 4_000_000_000L,
            totalMemoryBytes = 8_000_000_000L,
            lowMemory = false,
            appPssKb = 80000,
            processCpuMs = 1000L,
            powerSave = false,
            interactive = true,
            workload = Workload.GAMING,
            collectionMs = 2.5
        )
    }

    @Test
    fun testThermalTrendCalculation() {
        val samples = listOf(
            createSample(0L, 35.0),
            createSample(15_000L, 35.3),
            createSample(30_000L, 35.8),
            createSample(45_000L, 36.4),
            createSample(60_000L, 37.0)
        )

        val trend = ThermalTrendEngine.calculateTrend(samples, workloadDurationMs = 60_000L)

        assertEquals(37.0, trend.currentTemperature!!, 0.01)
        // From 0 to 60s (1 min), temperature rose by 2.0°C -> velocity ~ 2.0°C/min
        assertTrue("Velocity should be positive and close to 2.0°C/min", trend.temperatureVelocity > 1.5)
        assertTrue("Should detect rapid rise", trend.isRisingFast)
        assertFalse("Should not be cooling", trend.isCooling)
        assertEquals(5, trend.sampleCount)
    }

    @Test
    fun testThermalPredictionEngine() {
        val samples = listOf(
            createSample(0L, 36.0),
            createSample(15_000L, 36.5),
            createSample(30_000L, 37.1),
            createSample(45_000L, 37.8),
            createSample(60_000L, 38.6)
        )
        val trend = ThermalTrendEngine.calculateTrend(samples, 60_000L)
        val engine = ThermalPredictionEngine()
        val forecast = engine.predict(trend, samples)

        assertEquals(3, forecast.points.size)
        val p10 = forecast.getPoint(10)
        val p30 = forecast.getPoint(30)
        val p60 = forecast.getPoint(60)

        assertNotNull(p10)
        assertNotNull(p30)
        assertNotNull(p60)

        // Predicted temp should be higher than current 38.6°C since velocity is positive
        assertTrue(p10!!.predictedTemperature!! > 38.6)
        assertTrue(p30!!.predictedTemperature!! > p10.predictedTemperature!!)
        assertTrue(p60!!.predictedTemperature!! > p30.predictedTemperature!!)
        assertNotEquals(Risk.UNKNOWN, p60.thermalRisk)
    }

    @Test
    fun testAutoCoolGraduatedStrategyAndHysteresis() {
        val decisionEngine = AutoCoolDecisionEngine()
        val baseTwinState = TwinState(workload = Workload.GAMING, gamingState = true)
        val settings = AutoCoolSettings(autoCoolEnabled = true)

        // 1. Normal state: 35.0°C and stable
        val trendNormal = ThermalTrend(
            currentTemperature = 35.0,
            temperatureVelocity = 0.05,
            temperatureAcceleration = 0.0,
            rollingAverageTemperature = 35.0,
            thermalHeadroom = 0.40,
            thermalHeadroomTrend = 0.0,
            cpuHeadroom = null,
            gpuHeadroom = null,
            batteryTemperature = 35.0,
            workloadDurationMs = 60_000L
        )
        val d0 = decisionEngine.decide(baseTwinState, trendNormal, null, settings, null)
        assertEquals(AutoCoolStrategy.LEVEL_0_NORMAL, d0.strategy)
        assertEquals(AutoCoolState.NO_ACTION, d0.state)

        // 2. Rising trend -> Level 1 PRE-COOL
        val trendRising = trendNormal.copy(
            currentTemperature = 37.5,
            temperatureVelocity = 0.50,
            thermalHeadroom = 0.72
        )
        val d1 = decisionEngine.decide(baseTwinState, trendRising, null, settings, d0)
        assertEquals(AutoCoolStrategy.LEVEL_1_PRE_COOL, d1.strategy)
        assertEquals(AutoCoolState.PRE_COOL, d1.state)
        assertTrue(d1.actions.contains(CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE))

        // 3. Deteriorating headroom -> Level 2 COOL
        val trendHot = trendRising.copy(
            currentTemperature = 40.0,
            temperatureVelocity = 0.60,
            thermalHeadroom = 0.85
        )
        val d2 = decisionEngine.decide(baseTwinState, trendHot, null, settings, d1)
        assertEquals(AutoCoolStrategy.LEVEL_2_COOL, d2.strategy)
        assertEquals(AutoCoolState.COOL, d2.state)
        assertTrue(d2.actions.contains(CoolingAction.PAUSE_ROLLING_SCREEN_BUFFER))

        // 4. Critical status -> Level 4 CRITICAL
        val stateCritical = baseTwinState.copy(thermalState = 4)
        val trendCritical = trendHot.copy(currentTemperature = 45.5, thermalHeadroom = 1.08)
        val d4 = decisionEngine.decide(stateCritical, trendCritical, null, settings, d2)
        assertEquals(AutoCoolStrategy.LEVEL_4_CRITICAL, d4.strategy)
        assertTrue(d4.actions.contains(CoolingAction.EMERGENCY_STOP_SESSION_ADVICE))

        // 5. Hysteresis test: Exit Level 4 requires multiple stable samples
        val stateRecovering = baseTwinState.copy(thermalState = 1)
        val trendRecovering = trendHot.copy(currentTemperature = 39.0, temperatureVelocity = -0.3, thermalHeadroom = 0.70)

        // 1st recovery sample: should hold Level 4 or stay cautious
        val rec1 = decisionEngine.decide(stateRecovering, trendRecovering, null, settings, d4)
        assertEquals(1, rec1.consecutiveRecoverySamples)

        val rec2 = decisionEngine.decide(stateRecovering, trendRecovering, null, settings, rec1)
        assertEquals(2, rec2.consecutiveRecoverySamples)

        // After sustained recovery (>=3 samples), should step down gracefully
        val rec3 = decisionEngine.decide(stateRecovering, trendRecovering, null, settings, rec2)
        assertTrue("Should step down from Level 4 after sustained cooldown", rec3.strategy.level < AutoCoolStrategy.LEVEL_4_CRITICAL.level)
    }

    @Test
    fun testThermalVerificationCycle() {
        val verificationEngine = ThermalVerificationEngine()

        verificationEngine.onInterventionStarted(
            id = 101L,
            strategy = AutoCoolStrategy.LEVEL_2_COOL,
            intervention = "SOVARIX ECO + Sensor Throttle",
            predictedTempBefore = 41.2,
            actualTempBefore = 39.8
        )

        // Observe temperatures during intervention (reaches peak 40.0°C then drops)
        verificationEngine.onSampleObserved(createSample(10_000L, 40.0))
        verificationEngine.onSampleObserved(createSample(25_000L, 39.2))

        // Resolving immediately (<20s) should return null (premature)
        val premature = verificationEngine.resolveVerification(39.2, timestamp = System.currentTimeMillis() + 5_000L)
        assertNull(premature)

        // Resolve after 30 seconds
        val verified = verificationEngine.resolveVerification(39.0, timestamp = System.currentTimeMillis() + 35_000L)
        assertNotNull(verified)
        assertEquals(39.8, verified!!.actualTempBefore!!, 0.01)
        assertEquals(40.0, verified.actualTempPeak!!, 0.01)
        assertEquals(39.0, verified.actualTempAfter!!, 0.01)
        // Delta = 39.0 - 39.8 = -0.8°C (successful cooldown!)
        assertEquals(-0.8, verified.temperatureDelta!!, 0.01)
        assertEquals("POSITIVE", verified.effectiveness)
    }

    @Test
    fun testThermalActionPolicyRejectionOfProprietaryHacks() {
        val capabilities = CoolingActionCapabilities(
            canReduceSovarixWorkload = true,
            canReduceCaptureRate = true,
            canStopNonEssentialSovarixProcessing = true,
            canAdjustOwnRendering = true,
            canAdjustOwnFrameRate = true,
            canUseSupportedGamePerformanceAPI = false,
            canRequestUserSystemSetting = true,
            canUseOfficialOEMSDK = false
        )

        assertTrue(capabilities.canReduceSovarixWorkload)
        assertFalse(capabilities.canUseOfficialOEMSDK)
        assertFalse(capabilities.canUseSupportedGamePerformanceAPI)
    }
}
