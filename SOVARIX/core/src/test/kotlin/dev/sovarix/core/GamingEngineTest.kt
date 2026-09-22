package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class GamingEngineTest {
    private fun testSample(
        elapsedMs: Long = 10_000L,
        thermal: Int = 0,
        tempC: Double = 34.0,
        lowMem: Boolean = false
    ) = Sample(
        elapsedMs = elapsedMs,
        wallMs = 1_700_000_000_000L + elapsedMs,
        batteryPct = 75.0,
        batteryC = tempC,
        charging = false,
        currentUa = -500_000,
        thermalStatus = thermal,
        headroom = 0.2,
        availableMemoryBytes = 3_000_000_000L,
        totalMemoryBytes = 8_000_000_000L,
        lowMemory = lowMem,
        appPssKb = 30_000,
        processCpuMs = 500L,
        powerSave = false,
        interactive = true,
        workload = Workload.GAMING,
        collectionMs = 2.0
    )

    @Test
    fun manualTriggerGeneratesConfirmedMoment() {
        val engine = GamingMomentEngine(debounceWindowMs = 5_000L)
        val s = testSample()
        val moment = engine.evaluate(
            nowWallMs = 1_000_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            activeForecast = null,
            thermalSlopePerMin = null,
            manualTrigger = true
        )

        assertNotNull(moment)
        assertEquals("com.dts.freefireth", moment?.gamePackage)
        assertEquals(MomentType.COMBINED_GAME_EVENT, moment?.momentType)
        assertEquals(0.95, moment!!.confidence, 0.01)
        assertEquals(s, moment.deviceState)
    }

    @Test
    fun debouncePreventsImmediateRetrigger() {
        val engine = GamingMomentEngine(debounceWindowMs = 5_000L)
        val s = testSample()

        val first = engine.evaluate(
            nowWallMs = 1_000_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            activeForecast = null,
            thermalSlopePerMin = null,
            audioSpike = true
        )
        assertNotNull(first)

        // Within 5s window without manual trigger
        val second = engine.evaluate(
            nowWallMs = 1_002_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            activeForecast = null,
            thermalSlopePerMin = null,
            audioSpike = true
        )
        assertNull(second)

        // After debounce window expires
        val third = engine.evaluate(
            nowWallMs = 1_006_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            activeForecast = null,
            thermalSlopePerMin = null,
            audioSpike = true
        )
        assertNotNull(third)
    }

    @Test
    fun thermalElevationTriggersThermalMoment() {
        val engine = GamingMomentEngine()
        val s = testSample(thermal = 3, tempC = 41.5)
        val moment = engine.evaluate(
            nowWallMs = 2_000_000L,
            gamePackage = "Free Fire",
            sample = s,
            activeForecast = null,
            thermalSlopePerMin = 0.5
        )

        assertNotNull(moment)
        assertEquals(MomentType.THERMAL_EVENT, moment?.momentType)
        assertTrue(moment!!.availableSignals.contains("THERMAL_ELEVATION"))
    }

    @Test
    fun whyEngineGeneratesCalibratedReport() {
        val s = testSample(thermal = 3, tempC = 41.5)
        val state = TwinState(
            latest = s,
            workload = Workload.GAMING,
            chargingState = false
        )
        val incident = Incident(
            id = 101L,
            timestamp = 2_000_000L,
            type = "THERMAL_EVENT",
            severity = Risk.ANOMALY,
            summary = "Thermal spike detected during gaming"
        )
        val report = WhyEngine.explain(incident, state)
        assertEquals("WHY DID PERFORMANCE CHANGE?", report.title)
        assertTrue(report.observedFacts.any { it.contains("Thermal response increased") })
        assertTrue(report.possibleContributors.any { it.title.contains("Sustained workload") && it.evidenceRating == "strong" })
        assertTrue(report.unknownFactors.isNotEmpty())
        assertNotNull(report.recommendedTest)
    }

    @Test
    fun gamingPerformanceEngineDetectsThermalEvent() {
        val s = testSample(thermal = 2, tempC = 39.0)
        val state = TwinState(
            latest = s,
            workload = Workload.GAMING
        )
        val anomaly = GamingPerformanceEngine.evaluate(
            state = state,
            sessionElapsedSec = 120L,
            baselineTemp = 34.0
        )
        assertNotNull(anomaly)
        assertEquals("THERMAL_EVENT", anomaly?.eventType)
        assertEquals(Risk.WATCH, anomaly?.severity)
    }

    @Test
    fun gameProfileRegistrySupportsMultiGameAgnosticProfiles() {
        val dreamCricket = GameProfileRegistry.findProfile("Dream Cricket")
        assertEquals(GameGenre.CRICKET, dreamCricket.genre)
        assertEquals("Dream Cricket", dreamCricket.gameName)

        val bgmi = GameProfileRegistry.findProfile("BGMI")
        assertEquals(GameGenre.BATTLE_ROYALE, bgmi.genre)

        val asphalt = GameProfileRegistry.findProfile("Asphalt Legends")
        assertEquals(GameGenre.RACING, asphalt.genre)

        val unknown = GameProfileRegistry.findProfile("some.random.customgame")
        assertEquals(GameGenre.UNKNOWN, unknown.genre)
        assertEquals("some.random.customgame", unknown.packageName)
    }
}
