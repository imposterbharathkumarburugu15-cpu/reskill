package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class MotionEngineTest {

    private fun testSample() = Sample(
        elapsedMs = 15_000L,
        wallMs = 1_700_000_015_000L,
        batteryPct = 80.0,
        batteryC = 35.0,
        charging = false,
        currentUa = -600_000,
        thermalStatus = 0,
        headroom = 0.3,
        availableMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 8_000_000_000L,
        lowMemory = false,
        appPssKb = 25_000,
        processCpuMs = 800L,
        powerSave = false,
        interactive = true,
        workload = Workload.GAMING,
        collectionMs = 2.0
    )

    private fun testMotion(
        suddenRot: Boolean = false,
        suddenMotion: Boolean = false,
        intensity: Float = 0.2f
    ) = MotionSnapshot(
        timestamp = 1_700_000_015_000L,
        gyroAvailable = true,
        accelerometerAvailable = true,
        linearAccelerationAvailable = true,
        rotationVectorAvailable = true,
        gyroX = 0.1f, gyroY = 0.2f, gyroZ = 0.1f,
        angularVelocityMagnitude = if (suddenRot) 4.5f else 0.3f,
        gyroSpike = suddenRot,
        accelX = 0f, accelY = 9.8f, accelZ = 0.5f,
        accelerationMagnitude = if (suddenMotion) 18.2f else 9.8f,
        accelerationSpike = suddenMotion,
        linearAccelX = 0.1f, linearAccelY = 0.2f, linearAccelZ = 0.1f,
        linearAccelerationMagnitude = if (suddenMotion) 8.5f else 0.4f,
        movementIntensity = intensity,
        suddenRotationDetected = suddenRot,
        suddenMotionDetected = suddenMotion,
        confidence = 0.85f
    )

    @Test
    fun suddenRotationTriggersRapidRotationMoment() {
        val engine = GamingMomentEngine(debounceWindowMs = 5_000L)
        val s = testSample()
        val m = testMotion(suddenRot = true)

        val moment = engine.evaluate(
            nowWallMs = 1_000_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            motion = m,
            activeForecast = null,
            thermalSlopePerMin = null
        )

        assertNotNull(moment)
        assertEquals(MomentType.RAPID_ROTATION, moment?.momentType)
        assertTrue(moment!!.availableSignals.contains("ROTATION_SPIKE"))
        assertEquals(m, moment.motionSnapshot)
        assertTrue(moment.signalEvidence?.gyroConfidence ?: 0.0 > 0.8)
    }

    @Test
    fun combinedMotionAndAudioTriggersCombinedEvent() {
        val engine = GamingMomentEngine(debounceWindowMs = 5_000L)
        val s = testSample()
        val m = testMotion(suddenRot = true, intensity = 0.8f)

        val moment = engine.evaluate(
            nowWallMs = 1_000_000L,
            gamePackage = "Free Fire",
            sample = s,
            motion = m,
            activeForecast = null,
            thermalSlopePerMin = null,
            audioSpike = true
        )

        assertNotNull(moment)
        assertEquals(MomentType.COMBINED_GAME_EVENT, moment?.momentType)
        assertTrue(moment!!.availableSignals.contains("AUDIO_SPIKE"))
        assertTrue(moment.availableSignals.contains("ROTATION_SPIKE"))
    }

    @Test
    fun missingMotionSensorsDegradesGracefully() {
        val engine = GamingMomentEngine(debounceWindowMs = 5_000L)
        val s = testSample()

        // Null motion (sensor not available or not started)
        val moment = engine.evaluate(
            nowWallMs = 1_000_000L,
            gamePackage = "Free Fire",
            sample = s,
            motion = null,
            activeForecast = null,
            thermalSlopePerMin = null,
            audioSpike = true
        )

        assertNotNull(moment)
        assertEquals(MomentType.AUDIO_SPIKE, moment?.momentType)
        assertNull(moment?.motionSnapshot)
    }
}
