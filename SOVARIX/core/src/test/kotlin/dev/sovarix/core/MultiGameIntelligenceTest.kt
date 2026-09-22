package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class MultiGameIntelligenceTest {

    private fun testSample(
        elapsedMs: Long = 10_000L,
        thermal: Int = 0,
        tempC: Double = 33.5,
        lowMem: Boolean = false
    ) = Sample(
        elapsedMs = elapsedMs,
        wallMs = 1_700_000_000_000L + elapsedMs,
        batteryPct = 80.0,
        batteryC = tempC,
        charging = false,
        currentUa = -450_000,
        thermalStatus = thermal,
        headroom = 0.3,
        availableMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 8_000_000_000L,
        lowMemory = lowMem,
        appPssKb = 25_000,
        processCpuMs = 400L,
        powerSave = false,
        interactive = true,
        workload = Workload.GAMING,
        collectionMs = 1.5
    )

    @Test
    fun gameProfileRegistryAccuratelyResolvesKnownGenres() {
        val freeFire = GameProfileRegistry.findProfile("com.dts.freefireth")
        assertEquals("Free Fire", freeFire.gameName)
        assertEquals(GameGenre.BATTLE_ROYALE, freeFire.genre)
        assertTrue(freeFire.highLoadWorkload)

        val bgmi = GameProfileRegistry.findProfile("com.pubg.imobile")
        assertEquals("BGMI", bgmi.gameName)
        assertEquals(GameGenre.BATTLE_ROYALE, bgmi.genre)

        val dreamCricket = GameProfileRegistry.findProfile("com.sporta.dreamcricket")
        assertEquals("Dream Cricket", dreamCricket.gameName)
        assertEquals(GameGenre.CRICKET, dreamCricket.genre)
        assertFalse(dreamCricket.highLoadWorkload)

        val racing = GameProfileRegistry.findProfile("com.gameloft.android.ANMP.GloftA9HM")
        assertEquals("Asphalt Legends", racing.gameName)
        assertEquals(GameGenre.RACING, racing.genre)

        val fps = GameProfileRegistry.findProfile("com.activision.callofduty.shooter")
        assertEquals("Call of Duty Mobile", fps.gameName)
        assertEquals(GameGenre.FPS, fps.genre)

        val sports = GameProfileRegistry.findProfile("com.ea.gp.fifamobile")
        assertEquals("EA Sports FC", sports.gameName)
        assertEquals(GameGenre.SPORTS, sports.genre)
    }

    @Test
    fun unknownGameDoesNotFabricateIdentityAndWorksSeamlessly() {
        // Must not fabricate identity when package or game is unrecognized
        val unknown = GameProfileRegistry.findProfile("com.indie.randomgame")
        assertEquals("Unknown Game", unknown.gameName)
        assertEquals("com.indie.randomgame", unknown.packageName)
        assertEquals(GameGenre.UNKNOWN, unknown.genre)

        val engine = GamingMomentEngine()
        val s = testSample()
        val motion = MotionSnapshot(
            gyroAvailable = true,
            accelerometerAvailable = true,
            suddenRotationDetected = true,
            movementIntensity = 0.75f,
            angularVelocityMagnitude = 4.2f
        )

        val moment = engine.evaluate(
            nowWallMs = 1_000_000L,
            gamePackage = "com.indie.randomgame",
            sample = s,
            motion = motion
        )

        assertNotNull("Architecture must work even when genre = UNKNOWN", moment)
        assertEquals("Unknown Game", moment?.gameName)
        assertEquals(GameGenre.UNKNOWN, moment?.gameGenre)
        assertEquals(MomentType.RAPID_ROTATION, moment?.eventType)
        assertNull(moment?.gameSpecificMetadata)
    }

    @Test
    fun doesNotFabricateSemanticLabelsWithoutEvidence() {
        val engine = GamingMomentEngine()
        val s = testSample()

        // Free Fire event with audio spike
        val ffMoment = engine.evaluate(
            nowWallMs = 2_000_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            audioSpike = true
        )

        assertNotNull(ffMoment)
        // Must be generic event type, NOT a fabricated "KILL" or "HEADSHOT"
        assertEquals(MomentType.AUDIO_SPIKE, ffMoment?.eventType)
        assertNotEquals("KILL", ffMoment?.eventType)
        assertNotEquals("HEADSHOT", ffMoment?.eventType)

        // Dream Cricket event with visual event
        engine.reset()
        val cricketMoment = engine.evaluate(
            nowWallMs = 3_000_000L,
            gamePackage = "com.sporta.dreamcricket",
            sample = s,
            visualEvent = true
        )

        assertNotNull(cricketMoment)
        // Must be generic event type, NOT a fabricated "SIX" or "WICKET"
        assertEquals(MomentType.VISUAL_CHANGE, cricketMoment?.eventType)
        assertNotEquals("SIX", cricketMoment?.eventType)
        assertNotEquals("WICKET", cricketMoment?.eventType)
    }

    @Test
    fun cricketSessionAttachesCricketMetadataWhenAvailable() {
        val engine = GamingMomentEngine()
        val s = testSample()
        val motion = MotionSnapshot(
            accelerometerAvailable = true,
            suddenMotionDetected = true,
            movementIntensity = 0.70f,
            accelerationMagnitude = 18.5f
        )

        val moment = engine.evaluate(
            nowWallMs = 4_000_000L,
            gamePackage = "com.sporta.dreamcricket",
            sample = s,
            motion = motion,
            visualEvent = true
        )

        assertNotNull(moment)
        assertEquals("Dream Cricket", moment?.gameName)
        assertEquals(GameGenre.CRICKET, moment?.gameGenre)
        assertEquals(MomentType.COMBINED_GAME_EVENT, moment?.eventType)

        val metadata = moment?.gameSpecificMetadata
        assertTrue(metadata is GameSpecificMetadata.Cricket)
        val cricketMeta = metadata as GameSpecificMetadata.Cricket
        assertEquals("BAT_BALL_CONTACT_ZONE", cricketMeta.ballEvent)
    }

    @Test
    fun battleRoyaleSessionAttachesCombatIntensityMetadata() {
        val engine = GamingMomentEngine()
        val s = testSample()
        val motion = MotionSnapshot(
            gyroAvailable = true,
            accelerometerAvailable = true,
            movementIntensity = 0.88f,
            angularVelocityMagnitude = 5.6f,
            gyroSpike = true
        )

        val moment = engine.evaluate(
            nowWallMs = 5_000_000L,
            gamePackage = "com.dts.freefireth",
            sample = s,
            motion = motion,
            audioSpike = true
        )

        assertNotNull(moment)
        assertEquals("Free Fire", moment?.gameName)
        assertEquals(GameGenre.BATTLE_ROYALE, moment?.gameGenre)
        assertEquals(MomentType.COMBINED_GAME_EVENT, moment?.eventType)

        val metadata = moment?.gameSpecificMetadata
        assertTrue(metadata is GameSpecificMetadata.BattleRoyale)
        val brMeta = metadata as GameSpecificMetadata.BattleRoyale
        assertEquals(0.88f, brMeta.combatIntensity ?: 0f, 0.01f)
        assertEquals("AUDIO_SPIKE", brMeta.audioEvent)
    }

    @Test
    fun gameSessionLifecycleTracksMetadataAndCapabilities() {
        val caps = GamingCaptureCapabilities(
            audioPlaybackCaptureSupported = true,
            screenCaptureSupported = true,
            hapticObservationSupported = false,
            gameDetectionSupported = true,
            recordingSupported = true
        )

        val session = GameSession(
            sessionId = "sess_123",
            packageName = "com.gameloft.android.ANMP.GloftA9HM",
            gameName = "Asphalt Legends",
            sessionStart = 1_000_000L,
            captureCapabilities = caps,
            genre = GameGenre.RACING
        )

        assertEquals("sess_123", session.sessionId)
        assertEquals(GameGenre.RACING, session.genre)
        assertTrue(session.captureCapabilities.screenCaptureSupported)
        assertNull(session.sessionEnd)

        val endedSession = session.copy(sessionEnd = 1_060_000L)
        assertEquals(1_060_000L, endedSession.sessionEnd)
    }
}
