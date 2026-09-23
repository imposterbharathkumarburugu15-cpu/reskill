package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class MultilingualIntelligenceTest {

    @Test
    fun testMultilingualSimulationQueriesMapToSameOutcome() {
        val state = TwinState(
            running = true,
            temperature = 38.0,
            battery = 80.0,
            dna = DeviceDNA(
                behaviorModel = DeviceBehaviorModel(
                    gamingSessionsCount = 5,
                    totalObservations = 50,
                    recordingThermalDeltaCPerMin = 0.25,
                    chargingGamingThermalMultiplier = 1.40
                )
            )
        )

        // 1. Gaming for an hour in English, Telugu, and Hindi
        val outEn = SimulationEngine.simulateQuery(state, "What happens if I game for another hour?")
        val outTe = SimulationEngine.simulateQuery(state, "ఇంకో గంట గేమ్ ఆడితే ఏమవుతుంది?")
        val outHi = SimulationEngine.simulateQuery(state, "अगर मैं एक घंटे और गेम खेलूं तो क्या होगा?")

        assertTrue(outEn is SimulationResult)
        assertTrue(outTe is SimulationResult)
        assertTrue(outHi is SimulationResult)

        val resEn = outEn as SimulationResult
        val resTe = outTe as SimulationResult
        val resHi = outHi as SimulationResult

        // All should project the same delta and scenarios
        assertEquals(resEn.scenarios.size, resTe.scenarios.size)
        assertEquals(resEn.scenarios.size, resHi.scenarios.size)
        assertEquals(
            resEn.scenarios[1].predictedTemperatureC,
            resTe.scenarios[1].predictedTemperatureC
        )
        assertEquals(
            resEn.scenarios[1].predictedTemperatureC,
            resHi.scenarios[1].predictedTemperatureC
        )

        // 2. Screen recording in English, Telugu, and Hindi
        val recEn = SimulationEngine.simulateQuery(state, "What if I record while gaming?")
        val recTe = SimulationEngine.simulateQuery(state, "గేమ్ ఆడుతూ రికార్డ్ చేస్తే ఏమవుతుంది?")
        val recHi = SimulationEngine.simulateQuery(state, "गेमिंग के दौरान रिकॉर्ड करें तो क्या होगा?")

        assertTrue(recEn is SimulationResult)
        assertTrue(recTe is SimulationResult)
        assertTrue(recHi is SimulationResult)

        // 3. Charging while gaming in English, Telugu, and Hindi
        val chgEn = SimulationEngine.simulateQuery(state, "What if I charge while gaming?")
        val chgTe = SimulationEngine.simulateQuery(state, "ఛార్జింగ్ పెట్టి గేమ్ ఆడితే ఏమవుతుంది?")
        val chgHi = SimulationEngine.simulateQuery(state, "चार्ज करते हुए गेम खेलें तो क्या होगा?")

        assertTrue(chgEn is SimulationResult)
        assertTrue(chgTe is SimulationResult)
        assertTrue(chgHi is SimulationResult)

        // 4. Reduce workload in English, Telugu, and Hindi
        val redEn = SimulationEngine.simulateQuery(state, "What if I reduce the workload?")
        val redTe = SimulationEngine.simulateQuery(state, "వర్క్‌లోడ్ తగ్గిస్తే ఏమవుతుంది?")
        val redHi = SimulationEngine.simulateQuery(state, "वर्कलोड कम करें तो क्या होगा?")

        assertTrue(redEn is SimulationResult)
        assertTrue(redTe is SimulationResult)
        assertTrue(redHi is SimulationResult)
    }

    @Test
    fun testMultilingualLocalAIIntentParsing() {
        // Telugu diagnostic: "నా ఫోన్ ఎందుకు వేడెక్కుతోంది?" (Why is my phone getting hot?)
        val intentTe = LocalAIEngine.parseIntent("నా ఫోన్ ఎందుకు వేడెక్కుతోంది?")
        assertEquals(LocalAIIntentType.EXPLAIN_ANOMALY, intentTe.type)

        // Hindi diagnostic: "मेरा फोन इतना गर्म क्यों हो रहा है?" (Why is my phone getting so hot?)
        val intentHi = LocalAIEngine.parseIntent("मेरा फोन इतना गर्म क्यों हो रहा है?")
        assertEquals(LocalAIIntentType.EXPLAIN_ANOMALY, intentHi.type)

        // Telugu DNA: "నా ఫోన్ ప్రవర్తన ఏమిటి?" (What is my phone's behavior/DNA?)
        val intentDnaTe = LocalAIEngine.parseIntent("నా ఫోన్ ప్రవర్తన ఎలా ఉంది?")
        assertEquals(LocalAIIntentType.EXPLAIN_DNA, intentDnaTe.type)

        // Hindi Autopilot: "ऑटोपायलट का क्या लक्ष्य है?" (What is the autopilot goal?)
        val intentAutoHi = LocalAIEngine.parseIntent("ऑटोपायलट का क्या लक्ष्य है?")
        assertEquals(LocalAIIntentType.EXPLAIN_AUTOPILOT, intentAutoHi.type)
    }

    @Test
    fun testMultilingualDeviceDNAInsights() {
        val untrained = DeviceBehaviorModel(totalObservations = 5, gamingSessionsCount = 0)
        val pendingEn = untrained.generateInsights("en")
        val pendingTe = untrained.generateInsights("te")
        val pendingHi = untrained.generateInsights("hi")

        assertTrue(pendingEn.first().contains("Still learning"))
        assertTrue(pendingTe.first().contains("అర్థం చేసుకుంటోంది"))
        assertTrue(pendingHi.first().contains("समझा जा रहा है"))

        val trained = DeviceBehaviorModel(
            totalObservations = 40,
            gamingSessionsCount = 5,
            gamingDurationThermalCurve = mapOf(10 to 1.5, 30 to 4.5),
            recordingSessionsCount = 3,
            recordingThermalDeltaCPerMin = 0.22,
            chargingGamingSessionsCount = 3,
            chargingGamingThermalMultiplier = 1.35
        )

        val insightsEn = trained.generateInsights("en")
        val insightsTe = trained.generateInsights("te")
        val insightsHi = trained.generateInsights("hi")

        assertEquals(insightsEn.size, insightsTe.size)
        assertEquals(insightsEn.size, insightsHi.size)

        assertTrue(insightsTe.any { it.contains("ఎక్కువ సమయం గేమింగ్") })
        assertTrue(insightsHi.any { it.contains("लंबे गेमिंग सेशन") })
    }

    @Test
    fun testVoiceInterfacesMock() {
        var speechTriggered = false
        val mockSpeech = object : SpeechOutputProvider {
            override fun isSpeechAvailable(languageTag: String): Boolean = true
            override fun speak(text: String, languageTag: String) {
                speechTriggered = true
            }
            override fun stop() {}
        }

        mockSpeech.speak("మీ ఫోన్ సురక్షితంగా ఉంది", "te")
        assertTrue(speechTriggered)
    }
}
