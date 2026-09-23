package dev.sovarix.core

import kotlin.math.*

/**
 * Visual scores for the 4 core dimensions of Device DNA 2.0 [0.0..1.0].
 */
data class DeviceDNAScores(
    val thermalResponse: Double = 0.5,
    val gamingEndurance: Double = 0.5,
    val recoveryBehavior: Double = 0.5,
    val batteryResponse: Double = 0.5
)

/**
 * Pre-repair vs Post-repair baseline for forensic verification.
 */
data class RepairBaselineComparison(
    val baselineCapturedAt: Long = 0L,
    val baselineHeatingRateCPerMin: Double = 0.0,
    val baselineCoolingRateCPerMin: Double = 0.0,
    val baselineBatteryDrainPctPerHour: Double = 0.0,
    val postRepairCapturedAt: Long? = null,
    val postRepairHeatingRateCPerMin: Double? = null,
    val postRepairCoolingRateCPerMin: Double? = null,
    val postRepairBatteryDrainPctPerHour: Double? = null,
    val notes: String = "Forensic baseline comparison across hardware repair events."
) {
    val isVerified: Boolean get() = postRepairCapturedAt != null
    val evaluationSummary: String get() {
        if (!isVerified || postRepairHeatingRateCPerMin == null) {
            return "Awaiting post-repair session observations."
        }
        val heatDelta = postRepairHeatingRateCPerMin - baselineHeatingRateCPerMin
        return if (heatDelta <= 0.05) {
            "Post-repair thermal behavior is stable and matches or improves upon baseline (${String.format(java.util.Locale.US, "%+.2f", heatDelta)}°C/min)."
        } else {
            "Post-repair thermal rise exceeds baseline by ${String.format(java.util.Locale.US, "+%.2f", heatDelta)}°C/min. Recommend diagnostic inspection."
        }
    }
}

/**
 * Compact, persistent empirical model of smartphone behavior across physical sessions.
 * Never stores unbounded raw telemetry streams.
 * Learns real relationships:
 * - Workload -> Heating rate
 * - Workload -> Battery drain
 * - Gaming duration -> Thermal trajectory
 * - Screen recording -> Added thermal load
 * - Charging while gaming -> Heating multiplier
 * - Intervention -> Cooling recovery rate
 */
data class DeviceBehaviorModel(
    // 1. Workload -> Temperature response (°C / minute average heating rate)
    val workloadHeatingRates: Map<Workload, Double> = mapOf(
        Workload.IDLE to 0.02,
        Workload.EVERYDAY to 0.08,
        Workload.GAMING to 0.28,
        Workload.RECOVERY to -0.22
    ),

    // 2. Workload -> Battery drain (% / hour)
    val workloadBatteryDrainRates: Map<Workload, Double> = mapOf(
        Workload.IDLE to 1.5,
        Workload.EVERYDAY to 4.5,
        Workload.GAMING to 16.0,
        Workload.RECOVERY to 6.0
    ),

    // 3. Workload -> Memory utilization fraction (0.0 to 1.0)
    val workloadMemoryFractions: Map<Workload, Double> = mapOf(
        Workload.IDLE to 0.45,
        Workload.EVERYDAY to 0.60,
        Workload.GAMING to 0.75,
        Workload.RECOVERY to 0.58
    ),

    // 4. Gaming duration -> Thermal rise curve (delta °C observed after 10m, 30m, 60m)
    val gamingDurationThermalCurve: Map<Int, Double> = mapOf(
        10 to 1.8,
        30 to 4.2,
        60 to 6.8
    ),

    // 5. Recording while gaming -> Delta heating rate added (°C / minute)
    val recordingThermalDeltaCPerMin: Double? = null,

    // 6. Charging while gaming -> Heating acceleration multiplier
    val chargingGamingThermalMultiplier: Double? = null,

    // 7. Auto-Cool / Mitigation -> Empirical cooling recovery rate (°C / minute)
    val interventionRecoveryRateCPerMin: Double? = null,

    // Observation volume counters to ensure statistical confidence before asserting insights
    val totalObservations: Int = 0,
    val gamingSessionsCount: Int = 0,
    val recordingSessionsCount: Int = 0,
    val chargingGamingSessionsCount: Int = 0,
    val interventionCount: Int = 0,
    val lastUpdatedWallMs: Long = 0L
) {

    /**
     * Compute visual DNA scores [0.0..1.0] from empirical behavior.
     */
    fun computeDNAScores(): DeviceDNAScores {
        if (totalObservations < 15) {
            return DeviceDNAScores(0.5, 0.5, 0.5, 0.5)
        }

        // Thermal response: lower gaming heating rate = better score
        val gamingHeat = workloadHeatingRates[Workload.GAMING] ?: 0.28
        val thermalScore = (1.0 - (gamingHeat - 0.10) / 0.40).coerceIn(0.1, 0.95)

        // Gaming endurance: lower 30m/60m thermal saturation = better score
        val tempAt30 = gamingDurationThermalCurve[30] ?: 4.2
        val enduranceScore = (1.0 - (tempAt30 - 2.0) / 5.0).coerceIn(0.1, 0.95)

        // Recovery behavior: faster cooling in RECOVERY / intervention = better score
        val recRate = abs(interventionRecoveryRateCPerMin ?: workloadHeatingRates[Workload.RECOVERY] ?: 0.22)
        val recoveryScore = (recRate / 0.45).coerceIn(0.1, 0.95)

        // Battery response: lower gaming battery drain = better score
        val battDrain = workloadBatteryDrainRates[Workload.GAMING] ?: 16.0
        val batteryScore = (1.0 - (battDrain - 8.0) / 20.0).coerceIn(0.1, 0.95)

        return DeviceDNAScores(
            thermalResponse = thermalScore,
            gamingEndurance = enduranceScore,
            recoveryBehavior = recoveryScore,
            batteryResponse = batteryScore
        )
    }

    /**
     * Generate evidence-backed natural language insights in the requested language (en, te, hi).
     * Never fabricates patterns. Returns localized "Still learning your device" if insufficient data.
     */
    fun generateInsights(language: String = "en"): List<String> {
        val lang = language.lowercase().take(2)
        val insights = mutableListOf<String>()

        if (totalObservations < 25 || gamingSessionsCount < 2) {
            val pendingMsg = when (lang) {
                "te" -> "మీ పరికరాన్ని ఇంకా అర్థం చేసుకుంటోంది. బేస్‌లైన్ ప్రవర్తనను గుర్తించడానికి SOVARIXకి 2+ గేమింగ్ సెషన్‌లు అవసరం."
                "hi" -> "आपके डिवाइस को अभी समझा जा रहा है। बेसलाइन व्यवहार स्थापित करने के लिए SOVARIX को 2+ गेमिंग सेशन की आवश्यकता है।"
                else -> "Still learning your device. SOVARIX needs 2+ gaming sessions to establish baseline behaviors."
            }
            insights += pendingMsg
            return insights
        }

        // 1. Prolonged gaming thermal trajectory insight
        val tempAt30 = gamingDurationThermalCurve[30] ?: 0.0
        val tempAt10 = gamingDurationThermalCurve[10] ?: 0.0
        if (tempAt30 > 0 && tempAt10 > 0) {
            val delta = tempAt30 - tempAt10
            val deltaStr = String.format(java.util.Locale.US, "%.1f", delta)
            if (delta >= 2.0) {
                insights += when (lang) {
                    "te" -> "మీ ఫోన్ సాధారణంగా ఎక్కువ సమయం గేమింగ్ చేసిన తర్వాత (>20 నిమిషాలు) వేగంగా వేడెక్కుతుంది (+$deltaStr°C)."
                    "hi" -> "आपका फोन आमतौर पर लंबे गेमिंग सेशन (>20 मिनट) के बाद तेजी से गर्म होता है (+$deltaStr°C)।"
                    else -> "Your device experiences accelerated warming after sustained sessions (>20 min, +$deltaStr°C)."
                }
            } else {
                insights += when (lang) {
                    "te" -> "ఎక్కువ సమయం గేమింగ్ చేసినప్పటికీ మీ ఫోన్ ఉష్ణోగ్రత స్థిరంగా ఉంటుంది."
                    "hi" -> "लंबे गेमिंग सेशन के दौरान आपके डिवाइस का तापमान स्थिर रहता है।"
                    else -> "Thermal dissipation remains steady across prolonged gaming sessions."
                }
            }
        }

        // 2. Screen recording impact insight
        if (recordingSessionsCount >= 2 && recordingThermalDeltaCPerMin != null) {
            val recRateStr = String.format(java.util.Locale.US, "%.2f", recordingThermalDeltaCPerMin)
            if (recordingThermalDeltaCPerMin > 0.05) {
                insights += when (lang) {
                    "te" -> "గేమింగ్ సమయంలో స్క్రీన్ రికార్డ్ చేయడం వల్ల సాధారణం కంటే సుమారు +$recRateStr°C/నిమిషం అదనపు వేడి చేరుతుంది."
                    "hi" -> "गेमप्ले रिकॉर्ड करने से सामान्य गेमिंग की तुलना में लगभग +$recRateStr°C/मिनट अतिरिक्त थर्मल लोड बढ़ता है।"
                    else -> "Screen recording increases thermal load by ~+$recRateStr°C/min compared with normal gaming."
                }
            } else {
                insights += when (lang) {
                    "te" -> "స్క్రీన్ రికార్డింగ్ వల్ల మీ హార్డ్‌వేర్‌పై అదనపు వేడి భారం పడటం లేదు."
                    "hi" -> "स्क्रीन रिकॉर्डिंग से आपके हार्डवेयर पर कोई विशेष थर्मल लोड नहीं पड़ता।"
                    else -> "Screen recording introduces negligible thermal overhead on your hardware."
                }
            }
        }

        // 3. Charging + Gaming insight
        if (chargingGamingSessionsCount >= 2 && chargingGamingThermalMultiplier != null) {
            if (chargingGamingThermalMultiplier >= 1.25) {
                val pct = ((chargingGamingThermalMultiplier - 1.0) * 100).toInt()
                insights += when (lang) {
                    "te" -> "ఛార్జింగ్ పెట్టి గేమ్ ఆడటం వల్ల ఉష్ణోగ్రత సుమారు +$pct% వేగంగా పెరుగుతుంది."
                    "hi" -> "चार्ज करते हुए गेम खेलने से तापमान लगभग +$pct% तेजी से बढ़ता है।"
                    else -> "Charging while gaming accelerates thermal accumulation by approximately +$pct%."
                }
            }
        }

        // 4. Auto-Cool recovery efficacy
        if (interventionCount >= 2 && interventionRecoveryRateCPerMin != null) {
            val recCoolStr = String.format(java.util.Locale.US, "%.2f", abs(interventionRecoveryRateCPerMin))
            insights += when (lang) {
                "te" -> "థర్మల్ షీల్డ్ సక్రియమైనప్పుడు మీ ఫోన్ నిమిషానికి $recCoolStr°C చొప్పున వేగంగా చల్లబడుతుంది."
                "hi" -> "थर्मल शील्ड सक्रिय होने पर आपका फोन आमतौर पर $recCoolStr°C/मिनट की दर से ठंडा होता है।"
                else -> "Thermal Shield intervention typically restores cool recovery at $recCoolStr°C/min."
            }
        }

        if (insights.isEmpty()) {
            insights += when (lang) {
                "te" -> "పరికరం అన్ని పనుల్లోనూ సాధారణ ఉష్ణోగ్రత పరిధిలోనే పనిచేస్తోంది."
                "hi" -> "डिवाइस का व्यवहार सभी वर्कलोड्स में सामान्य सीमा के भीतर है।"
                else -> "Device behavior is within expected nominal baselines for all monitored workloads."
            }
        }

        return insights
    }

    /**
     * Ingests a completed session and updates empirical distributions using rolling exponential moving averages.
     */
    fun updateWithSession(
        sessionWorkload: Workload,
        durationMinutes: Double,
        startTempC: Double?,
        endTempC: Double?,
        startBatteryPct: Double?,
        endBatteryPct: Double?,
        wasRecording: Boolean,
        wasCharging: Boolean,
        interventionApplied: Boolean,
        postInterventionCoolingRate: Double?
    ): DeviceBehaviorModel {
        if (durationMinutes < 1.0) return this

        val alpha = 0.25 // Smooth rolling adaptation

        // Update heating rate
        val newHeatingRates = workloadHeatingRates.toMutableMap()
        if (startTempC != null && endTempC != null) {
            val observedRate = (endTempC - startTempC) / durationMinutes
            val currentRate = newHeatingRates[sessionWorkload] ?: observedRate
            newHeatingRates[sessionWorkload] = currentRate * (1.0 - alpha) + observedRate * alpha
        }

        // Update battery drain rate (%/hour)
        val newBatteryRates = workloadBatteryDrainRates.toMutableMap()
        if (startBatteryPct != null && endBatteryPct != null && !wasCharging) {
            val observedDrain = ((startBatteryPct - endBatteryPct) / durationMinutes) * 60.0
            if (observedDrain >= 0.0) {
                val currentDrain = newBatteryRates[sessionWorkload] ?: observedDrain
                newBatteryRates[sessionWorkload] = currentDrain * (1.0 - alpha) + observedDrain * alpha
            }
        }

        // Update duration curve if gaming
        val newCurve = gamingDurationThermalCurve.toMutableMap()
        if (sessionWorkload == Workload.GAMING && startTempC != null && endTempC != null) {
            val deltaTemp = endTempC - startTempC
            if (durationMinutes in 8.0..15.0) {
                newCurve[10] = (newCurve[10] ?: deltaTemp) * (1.0 - alpha) + deltaTemp * alpha
            } else if (durationMinutes in 25.0..35.0) {
                newCurve[30] = (newCurve[30] ?: deltaTemp) * (1.0 - alpha) + deltaTemp * alpha
            } else if (durationMinutes >= 50.0) {
                newCurve[60] = (newCurve[60] ?: deltaTemp) * (1.0 - alpha) + deltaTemp * alpha
            }
        }

        // Update recording delta
        var newRecordingDelta = recordingThermalDeltaCPerMin
        var newRecCount = recordingSessionsCount
        if (sessionWorkload == Workload.GAMING && wasRecording && startTempC != null && endTempC != null) {
            val normalGamingRate = newHeatingRates[Workload.GAMING] ?: 0.28
            val observedRate = (endTempC - startTempC) / durationMinutes
            val delta = (observedRate - normalGamingRate).coerceAtLeast(0.0)
            newRecordingDelta = if (newRecordingDelta == null) delta else (newRecordingDelta * (1.0 - alpha) + delta * alpha)
            newRecCount++
        }

        // Update charging gaming multiplier
        var newChargingMult = chargingGamingThermalMultiplier
        var newChargingCount = chargingGamingSessionsCount
        if (sessionWorkload == Workload.GAMING && wasCharging && startTempC != null && endTempC != null) {
            val normalGamingRate = (newHeatingRates[Workload.GAMING] ?: 0.28).coerceAtLeast(0.05)
            val observedRate = ((endTempC - startTempC) / durationMinutes).coerceAtLeast(0.05)
            val mult = observedRate / normalGamingRate
            newChargingMult = if (newChargingMult == null) mult else (newChargingMult * (1.0 - alpha) + mult * alpha)
            newChargingCount++
        }

        // Update intervention recovery rate
        var newInterventionRate = interventionRecoveryRateCPerMin
        var newInterventionCount = interventionCount
        if (interventionApplied && postInterventionCoolingRate != null) {
            val rate = abs(postInterventionCoolingRate)
            newInterventionRate = if (newInterventionRate == null) rate else (newInterventionRate * (1.0 - alpha) + rate * alpha)
            newInterventionCount++
        }

        return copy(
            workloadHeatingRates = newHeatingRates,
            workloadBatteryDrainRates = newBatteryRates,
            gamingDurationThermalCurve = newCurve,
            recordingThermalDeltaCPerMin = newRecordingDelta,
            chargingGamingThermalMultiplier = newChargingMult,
            interventionRecoveryRateCPerMin = newInterventionRate,
            totalObservations = totalObservations + 1,
            gamingSessionsCount = if (sessionWorkload == Workload.GAMING) gamingSessionsCount + 1 else gamingSessionsCount,
            recordingSessionsCount = newRecCount,
            chargingGamingSessionsCount = newChargingCount,
            interventionCount = newInterventionCount,
            lastUpdatedWallMs = System.currentTimeMillis()
        )
    }
}
