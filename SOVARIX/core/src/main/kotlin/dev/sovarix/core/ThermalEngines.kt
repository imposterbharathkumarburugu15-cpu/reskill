package dev.sovarix.core

import kotlin.math.*

/**
 * Section 4: Real Thermal Trend Engine.
 * Calculates current temperature, velocity, acceleration, rolling average, and headroom trends.
 */
object ThermalTrendEngine {

    fun calculateTrend(
        samples: List<Sample>,
        workloadDurationMs: Long = 0L,
        recentInterventions: List<String> = emptyList()
    ): ThermalTrend {
        val validSamples = samples.filter { it.batteryC != null && it.batteryC.isFinite() }
        if (validSamples.isEmpty()) {
            return ThermalTrend(
                currentTemperature = null,
                temperatureVelocity = 0.0,
                temperatureAcceleration = 0.0,
                rollingAverageTemperature = null,
                thermalHeadroom = null,
                thermalHeadroomTrend = null,
                cpuHeadroom = null,
                gpuHeadroom = null,
                batteryTemperature = null,
                workloadDurationMs = workloadDurationMs,
                recentInterventions = recentInterventions,
                sampleCount = 0
            )
        }

        val latest = validSamples.last()
        val currentTemp = latest.batteryC
        val currentHeadroom = latest.headroom

        // Rolling average across available window (up to last 10 samples)
        val rollingWindow = validSamples.takeLast(10)
        val rollingAvg = rollingWindow.mapNotNull { it.batteryC }.average()

        if (validSamples.size < 2) {
            return ThermalTrend(
                currentTemperature = currentTemp,
                temperatureVelocity = 0.0,
                temperatureAcceleration = 0.0,
                rollingAverageTemperature = rollingAvg,
                thermalHeadroom = currentHeadroom,
                thermalHeadroomTrend = null,
                cpuHeadroom = null,
                gpuHeadroom = null,
                batteryTemperature = currentTemp,
                workloadDurationMs = workloadDurationMs,
                recentInterventions = recentInterventions,
                sampleCount = 1
            )
        }

        // Calculate velocity (slope per minute) over the window (preferring last 60s)
        val windowForVelocity = validSamples.takeLast(8)
        val firstInWindow = windowForVelocity.first()
        val dtMin = (latest.elapsedMs - firstInWindow.elapsedMs) / 60_000.0

        val velocity = if (dtMin >= 0.1 && firstInWindow.batteryC != null && currentTemp != null) {
            (currentTemp - firstInWindow.batteryC) / dtMin
        } else {
            0.0
        }

        // Calculate acceleration: velocity change over two consecutive sub-windows
        val acceleration = if (validSamples.size >= 4) {
            val midIndex = validSamples.size / 2
            val firstHalf = validSamples.subList(0, midIndex)
            val secondHalf = validSamples.subList(midIndex, validSamples.size)

            val dt1 = (firstHalf.last().elapsedMs - firstHalf.first().elapsedMs) / 60_000.0
            val v1 = if (dt1 >= 0.05 && firstHalf.first().batteryC != null && firstHalf.last().batteryC != null) {
                (firstHalf.last().batteryC!! - firstHalf.first().batteryC!!) / dt1
            } else 0.0

            val dt2 = (secondHalf.last().elapsedMs - secondHalf.first().elapsedMs) / 60_000.0
            val v2 = if (dt2 >= 0.05 && secondHalf.first().batteryC != null && secondHalf.last().batteryC != null) {
                (secondHalf.last().batteryC!! - secondHalf.first().batteryC!!) / dt2
            } else 0.0

            val midDt = (secondHalf.last().elapsedMs - firstHalf.last().elapsedMs) / 60_000.0
            if (midDt >= 0.05) (v2 - v1) / midDt else 0.0
        } else {
            0.0
        }

        // Thermal headroom trend (Δheadroom / min)
        val headroomSamples = validSamples.filter { it.headroom != null && it.headroom.isFinite() }
        val headroomTrend = if (headroomSamples.size >= 2) {
            val hFirst = headroomSamples.first()
            val hLast = headroomSamples.last()
            val hDt = (hLast.elapsedMs - hFirst.elapsedMs) / 60_000.0
            if (hDt >= 0.1) (hLast.headroom!! - hFirst.headroom!!) / hDt else null
        } else null

        return ThermalTrend(
            currentTemperature = currentTemp,
            temperatureVelocity = velocity,
            temperatureAcceleration = acceleration,
            rollingAverageTemperature = rollingAvg,
            thermalHeadroom = currentHeadroom,
            thermalHeadroomTrend = headroomTrend,
            cpuHeadroom = null,
            gpuHeadroom = null,
            batteryTemperature = currentTemp,
            workloadDurationMs = workloadDurationMs,
            recentInterventions = recentInterventions,
            sampleCount = validSamples.size
        )
    }
}

/**
 * Section 5: SOVARIX Lightweight Thermal Prediction Engine.
 * Predicts temperatures at 10s, 30s, and 60s horizons using online regression and trend extrapolation.
 * Never uses an LLM for numerical forecasting.
 */
class ThermalPredictionEngine {

    fun predict(
        trend: ThermalTrend,
        history: List<Sample>,
        timestamp: Long = System.currentTimeMillis()
    ): ThermalForecastResult {
        val currentTemp = trend.currentTemperature
        val sampleCount = trend.sampleCount

        // Need at least 3 valid samples to attempt prediction
        if (currentTemp == null || sampleCount < 3) {
            val uncalibratedPoints = listOf(10, 30, 60).map { sec ->
                ThermalForecastPoint(
                    horizonSeconds = sec,
                    predictedTemperature = currentTemp,
                    confidence = ThermalConfidence.LOW,
                    thermalRisk = Risk.UNKNOWN,
                    modelVersion = "v1.0-insufficient-data"
                )
            }
            return ThermalForecastResult(timestamp, uncalibratedPoints, "Collecting baseline observations (<3 samples)")
        }

        val v = trend.temperatureVelocity // °C / min
        val a = trend.temperatureAcceleration // °C / min²

        // Real calibrated confidence rating based on observation duration and sample depth
        val confidence = when {
            sampleCount >= 10 && history.last().elapsedMs - history.first().elapsedMs >= 60_000L -> ThermalConfidence.HIGH
            sampleCount >= 5 && history.last().elapsedMs - history.first().elapsedMs >= 30_000L -> ThermalConfidence.MEDIUM
            else -> ThermalConfidence.LOW
        }

        val horizons = listOf(10, 30, 60)
        val points = horizons.map { seconds ->
            val tMinutes = seconds / 60.0
            // Taylor expansion with damping factor to avoid over-predicting exponential runaways
            val damping = exp(-0.3 * tMinutes)
            val deltaT = (v * tMinutes + 0.5 * a * tMinutes.pow(2)) * damping
            val predicted = (currentTemp + deltaT).coerceIn(-10.0, 75.0)

            val risk = when {
                predicted >= 43.0 || (trend.thermalHeadroom ?: 0.0) >= 0.95 -> Risk.ANOMALY
                predicted >= 39.5 || v >= 0.45 || (trend.thermalHeadroom ?: 0.0) >= 0.80 -> Risk.WATCH
                else -> Risk.NORMAL
            }

            ThermalForecastPoint(
                horizonSeconds = seconds,
                predictedTemperature = predicted,
                confidence = confidence,
                thermalRisk = risk,
                modelVersion = "v1.0-polynomial-damping"
            )
        }

        return ThermalForecastResult(
            timestamp = timestamp,
            points = points,
            modelDescription = "Empirical polynomial extrapolation with velocity damping"
        )
    }
}

/**
 * Section 6, 10, 11, 12: Auto-Cool Decision Engine.
 * Enforces graduated mitigation (Levels 0-4), hysteresis against oscillation,
 * device-specific calibration, and gradual restoration.
 */
class AutoCoolDecisionEngine {

    fun decide(
        twinState: TwinState,
        trend: ThermalTrend,
        forecast: ThermalForecastResult?,
        settings: AutoCoolSettings,
        currentDecision: AutoCoolDecision?,
        capabilities: CoolingActionCapabilities = CoolingActionCapabilities()
    ): AutoCoolDecision {
        val now = System.currentTimeMillis()
        val temp = trend.currentTemperature ?: twinState.temperature ?: 35.0
        val v = trend.temperatureVelocity
        val headroom = trend.thermalHeadroom ?: twinState.thermalHeadroom ?: 0.0
        val thermalStatus = twinState.thermalState ?: twinState.latest?.thermalStatus ?: 0
        val isGaming = twinState.gamingState

        // Check if master Auto-Cool is enabled
        if (!settings.autoCoolEnabled) {
            // Even if auto-cool is OFF, device safety protection remains active for Level 3/4
            if (settings.thermalProtectionEnabled && (thermalStatus >= 3 || temp >= 44.0 || headroom >= 1.0)) {
                return AutoCoolDecision(
                    strategy = AutoCoolStrategy.LEVEL_3_AGGRESSIVE_COOL,
                    state = AutoCoolState.AGGRESSIVE_COOL,
                    reason = "Thermal Safety Override: Device approaching critical thermal threshold",
                    actions = listOf(
                        CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE,
                        CoolingAction.PAUSE_ROLLING_SCREEN_BUFFER,
                        CoolingAction.SWITCH_RESOURCE_GOVERNOR_ECO
                    ),
                    timestamp = now
                )
            }
            return AutoCoolDecision(
                strategy = AutoCoolStrategy.LEVEL_0_NORMAL,
                state = AutoCoolState.NO_ACTION,
                reason = "Auto-Cool disabled by user setting",
                actions = emptyList(),
                timestamp = now
            )
        }

        // Apply aggressiveness threshold offsets
        val offset = when (settings.aggressiveness) {
            AutoCoolAggressiveness.COOLING_FIRST -> -1.0 // Cool sooner
            AutoCoolAggressiveness.PERFORMANCE -> +1.5   // Tolerate slightly more heat
            AutoCoolAggressiveness.BALANCED -> 0.0
        }

        val previousStrategy = currentDecision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL
        val previousRecoveryCount = currentDecision?.consecutiveRecoverySamples ?: 0

        // ==============================================================
        // HYSTERESIS LOGIC:
        // Escalation is responsive; De-escalation requires sustained cooldown.
        // ==============================================================

        // 1. Check CRITICAL (LEVEL 4)
        if (thermalStatus >= 4 || temp >= (45.0 + offset) || headroom >= 1.05) {
            return AutoCoolDecision(
                strategy = AutoCoolStrategy.LEVEL_4_CRITICAL,
                state = AutoCoolState.AGGRESSIVE_COOL,
                reason = "Android thermal controller reported CRITICAL pressure (Status $thermalStatus, ${String.format(java.util.Locale.US, "%.1f°C", temp)}). Suspending all non-essential work.",
                actions = listOf(
                    CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE,
                    CoolingAction.REDUCE_MOTION_SAMPLING_RATE,
                    CoolingAction.PAUSE_ROLLING_SCREEN_BUFFER,
                    CoolingAction.PAUSE_LOCAL_AI,
                    CoolingAction.SWITCH_RESOURCE_GOVERNOR_ECO,
                    CoolingAction.ADJUST_UI_RENDERING_LOW_POWER,
                    CoolingAction.EMERGENCY_STOP_SESSION_ADVICE
                ),
                timestamp = now,
                consecutiveRecoverySamples = 0
            )
        }

        // 2. Check AGGRESSIVE COOL (LEVEL 3)
        val enterLevel3 = thermalStatus >= 3 || temp >= (42.0 + offset) || headroom >= 0.92
        val exitLevel3 = thermalStatus < 2 && temp < (40.5 + offset) && headroom < 0.85 && v <= 0.0

        if (previousStrategy == AutoCoolStrategy.LEVEL_4_CRITICAL) {
            // Recovering from Level 4 requires sustained recovery (steps down on 3rd sample)
            if (exitLevel3 && previousRecoveryCount >= 2) {
                return level3Decision(now, "Recovered from critical thermal threshold", 0)
            }
            return (currentDecision ?: level4Decision(now, "Holding critical state during cooldown", previousRecoveryCount))
                .copy(consecutiveRecoverySamples = previousRecoveryCount + 1, timestamp = now)
        }

        if (enterLevel3) {
            return level3Decision(now, "Approaching severe thermal limits (Status $thermalStatus, Headroom ${String.format(java.util.Locale.US, "%.2f", headroom)})", 0)
        }

        // 3. Check COOL (LEVEL 2)
        val enterLevel2 = (temp >= (39.5 + offset) && v >= 0.3) || headroom >= 0.82 || temp >= (41.0 + offset)
        val exitLevel2 = temp < (38.5 + offset) && headroom < 0.75 && v <= 0.05

        if (previousStrategy == AutoCoolStrategy.LEVEL_3_AGGRESSIVE_COOL) {
            if (exitLevel3 && previousRecoveryCount >= 2) {
                return level2Decision(now, "Thermal stress relaxing: step-down to COOL", 0)
            } else if (exitLevel3) {
                return (currentDecision ?: level3Decision(now, "Holding aggressive cooling during step-down", previousRecoveryCount))
                    .copy(consecutiveRecoverySamples = previousRecoveryCount + 1, timestamp = now)
            }
        }

        if (enterLevel2) {
            return level2Decision(now, "Thermal headroom deteriorating (Headroom ${String.format(java.util.Locale.US, "%.2f", headroom)}, Temp ${String.format(java.util.Locale.US, "%.1f°C", temp)})", 0)
        }

        // 4. Check PRE-COOL (LEVEL 1)
        val sixtySecForecast = forecast?.getPoint(60)?.predictedTemperature
        val forecastRising = sixtySecForecast != null && sixtySecForecast >= (39.0 + offset)
        val enterLevel1 = (v >= 0.35 && temp >= 37.0) || headroom >= 0.70 || forecastRising
        val exitLevel1 = temp < (37.5 + offset) && v <= 0.0 && headroom < 0.65

        if (previousStrategy == AutoCoolStrategy.LEVEL_2_COOL) {
            if (exitLevel2 && previousRecoveryCount >= 2) {
                return level1Decision(now, "Gradual recovery: stepping down to PRE-COOL", 0)
            } else if (exitLevel2) {
                return (currentDecision ?: level2Decision(now, "Holding cooling during step-down", previousRecoveryCount))
                    .copy(consecutiveRecoverySamples = previousRecoveryCount + 1, timestamp = now)
            }
        }

        if (enterLevel1) {
            val r = if (forecastRising) "Forecast predicts temperature climb (${String.format(java.util.Locale.US, "%.1f°C in 60s", sixtySecForecast)})"
                    else "Thermal trend rising (+${String.format(java.util.Locale.US, "%.2f°C/min", v)})"
            return level1Decision(now, r, 0)
        }

        // 5. NORMAL (LEVEL 0) / RECOVERY
        if (previousStrategy == AutoCoolStrategy.LEVEL_1_PRE_COOL) {
            if (exitLevel1 && previousRecoveryCount >= 2) {
                return AutoCoolDecision(
                    strategy = AutoCoolStrategy.LEVEL_0_NORMAL,
                    state = AutoCoolState.RECOVERY,
                    reason = "Device thermal state stabilized and recovered",
                    actions = emptyList(),
                    timestamp = now,
                    consecutiveRecoverySamples = 0
                )
            } else if (exitLevel1) {
                return (currentDecision ?: level1Decision(now, "Holding pre-cool during step-down", previousRecoveryCount))
                    .copy(consecutiveRecoverySamples = previousRecoveryCount + 1, timestamp = now)
            }
        }

        return AutoCoolDecision(
            strategy = AutoCoolStrategy.LEVEL_0_NORMAL,
            state = if (currentDecision?.state == AutoCoolState.RECOVERY) AutoCoolState.NO_ACTION else AutoCoolState.NO_ACTION,
            reason = if (isGaming) "Gaming thermal state optimal" else "Device thermal parameters normal",
            actions = emptyList(),
            timestamp = now,
            consecutiveRecoverySamples = 0
        )
    }

    private fun level1Decision(now: Long, reason: String, recoveryCount: Int) = AutoCoolDecision(
        strategy = AutoCoolStrategy.LEVEL_1_PRE_COOL,
        state = AutoCoolState.PRE_COOL,
        reason = reason,
        actions = listOf(
            CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE,
            CoolingAction.PAUSE_LOCAL_AI,
            CoolingAction.REDUCE_MOMENT_DETECTION_COMPLEXITY
        ),
        timestamp = now,
        consecutiveRecoverySamples = recoveryCount
    )

    private fun level2Decision(now: Long, reason: String, recoveryCount: Int) = AutoCoolDecision(
        strategy = AutoCoolStrategy.LEVEL_2_COOL,
        state = AutoCoolState.COOL,
        reason = reason,
        actions = listOf(
            CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE,
            CoolingAction.REDUCE_MOTION_SAMPLING_RATE,
            CoolingAction.PAUSE_ROLLING_SCREEN_BUFFER,
            CoolingAction.PAUSE_LOCAL_AI,
            CoolingAction.SWITCH_RESOURCE_GOVERNOR_ECO,
            CoolingAction.REQUEST_USER_GAME_GRAPHICS_ADVICE
        ),
        timestamp = now,
        consecutiveRecoverySamples = recoveryCount
    )

    private fun level3Decision(now: Long, reason: String, recoveryCount: Int) = AutoCoolDecision(
        strategy = AutoCoolStrategy.LEVEL_3_AGGRESSIVE_COOL,
        state = AutoCoolState.AGGRESSIVE_COOL,
        reason = reason,
        actions = listOf(
            CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE,
            CoolingAction.REDUCE_MOTION_SAMPLING_RATE,
            CoolingAction.PAUSE_ROLLING_SCREEN_BUFFER,
            CoolingAction.PAUSE_LOCAL_AI,
            CoolingAction.SWITCH_RESOURCE_GOVERNOR_ECO,
            CoolingAction.ADJUST_UI_RENDERING_LOW_POWER,
            CoolingAction.REQUEST_USER_DISPLAY_REFRESH_ADVICE,
            CoolingAction.REQUEST_USER_GAME_GRAPHICS_ADVICE
        ),
        timestamp = now,
        consecutiveRecoverySamples = recoveryCount
    )

    private fun level4Decision(now: Long, reason: String, recoveryCount: Int) = AutoCoolDecision(
        strategy = AutoCoolStrategy.LEVEL_4_CRITICAL,
        state = AutoCoolState.AGGRESSIVE_COOL,
        reason = reason,
        actions = listOf(
            CoolingAction.REDUCE_SOVARIX_TELEMETRY_RATE,
            CoolingAction.REDUCE_MOTION_SAMPLING_RATE,
            CoolingAction.PAUSE_ROLLING_SCREEN_BUFFER,
            CoolingAction.PAUSE_LOCAL_AI,
            CoolingAction.SWITCH_RESOURCE_GOVERNOR_ECO,
            CoolingAction.ADJUST_UI_RENDERING_LOW_POWER,
            CoolingAction.EMERGENCY_STOP_SESSION_ADVICE
        ),
        timestamp = now,
        consecutiveRecoverySamples = recoveryCount
    )
}

/**
 * Section 13: Predicted vs Actual Closed-Loop Verification Engine.
 */
class ThermalVerificationEngine {

    private var activeTicket: VerificationTicket? = null

    data class VerificationTicket(
        val id: Long,
        val startTimeMs: Long,
        val strategy: AutoCoolStrategy,
        val intervention: String,
        val predictedTempBefore: Double?,
        val actualTempBefore: Double?,
        var peakTemp: Double?
    )

    fun onInterventionStarted(
        id: Long,
        strategy: AutoCoolStrategy,
        intervention: String,
        predictedTempBefore: Double?,
        actualTempBefore: Double?
    ) {
        activeTicket = VerificationTicket(
            id = id,
            startTimeMs = System.currentTimeMillis(),
            strategy = strategy,
            intervention = intervention,
            predictedTempBefore = predictedTempBefore,
            actualTempBefore = actualTempBefore,
            peakTemp = actualTempBefore
        )
    }

    fun onSampleObserved(sample: Sample) {
        val ticket = activeTicket ?: return
        val current = sample.batteryC ?: return
        if (ticket.peakTemp == null || current > ticket.peakTemp!!) {
            ticket.peakTemp = current
        }
    }

    fun resolveVerification(
        currentTemp: Double?,
        timestamp: Long = System.currentTimeMillis()
    ): AutoCoolVerification? {
        val ticket = activeTicket ?: return null
        val durationSec = (timestamp - ticket.startTimeMs) / 1000

        // Only resolve if at least 20 seconds have elapsed since intervention
        if (durationSec < 20) return null

        val before = ticket.actualTempBefore
        val after = currentTemp
        val predicted = ticket.predictedTempBefore

        val delta = if (before != null && after != null) after - before else null
        val error = if (predicted != null && after != null) after - predicted else null

        val effectiveness = when {
            delta != null && delta <= -0.3 -> "POSITIVE"
            delta != null && delta <= 0.2 -> "NEUTRAL"
            delta != null && delta > 0.5 -> "NEGATIVE"
            else -> "NEUTRAL"
        }

        val result = AutoCoolVerification(
            id = ticket.id,
            timestamp = timestamp,
            intervention = ticket.intervention,
            strategy = ticket.strategy,
            predictedTempBefore = ticket.predictedTempBefore,
            actualTempBefore = ticket.actualTempBefore,
            actualTempPeak = ticket.peakTemp,
            actualTempAfter = after,
            temperatureDelta = delta,
            predictionError = error,
            coolingResponseTimeSec = durationSec,
            effectiveness = effectiveness
        )

        activeTicket = null
        return result
    }
}
