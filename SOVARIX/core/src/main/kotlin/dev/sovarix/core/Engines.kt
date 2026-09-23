package dev.sovarix.core

import kotlin.math.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResourceGovernor(private val config: GovernorConfig) {
    fun budgetExceeded(overhead: Overhead?): Boolean =
        (overhead?.processingMs ?: 0.0) > config.collectorBudgetMs ||
        (overhead?.cpuOneCorePct ?: 0.0) > config.cpuBudgetOneCorePct ||
        (overhead?.pssMb ?: 0.0) > config.memoryBudgetMb
}

class ObservationController(private val config: GovernorConfig) {
    private val governor = ResourceGovernor(config)
    private var burstStart: Long? = null
    private var recoveryUntil = 0L
    fun reset() { burstStart = null; recoveryUntil = 0L }
    fun choose(s: Sample, rise: Double?, overhead: Overhead?, economy: Boolean): ObservationPolicy {
        if ((s.thermalStatus ?: 0) >= 4) {
            reset()
            return ObservationPolicy(ObservationMode.LOW_POWER, config.lowPowerMs, "Android reports critical thermal pressure. Session stopped.", false, true)
        }
        val budgetExceeded = governor.budgetExceeded(overhead)
        if (economy || s.powerSave || !s.interactive || (s.batteryPct?.let { it <= config.batteryFloor } == true) ||
            (s.thermalStatus ?: 0) >= 3 || (s.headroom ?: 0.0) >= 1.0 || budgetExceeded) {
            burstStart = null
            recoveryUntil = s.elapsedMs + config.recoveryHoldMs
            return ObservationPolicy(ObservationMode.LOW_POWER, config.lowPowerMs,
                when { economy -> "Economy observation selected"; budgetExceeded -> "Own resource budget exceeded"
                    !s.interactive -> "Screen off; no wake lock"; s.powerSave -> "Android battery saver active"
                    (s.thermalStatus ?: 0) >= 3 || (s.headroom ?: 0.0) >= 1.0 -> "Thermal pressure: reducing our own work"
                    else -> "Low battery: conserving resources" }, false)
        }
        val start = burstStart
        if (start != null) {
            if (s.elapsedMs - start < config.maxBurstMs) return ObservationPolicy(ObservationMode.HIGH_ACTIVITY, config.highMs, "Bounded observation burst", false)
            burstStart = null; recoveryUntil = s.elapsedMs + config.recoveryHoldMs
        }
        if (s.elapsedMs < recoveryUntil || s.workload == Workload.RECOVERY)
            return ObservationPolicy(ObservationMode.RECOVERY, config.recoveryMs, "Recovery observation; burst cooldown", false)
        if (rise != null && rise >= config.rapidRiseCPerMin) {
            burstStart = s.elapsedMs
            return ObservationPolicy(ObservationMode.HIGH_ACTIVITY, config.highMs, "Recent battery temperature rose rapidly", false)
        }
        return ObservationPolicy(ObservationMode.NORMAL, config.normalMs, "Lightweight monitoring", s.workload != Workload.GAMING)
    }
}

/** Uses monotonic time and only the newest contiguous charging/workload regime. */
object Statistics {
    fun regime(history: List<Sample>, config: GovernorConfig): List<Sample> {
        if (history.isEmpty()) return emptyList()
        val latest = history.last(); var start = history.lastIndex
        while (start > 0) {
            val prev = history[start - 1]; val next = history[start]
            if (prev.workload != latest.workload || prev.charging != latest.charging ||
                next.elapsedMs - prev.elapsedMs !in 1..config.maxGapMs ||
                latest.elapsedMs - prev.elapsedMs > 15 * 60_000L) break
            start--
        }
        return history.subList(start, history.size)
    }
    fun fit(history: List<Sample>, config: GovernorConfig, value: (Sample) -> Double?): Trend? {
        val window = regime(history, config)
        val latest = window.lastOrNull() ?: return null
        // Never extrapolate from a stale sensor that is missing in the latest sample.
        if (value(latest)?.isFinite() != true) return null
        val points = window.mapNotNull { s -> value(s)?.takeIf { it.isFinite() }?.let { s.elapsedMs to it } }
        if (points.size < 4 || points.last().first - points.first().first < 20_000L) return null
        if (points.zipWithNext().any { (a, b) -> b.first - a.first > config.maxGapMs }) return null
        val xs = points.map { (it.first - latest.elapsedMs) / 60_000.0 }
        val ys = points.map { it.second }; val xm = xs.average(); val ym = ys.average()
        val sxx = xs.sumOf { (it - xm).pow(2) }
        if (sxx <= 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - xm) * (ys[it] - ym) } / sxx
        val intercept = ym - slope * xm
        val residual = sqrt(xs.indices.sumOf { (ys[it] - intercept - slope * xs[it]).pow(2) } / (xs.size - 2))
        return Trend(slope, intercept, residual, points.size, points.last().first - points.first().first, xm, sxx)
    }
}

class ForecastEngine(private val config: GovernorConfig = GovernorConfig()) {
    fun forecast(history: List<Sample>, minutes: Int, dna: DeviceDNA): Forecast {
        require(minutes in 1..15)
        val now = history.lastOrNull()?.elapsedMs ?: 0L
        val bias = dna.temperatureBiasByHorizon[minutes] ?: 0.0

        fun project(value: (Sample) -> Double?, low: Double, high: Double, floor: Double, appliedBias: Double = 0.0): Projection? {
            val fit = Statistics.fit(history, config, value) ?: return null
            val horizonMs = minutes * 60_000L
            if (fit.spanMs < horizonMs || fit.sampleCount < 8 || fit.spanMs < config.minimumHistoryMs) return null
            val raw = fit.latestEstimate + fit.slopePerMinute * minutes + appliedBias
            val band = (max(floor, fit.residualSd) * 2 * sqrt(1 + 1.0 / fit.sampleCount + (minutes - fit.xMean).pow(2) / fit.sxx)).coerceAtLeast(0.2)
            return Projection(raw.coerceIn(low, high), band, fit.sampleCount, fit.spanMs / 60_000.0, raw !in low..high)
        }

        val tempProj = project({ it.batteryC }, -20.0, 80.0, 0.1, bias)
        val battProj = project({ it.batteryPct }, 0.0, 100.0, 0.5)
        val memProj = project({ it.memoryUsedFraction?.times(100) }, 0.0, 100.0, 1.0)
        val windowPoints = Statistics.regime(history, config).size

        val evidence = if (tempProj != null) {
            "Linear trend over ${tempProj.samples} observations (${String.format(Locale.US, "%.1f", tempProj.historyMinutes)} min span)."
        } else {
            "Collecting history: $windowPoints of 8 observations required."
        }

        return Forecast(
            createdElapsedMs = now,
            horizonMinutes = minutes,
            temperature = tempProj,
            battery = battProj,
            memory = memProj,
            evidence = evidence,
            temperatureBiasApplied = bias
        )
    }

    /**
     * FutureEngine evaluation:
     * Consumes TwinState independently.
     * Answers: "What is likely to happen next?"
     * Does NOT depend on AnomalyEngine output.
     */
    fun evaluate(state: TwinState, horizonMinutes: Int = 2): ForecastResult {
        val fList = listOf(1, 2, 5, 15).map { forecast(state.history, it, state.dna) }
        val active = fList.firstOrNull { it.horizonMinutes == horizonMinutes } ?: fList.firstOrNull()
        val tempVal = active?.temperature?.value
        val battVal = active?.battery?.value
        val memVal = active?.memory?.value

        val baselineTemp = state.temperature ?: state.latest?.batteryC ?: 37.0
        val risk = when {
            tempVal != null && (tempVal - baselineTemp) >= 1.5 -> Risk.ANOMALY
            tempVal != null && (tempVal - baselineTemp) >= 0.5 -> Risk.WATCH
            active?.temperature != null -> Risk.NORMAL
            else -> Risk.UNKNOWN
        }

        val conf = when {
            active?.temperature != null && active.temperature.samples >= 15 -> 0.85
            active?.temperature != null -> 0.70
            else -> 0.30
        }

        // Multi-horizon numerical extrapolations (+10s, +30s, +60s, +5m, +15m)
        val slopePerSec = ((state.thermalTrend?.temperatureVelocity ?: state.temperatureTrend?.slopePerMinute ?: 0.0) / 60.0).coerceIn(-0.1, 0.1)
        val sampleCount = state.history.size
        val spanMins = if (sampleCount > 0) sampleCount * 0.2 else 1.0

        val p10 = Projection(
            value = (baselineTemp + slopePerSec * 10.0).coerceIn(-20.0, 80.0),
            heuristicBand = 0.2,
            samples = sampleCount,
            historyMinutes = spanMins,
            clamped = false
        )
        val p30 = Projection(
            value = (baselineTemp + slopePerSec * 30.0).coerceIn(-20.0, 80.0),
            heuristicBand = 0.35,
            samples = sampleCount,
            historyMinutes = spanMins,
            clamped = false
        )
        val p60 = Projection(
            value = (baselineTemp + slopePerSec * 60.0).coerceIn(-20.0, 80.0),
            heuristicBand = 0.5,
            samples = sampleCount,
            historyMinutes = spanMins,
            clamped = false
        )
        val p5m = fList.firstOrNull { it.horizonMinutes == 5 }?.temperature
        val p15m = fList.firstOrNull { it.horizonMinutes == 15 }?.temperature

        val recoveryRatePerMin = kotlin.math.abs(state.dna.behaviorModel.interventionRecoveryRateCPerMin ?: 0.22)
        val recoveryProj = if (state.productState == ProductState.RECOVERY || slopePerSec < -0.001) {
            val projectedCooling = (baselineTemp - recoveryRatePerMin * 2.0).coerceAtLeast(36.0)
            Projection(projectedCooling, 0.4, sampleCount, 2.0, false)
        } else null

        return ForecastResult(
            timestamp = state.timestamp.takeIf { it > 0 } ?: (state.latest?.wallMs ?: 0L),
            horizonMinutes = horizonMinutes,
            predictedTemperature = tempVal,
            predictedBattery = battVal,
            predictedMemory = memVal,
            confidence = conf,
            risk = risk,
            evidence = active?.evidence ?: "Insufficient stable history for projection.",
            forecasts = fList,
            temperatureBiasApplied = active?.temperatureBiasApplied ?: 0.0,
            forecast10s = p10,
            forecast30s = p30,
            forecast60s = p60,
            forecast5m = p5m,
            forecast15m = p15m,
            recoveryTrajectory = recoveryProj
        )
    }
}
typealias FutureEngine = ForecastEngine

object AnomalyEngine {
    fun evaluate(s: Sample, trend: Trend?, dna: DeviceDNA): Anomaly {
        val facts = mutableListOf<String>()
        var risk = Risk.NORMAL
        if (s.thermalStatus == null && s.batteryC == null && s.lowMemory == null)
            return Anomaly(Risk.UNKNOWN, listOf("Relevant signals are unavailable."))
        if ((s.thermalStatus ?: 0) >= 3 || (s.headroom ?: 0.0) >= 1.0) {
            risk = Risk.ANOMALY; facts += "Android reports elevated thermal pressure. No cross-app FPS measurement is available."
        } else if ((s.thermalStatus ?: 0) > 0) {
            risk = Risk.WATCH; facts += "Android thermal status is above NONE."
        }
        if (s.lowMemory == true) { if (risk != Risk.ANOMALY) risk = Risk.WATCH; facts += "Android reports low memory." }
        val baseline = dna.baselineByContext[contextKey(s)]
        if (baseline != null && baseline.count >= 30 && s.batteryC != null &&
            s.batteryC > baseline.meanC + 3 * max(baseline.sd, 0.5)) {
            if (risk != Risk.ANOMALY) risk = Risk.WATCH
            facts += "Battery temperature exceeds this phone's recorded context baseline by more than 3 spread units."
        }
        if (trend != null && trend.slopePerMinute > 0) facts += "Battery temperature trend is rising; this is not a CPU temperature reading."
        if (facts.isEmpty()) facts += "No configured alert in the available signals. This is not a device health diagnosis."
        return Anomaly(risk, facts)
    }

    /**
     * AnomalyEngine evaluation:
     * Consumes TwinState + DeviceDNA independently.
     * Answers: "Is the current device behavior unusual?"
     * Operates completely independently of FutureEngine and SimulationEngine.
     */
    fun evaluate(state: TwinState): AnomalyResult {
        val s = state.latest ?: return AnomalyResult(
            type = "UNKNOWN",
            severity = Risk.UNKNOWN,
            timestamp = state.timestamp,
            evidence = listOf("No telemetry sample available in TwinState.")
        )
        val trend = state.temperatureTrend ?: Statistics.fit(state.history, GovernorConfig()) { it.batteryC }
        val anomaly = evaluate(s, trend, state.dna)
        val baseline = state.dna.baselineByContext[contextKey(s)]
        val dev = if (baseline != null && s.batteryC != null) s.batteryC - baseline.meanC else null

        val anomalyType = when {
            (s.thermalStatus ?: 0) >= 3 || (s.headroom ?: 0.0) >= 1.0 -> "THERMAL_THROTTLE_RISK"
            dev != null && dev >= 2.5 -> "THERMAL_SPIKE"
            trend != null && trend.slopePerMinute >= 0.4 -> "THERMAL_SPIKE"
            s.lowMemory == true -> "MEMORY_PRESSURE_SPIKE"
            (state.batteryTrend?.slopePerMinute ?: 0.0) <= -0.5 -> "BATTERY_DRAIN_SPIKE"
            (s.thermalStatus ?: 0) > 0 -> "ELEVATED_TEMPERATURE"
            anomaly.risk == Risk.NORMAL -> "NOMINAL"
            else -> "NOMINAL"
        }

        return AnomalyResult(
            type = anomalyType,
            severity = anomaly.risk,
            observedValue = s.batteryC,
            baselineValue = baseline?.meanC,
            deviation = dev,
            timestamp = s.wallMs,
            indicators = anomaly.evidence,
            evidence = anomaly.evidence,
            score = when (anomaly.risk) {
                Risk.ANOMALY -> 0.90
                Risk.WATCH -> 0.60
                Risk.NORMAL -> 0.10
                Risk.UNKNOWN -> 0.0
            },
            contextKey = contextKey(s)
        )
    }

    fun contextKey(s: Sample) = "${s.workload.name}:${s.charging}"
}

data class Simulation(val base: Forecast, val assumedTrendMultiplier: Double, val temperatureC: Double?,
    val batteryPct: Double?, val explanation: String)

object SimulationEngine {
    /** A sensitivity analysis with a user assumption, not a learned effect of changing game graphics. */
    fun simulate(history: List<Sample>, base: Forecast, multiplier: Double): Simulation {
        require(multiplier in 0.0..2.0 && multiplier.isFinite())
        val last = history.lastOrNull()
        fun change(current: Double?, projected: Double?, low: Double, high: Double): Double? =
            if (current != null && projected != null) (current + (projected - current) * multiplier).coerceIn(low, high) else null
        return Simulation(base, multiplier,
            change(last?.batteryC, base.temperature?.value, -20.0, 80.0),
            change(last?.batteryPct, base.battery?.value, 0.0, 100.0),
            "SIMULATION — NOT REAL. Assumes the observed rate changes by the selected multiplier. No hardware setting is changed. Workload intervention effects are not calibrated.")
    }

    /**
     * SimulationEngine evaluation:
     * Operates on a COPY of TwinState.
     * NEVER mutates the real TwinState.
     * Answers: "What could happen if we changed something?" (Counterfactual analysis).
     */
    fun evaluate(state: TwinState, baseForecast: Forecast? = null): SimulationResult {
        // Clone state copy to guarantee absolute isolation from real TwinState
        val stateCopy = state.copy(history = state.history.toList())
        val forecast = baseForecast ?: stateCopy.forecasts.firstOrNull() ?: ForecastEngine().forecast(stateCopy.history, 2, stateCopy.dna)
        val lastSample = stateCopy.latest

        // Scenario A: No intervention (1.0x baseline trend)
        val simA = simulate(stateCopy.history, forecast, 1.0)
        val scenarioA = SimulationScenario(
            id = "scenario_a",
            name = "Scenario A: No Intervention (Current Trajectory)",
            assumedTrendMultiplier = 1.0,
            predictedTemperatureC = simA.temperatureC,
            predictedBatteryPct = simA.batteryPct,
            thermalRisk = when {
                (simA.temperatureC ?: 0.0) >= 42.0 -> Risk.ANOMALY
                (simA.temperatureC ?: 0.0) >= 39.0 -> Risk.WATCH
                else -> Risk.NORMAL
            },
            performanceRisk = if ((lastSample?.thermalStatus ?: 0) >= 2) Risk.WATCH else Risk.NORMAL,
            explanation = "Continues current trajectory with no workload or display adjustments."
        )

        // Scenario B: Reduced Workload (0.5x trend)
        val simB = simulate(stateCopy.history, forecast, 0.5)
        val scenarioB = SimulationScenario(
            id = "scenario_b",
            name = "Scenario B: Reduced Workload",
            assumedTrendMultiplier = 0.5,
            predictedTemperatureC = simB.temperatureC,
            predictedBatteryPct = simB.batteryPct,
            thermalRisk = if ((simB.temperatureC ?: 0.0) >= 42.0) Risk.ANOMALY else Risk.NORMAL,
            performanceRisk = Risk.NORMAL,
            explanation = "Simulates reduced graphics/frame load, projecting lower thermal trajectory."
        )

        // Scenario C: Cooling / Recovery Action (0.0x trend)
        val simC = simulate(stateCopy.history, forecast, 0.0)
        val scenarioC = SimulationScenario(
            id = "scenario_c",
            name = "Scenario C: Cooling / Recovery Hold",
            assumedTrendMultiplier = 0.0,
            predictedTemperatureC = simC.temperatureC,
            predictedBatteryPct = simC.batteryPct,
            thermalRisk = Risk.NORMAL,
            performanceRisk = Risk.NORMAL,
            explanation = "Simulates thermal leveling hold where temperature slope plateaus."
        )

        val scenarios = listOf(scenarioA, scenarioB, scenarioC)
        val recommended = if (scenarioA.thermalRisk != Risk.NORMAL) scenarioB else scenarioA

        return SimulationResult(
            timestamp = stateCopy.timestamp.takeIf { it > 0 } ?: (lastSample?.wallMs ?: 0L),
            baseForecast = forecast,
            scenarios = scenarios,
            recommendedScenario = recommended,
            explanation = "SIMULATION — COUNTERFACTUAL ANALYSIS ON A CLONED COPY OF TWIN STATE. REAL DEVICE IS UNCHANGED."
        )
    }

    /**
     * Evaluates a concrete controllable lever on a copy of TwinState.
     * Explicitly rejects unsupported / unmodeled system variables per Directive Section 7.
     */
    fun simulateLever(state: TwinState, request: SimulationRequest): SimulationOutcome {
        val now = state.timestamp.takeIf { it > 0 } ?: (state.latest?.wallMs ?: System.currentTimeMillis())
        if (request.lever == ControllableLever.UNSUPPORTED) {
            return SimulationUnsupported(
                variable = request.targetValue,
                reason = "Variable '${request.targetValue}' cannot be controlled or reliably modeled via public Android APIs. SOVARIX does not fabricate unmodeled system simulations.",
                timestamp = now
            )
        }

        val multiplier = when (request.lever) {
            ControllableLever.FPS_CAP -> when (request.targetValue) {
                "60" -> 0.60
                "90" -> 0.80
                "120" -> 1.00
                else -> 0.60
            }
            ControllableLever.WORKLOAD_INTENSITY -> when (request.targetValue) {
                "25%" -> 0.25
                "50%" -> 0.50
                "75%" -> 0.75
                "100%" -> 1.00
                else -> 0.50
            }
            ControllableLever.APP_DIMMING -> when (request.targetValue) {
                "20%" -> 0.85
                "50%" -> 0.92
                else -> 0.85
            }
            ControllableLever.RESOLUTION_SCALE -> when (request.targetValue) {
                "720p" -> 0.70
                "1080p" -> 1.00
                else -> 0.80
            }
            ControllableLever.UNSUPPORTED -> 1.0
        }

        val stateCopy = state.copy(history = state.history.toList())
        val forecast = stateCopy.forecasts.firstOrNull { it.horizonMinutes == request.horizonMinutes }
            ?: ForecastEngine().forecast(stateCopy.history, request.horizonMinutes, stateCopy.dna)
        val lastSample = stateCopy.latest

        val simBaseline = simulate(stateCopy.history, forecast, 1.0)
        val simIntervention = simulate(stateCopy.history, forecast, multiplier)

        val scenarioBaseline = SimulationScenario(
            id = "scenario_a",
            name = "Baseline Trajectory (No Intervention)",
            assumedTrendMultiplier = 1.0,
            predictedTemperatureC = simBaseline.temperatureC,
            predictedBatteryPct = simBaseline.batteryPct,
            thermalRisk = when {
                (simBaseline.temperatureC ?: 0.0) >= 42.0 -> Risk.ANOMALY
                (simBaseline.temperatureC ?: 0.0) >= 39.0 -> Risk.WATCH
                else -> Risk.NORMAL
            },
            performanceRisk = if ((lastSample?.thermalStatus ?: 0) >= 2) Risk.WATCH else Risk.NORMAL,
            explanation = "Maintains current unconstrained workload."
        )

        val scenarioLever = SimulationScenario(
            id = "scenario_lever_${request.lever.name.lowercase()}",
            name = "Simulated Lever: ${request.lever.label} -> ${request.targetValue}",
            assumedTrendMultiplier = multiplier,
            predictedTemperatureC = simIntervention.temperatureC,
            predictedBatteryPct = simIntervention.batteryPct,
            thermalRisk = when {
                (simIntervention.temperatureC ?: 0.0) >= 42.0 -> Risk.ANOMALY
                (simIntervention.temperatureC ?: 0.0) >= 39.0 -> Risk.WATCH
                else -> Risk.NORMAL
            },
            performanceRisk = Risk.NORMAL,
            explanation = "Assumes ${request.lever.label} set to ${request.targetValue} yields ~${(multiplier * 100).toInt()}% trend scaling."
        )

        return SimulationResult(
            timestamp = now,
            baseForecast = forecast,
            scenarios = listOf(scenarioBaseline, scenarioLever),
            recommendedScenario = scenarioLever,
            activeLever = request.lever,
            requestedValue = request.targetValue,
            isSupported = true,
            explanation = "SIMULATION — COUNTERFACTUAL ANALYSIS OF ${request.lever.label.uppercase()} ON A CLONED COPY OF TWIN STATE. REAL PHONE HARDWARE IS UNCHANGED."
        )
    }

    /** Dispatches a query or variable name. Rejects unmodeled variables. */
    fun simulateQuery(state: TwinState, variableOrQuery: String): SimulationOutcome {
        val q = variableOrQuery.trim().lowercase(Locale.US)
        val now = state.timestamp.takeIf { it > 0 } ?: (state.latest?.wallMs ?: System.currentTimeMillis())
        val behavior = state.dna.behaviorModel
        val currentTemp = state.temperature ?: state.latest?.batteryC ?: 37.0
        val currentBatt = state.battery ?: 75.0

        // 1. "What if I play for another hour?" / "60 minutes" / Telugu / Hindi
        if (q.contains("hour") || q.contains("60 min") || q.contains("another 60") ||
            q.contains("గంట") || q.contains("ఆడితే") || q.contains("ఆడినా") ||
            q.contains("घंटे") || q.contains("खेलूं") || q.contains("एक घंटा")) {
            val deltaTemp = behavior.gamingDurationThermalCurve[60] ?: 6.8
            val drainPerHour = behavior.workloadBatteryDrainRates[Workload.GAMING] ?: 16.0
            val projectedTemp = (currentTemp + deltaTemp).coerceIn(20.0, 55.0)
            val projectedBatt = (currentBatt - drainPerHour).coerceIn(0.0, 100.0)

            val currentPath = SimulationScenario(
                id = "current_path",
                name = "CURRENT PATH: Stop Session Now",
                assumedTrendMultiplier = 0.0,
                predictedTemperatureC = currentTemp,
                predictedBatteryPct = currentBatt,
                thermalRisk = if (currentTemp >= 40.0) Risk.WATCH else Risk.NORMAL,
                performanceRisk = Risk.NORMAL,
                explanation = "Session ends now. Temperature stabilizes and begins cooling toward baseline."
            )
            val alternativePath = SimulationScenario(
                id = "alternative_path",
                name = "ALTERNATIVE PATH: Game for Another 60 Min",
                assumedTrendMultiplier = 1.0,
                predictedTemperatureC = projectedTemp,
                predictedBatteryPct = projectedBatt,
                thermalRisk = if (projectedTemp >= 42.0) Risk.ANOMALY else Risk.WATCH,
                performanceRisk = if (projectedTemp >= 42.0) Risk.ANOMALY else Risk.WATCH,
                explanation = "Sustained 60 min gaming adds ~+${String.format(Locale.US, "%.1f", deltaTemp)}°C based on your device's empirical gaming thermal curve."
            )
            return SimulationResult(
                timestamp = now,
                scenarios = listOf(currentPath, alternativePath),
                recommendedScenario = currentPath,
                isSupported = true,
                explanation = "COUNTERFACTUAL SIMULATION: Comparing current path vs 60 minutes additional gaming based on Device DNA."
            )
        }

        // 2. "What if I record while gaming?" / "screen recording" / Telugu / Hindi
        if (q.contains("record") || q.contains("screen capture") ||
            q.contains("రికార్డ్") || q.contains("రికార్డింగ్") ||
            q.contains("रिकॉर्ड") || q.contains("रिकॉर्डिंग")) {
            val deltaHeatingRate = behavior.recordingThermalDeltaCPerMin ?: 0.18
            val projectedTemp = (currentTemp + deltaHeatingRate * 15.0).coerceIn(20.0, 55.0)
            val projectedBatt = (currentBatt - 4.5).coerceIn(0.0, 100.0)

            val currentPath = SimulationScenario(
                id = "current_path",
                name = "CURRENT PATH: Gaming without Recording",
                assumedTrendMultiplier = 1.0,
                predictedTemperatureC = (currentTemp + 1.2).coerceIn(20.0, 55.0),
                predictedBatteryPct = (currentBatt - 3.5).coerceIn(0.0, 100.0),
                thermalRisk = Risk.NORMAL,
                performanceRisk = Risk.NORMAL,
                explanation = "Standard gaming workload without screen capture pipeline overhead."
            )
            val alternativePath = SimulationScenario(
                id = "alternative_path",
                name = "ALTERNATIVE PATH: Gaming with Active Screen Recording",
                assumedTrendMultiplier = 1.5,
                predictedTemperatureC = projectedTemp,
                predictedBatteryPct = projectedBatt,
                thermalRisk = if (projectedTemp >= 41.0) Risk.WATCH else Risk.NORMAL,
                performanceRisk = if (projectedTemp >= 41.0) Risk.WATCH else Risk.NORMAL,
                explanation = "MediaProjection capture buffer adds ~+${String.format(Locale.US, "%.2f", deltaHeatingRate)}°C/min thermal load."
            )
            return SimulationResult(
                timestamp = now,
                scenarios = listOf(currentPath, alternativePath),
                recommendedScenario = currentPath,
                isSupported = true,
                explanation = "COUNTERFACTUAL SIMULATION: Gaming with vs without screen recording."
            )
        }

        // 3. "What if I charge while gaming?" / "charging" / Telugu / Hindi
        if (q.contains("charge") || q.contains("charging") ||
            q.contains("ఛార్జ్") || q.contains("ఛార్జింగ్") ||
            q.contains("चार्ज") || q.contains("चार्जिंग")) {
            val mult = behavior.chargingGamingThermalMultiplier ?: 1.35
            val currentGamingRate = behavior.workloadHeatingRates[Workload.GAMING] ?: 0.28
            val chargingRate = currentGamingRate * mult
            val projectedTemp = (currentTemp + chargingRate * 15.0).coerceIn(20.0, 55.0)

            val currentPath = SimulationScenario(
                id = "current_path",
                name = "CURRENT PATH: Gaming on Battery",
                assumedTrendMultiplier = 1.0,
                predictedTemperatureC = (currentTemp + currentGamingRate * 15.0).coerceIn(20.0, 55.0),
                predictedBatteryPct = (currentBatt - 4.0).coerceIn(0.0, 100.0),
                thermalRisk = Risk.NORMAL,
                performanceRisk = Risk.NORMAL,
                explanation = "Discharging battery under gaming load."
            )
            val alternativePath = SimulationScenario(
                id = "alternative_path",
                name = "ALTERNATIVE PATH: Gaming while Charging",
                assumedTrendMultiplier = mult,
                predictedTemperatureC = projectedTemp,
                predictedBatteryPct = (currentBatt + 8.0).coerceIn(0.0, 100.0),
                thermalRisk = if (projectedTemp >= 42.0) Risk.ANOMALY else Risk.WATCH,
                performanceRisk = if (projectedTemp >= 42.0) Risk.ANOMALY else Risk.WATCH,
                explanation = "Battery fast-charging adds joule heating, accelerating thermal rise by ~+${((mult - 1.0) * 100).toInt()}%."
            )
            return SimulationResult(
                timestamp = now,
                scenarios = listOf(currentPath, alternativePath),
                recommendedScenario = currentPath,
                isSupported = true,
                explanation = "COUNTERFACTUAL SIMULATION: Gaming on battery vs gaming while charging."
            )
        }

        // 4. "What if I reduce the workload?" / "reduce" / Telugu / Hindi
        if (q.contains("reduce") || q.contains("lower") || q.contains("cool") || q.contains("mitigat") ||
            q.contains("stop") || q.contains("తగ్గించ") || q.contains("తగ్గిస్తే") || q.contains("ఆపితే") ||
            q.contains("कम") || q.contains("घटा") || q.contains("बंद")) {
            val coolingRate = kotlin.math.abs(behavior.interventionRecoveryRateCPerMin ?: 0.22)
            val projectedTemp = (currentTemp - coolingRate * 10.0).coerceAtLeast(36.0)

            val currentPath = SimulationScenario(
                id = "current_path",
                name = "CURRENT PATH: Sustained Heavy Workload",
                assumedTrendMultiplier = 1.0,
                predictedTemperatureC = currentTemp + 1.5,
                predictedBatteryPct = currentBatt - 3.0,
                thermalRisk = if (currentTemp >= 40.0) Risk.WATCH else Risk.NORMAL,
                performanceRisk = Risk.NORMAL,
                explanation = "Maintains current unconstrained processing load."
            )
            val alternativePath = SimulationScenario(
                id = "alternative_path",
                name = "ALTERNATIVE PATH: Workload Reduction / Auto-Cool Active",
                assumedTrendMultiplier = 0.3,
                predictedTemperatureC = projectedTemp,
                predictedBatteryPct = currentBatt - 1.0,
                thermalRisk = Risk.NORMAL,
                performanceRisk = Risk.NORMAL,
                explanation = "Throttling background sampling and pausing non-essential intelligence restores cooling at ~${String.format(Locale.US, "%.2f", coolingRate)}°C/min."
            )
            return SimulationResult(
                timestamp = now,
                scenarios = listOf(currentPath, alternativePath),
                recommendedScenario = alternativePath,
                isSupported = true,
                explanation = "COUNTERFACTUAL SIMULATION: Sustained workload vs workload reduction."
            )
        }

        // 5. Check unsupported or standard levers via LocalAIIntent
        val intent = LocalAIEngine.parseIntent(variableOrQuery)
        return when (intent.type) {
            LocalAIIntentType.UNSUPPORTED_SIMULATION -> SimulationUnsupported(
                variable = intent.unsupportedVariable ?: variableOrQuery,
                reason = "Variable '$variableOrQuery' cannot be controlled or reliably modeled via public Android APIs. SOVARIX does not fabricate unmodeled system simulations.",
                timestamp = now
            )
            LocalAIIntentType.SIMULATE -> {
                val req = intent.simulationRequest ?: SimulationRequest(ControllableLever.FPS_CAP, "60")
                simulateLever(state, req)
            }
            else -> evaluate(state)
        }
    }
}

// =========================================================
// CORRELATION LAYER (INSIGHT / EVENT ENGINE)
// =========================================================

object InsightEngine {
    /**
     * InsightEngine correlates multi-engine outputs.
     * It does NOT become a mandatory sequential dependency for the individual intelligence engines.
     */
    fun correlate(
        state: TwinState,
        forecast: ForecastResult? = null,
        anomaly: AnomalyResult? = null,
        simulation: SimulationResult? = null,
        gaming: GamingMomentResult? = null
    ): List<InsightResult> {
        val insights = mutableListOf<InsightResult>()
        val now = state.timestamp.takeIf { it > 0 } ?: (state.latest?.wallMs ?: System.currentTimeMillis())

        // 1. Thermal Risk Correlation (Forecast + Anomaly + Simulation Mitigation)
        val isThermalForecastHigh = (forecast?.risk == Risk.ANOMALY || forecast?.risk == Risk.WATCH)
        val isAnomalyDetected = (anomaly?.risk == Risk.ANOMALY || anomaly?.risk == Risk.WATCH)
        val bBenefit = simulation?.scenarios?.find { it.id == "scenario_b" }?.let { b ->
            val aTemp = simulation.scenarios.find { it.id == "scenario_a" }?.predictedTemperatureC
            if (aTemp != null && b.predictedTemperatureC != null) aTemp - b.predictedTemperatureC else null
        }

        if (isThermalForecastHigh && isAnomalyDetected) {
            val diffStr = if (bBenefit != null && bBenefit > 0.1) String.format(" (predicted -%.1f°C under reduced workload)", bBenefit) else ""
            insights += InsightResult(
                id = "insight_thermal_${now}",
                timestamp = now,
                category = InsightCategory.THERMAL,
                title = "Thermal performance risk detected",
                summary = "Thermal rise predicted and temperature is abnormal for this device baseline.$diffStr A workload reduction scenario is predicted to reduce thermal rise.",
                confidence = ((forecast?.confidence ?: 0.5) + (anomaly?.score ?: 0.5)) / 2.0,
                severity = Risk.ANOMALY,
                recommendedAction = TwinAction.REDUCE_OBSERVATION,
                supportingEvidence = (forecast?.evidence?.let { listOf(it) } ?: emptyList()) + (anomaly?.evidence ?: emptyList())
            )
        } else if (isThermalForecastHigh) {
            insights += InsightResult(
                id = "insight_forecast_rise_${now}",
                timestamp = now,
                category = InsightCategory.THERMAL,
                title = "Projected temperature rise",
                summary = "Temperature extrapolation indicates upward trajectory over horizon.",
                confidence = forecast?.confidence ?: 0.6,
                severity = Risk.WATCH,
                recommendedAction = null,
                supportingEvidence = listOf(forecast?.evidence ?: "")
            )
        } else if (isAnomalyDetected) {
            insights += InsightResult(
                id = "insight_anomaly_${now}",
                timestamp = now,
                category = InsightCategory.SYSTEM,
                title = "Atypical device baseline deviation",
                summary = anomaly?.evidence?.firstOrNull() ?: "Device metric deviates from recorded context baseline.",
                confidence = anomaly?.score ?: 0.7,
                severity = anomaly?.risk ?: Risk.WATCH,
                recommendedAction = null,
                supportingEvidence = anomaly?.evidence ?: emptyList()
            )
        }

        // 2. Gaming Intelligence Correlation
        if (gaming != null && gaming.detected && (gaming.moment != null || gaming.momentType != null)) {
            val mType = gaming.moment?.momentType ?: gaming.momentType ?: "MOMENT"
            val hasMotion = (gaming.motionSnapshot?.movementIntensity ?: 0f) > 0.5f
            insights += InsightResult(
                id = "insight_gaming_${now}",
                timestamp = now,
                category = InsightCategory.GAMING,
                title = "Gaming event correlated: $mType",
                summary = "Multi-signal fusion detected gaming event (confidence ${(gaming.confidence * 100).toInt()}%)." +
                        if (hasMotion) " High physical motion confirmed." else "",
                confidence = gaming.confidence,
                severity = Risk.NORMAL,
                recommendedAction = null,
                supportingEvidence = gaming.availableSignals
            )
        }

        // 3. Performance / Memory Pressure Correlation
        if (state.memoryPressure == true || state.latest?.lowMemory == true) {
            insights += InsightResult(
                id = "insight_mem_${now}",
                timestamp = now,
                category = InsightCategory.PERFORMANCE,
                title = "Memory pressure reported by Android",
                summary = "System is experiencing low memory pressure. Background allocations constrained.",
                confidence = 0.90,
                severity = Risk.WATCH,
                recommendedAction = null,
                supportingEvidence = listOf("Android lowMemory flag is true")
            )
        }

        return insights
    }
}

// =========================================================
// HISTORICAL EVIDENCE LAYER (BLACK BOX)
// =========================================================

class BlackBox(private val capacity: Int = 300) {
    private val events = ArrayDeque<BlackBoxEvent>()
    private var nextId = 0L
    private var lastHash = "GENESIS"

    private fun computeHash(previousHash: String, id: Long, timestamp: Long, eventType: String,
                            intervention: String?, predictedOutcome: Double?, actualOutcome: Double?,
                            predictionError: Double?, notes: String): String {
        val canonical = "$previousHash|$id|$timestamp|$eventType|${intervention ?: ""}|${predictedOutcome ?: ""}|${actualOutcome ?: ""}|${predictionError ?: ""}|$notes"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun record(
        eventType: String,
        twinState: TwinState,
        forecast: ForecastResult? = null,
        anomaly: AnomalyResult? = null,
        simulation: SimulationResult? = null,
        gamingContext: GamingMomentResult? = null,
        intervention: String? = null,
        predictedOutcome: Double? = null,
        actualOutcome: Double? = null,
        predictionError: Double? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        source: String = "TWIN_ENGINE",
        evidenceLevel: EvidenceLevel = EvidenceLevel.OBSERVATIONAL
    ): BlackBoxEvent {
        val id = ++nextId
        val previousHash = lastHash
        val currentHash = computeHash(previousHash, id, timestamp, eventType, intervention, predictedOutcome, actualOutcome, predictionError, notes)
        val event = BlackBoxEvent(
            id = id,
            timestamp = timestamp,
            eventType = eventType,
            twinStateSnapshot = twinState,
            forecast = forecast,
            anomaly = anomaly,
            simulation = simulation,
            gamingContext = gamingContext,
            intervention = intervention,
            predictedOutcome = predictedOutcome,
            actualOutcome = actualOutcome,
            predictionError = predictionError,
            notes = notes,
            previousHash = previousHash,
            currentHash = currentHash,
            source = source,
            evidenceLevel = evidenceLevel
        )
        lastHash = currentHash
        events.addLast(event)
        while (events.size > capacity) events.removeFirst()
        return event
    }

    @Synchronized
    fun record(event: BlackBoxEvent): BlackBoxEvent {
        // Recompute hash for integrity when loading external events
        val previousHash = lastHash
        val currentHash = computeHash(previousHash, event.id, event.timestamp, event.eventType, event.intervention, event.predictedOutcome, event.actualOutcome, event.predictionError, event.notes)
        val hashed = event.copy(previousHash = previousHash, currentHash = currentHash)
        lastHash = currentHash
        if (event.id > nextId) nextId = event.id
        events.addLast(hashed)
        while (events.size > capacity) events.removeFirst()
        return hashed
    }

    @Synchronized
    fun getAll(): List<BlackBoxEvent> = events.toList()

    @Synchronized
    fun getLatest(): BlackBoxEvent? = events.lastOrNull()

    @Synchronized
    fun clear() { events.clear(); nextId = 0; lastHash = "GENESIS" }

    /**
     * Verifies the SHA-256 hash chain from first event to last.
     * Returns (isValid, statusMessage).
     */
    @Synchronized
    fun verifyChain(): Pair<Boolean, String> {
        if (events.isEmpty()) return Pair(true, "EMPTY LEDGER — NO EVENTS TO VERIFY")
        var expectedPrev = events.first().previousHash
        for ((index, event) in events.withIndex()) {
            if (event.previousHash != expectedPrev) {
                return Pair(false, "CHAIN BROKEN AT EVENT #${event.id} (index $index): expected previousHash=$expectedPrev, found=${event.previousHash}")
            }
            val recomputed = computeHash(event.previousHash, event.id, event.timestamp, event.eventType, event.intervention, event.predictedOutcome, event.actualOutcome, event.predictionError, event.notes)
            if (event.currentHash != recomputed) {
                return Pair(false, "HASH MISMATCH AT EVENT #${event.id} (index $index): stored=${event.currentHash.take(16)}..., recomputed=${recomputed.take(16)}...")
            }
            expectedPrev = event.currentHash
        }
        return Pair(true, "VERIFIED — ${events.size} events, chain intact from ${events.first().previousHash} to ${events.last().currentHash.take(16)}...")
    }
}

// =========================================================
// INCIDENT ENGINE (SECTIONS 8-9)
// =========================================================

class IncidentEngine {
    private var nextIncidentId = 0L
    private val activeIncidents = mutableListOf<Incident>()
    private val resolvedIncidents = mutableListOf<Incident>()
    private var lastIncidentAt = 0L
    private val cooldownMs = 120_000L // Minimum 2 minutes between incidents

    fun evaluate(
        anomaly: AnomalyResult,
        state: TwinState,
        blackBoxEvents: List<BlackBoxEvent>
    ): Incident? {
        if (anomaly.severity != Risk.ANOMALY && anomaly.severity != Risk.WATCH) {
            // Check if any active incidents should be resolved
            resolveActiveIncidents(state)
            return null
        }
        // Cooldown: don't create duplicate incidents
        if (System.currentTimeMillis() - lastIncidentAt < cooldownMs) return null

        val timeline = reconstructTimeline(state, blackBoxEvents)
        val deviation = anomaly.deviation
        val type = anomaly.type

        val why = WhyEngine.explain(
            incident = Incident(
                id = nextIncidentId + 1,
                timestamp = System.currentTimeMillis(),
                type = type,
                severity = anomaly.severity
            ),
            state = state
        )
        val incident = Incident(
            id = ++nextIncidentId,
            timestamp = System.currentTimeMillis(),
            type = type,
            severity = anomaly.severity,
            timelineEvents = timeline,
            whyReport = why,
            status = IncidentStatus.INVESTIGATING,
            evidenceCount = timeline.size,
            deviationFromBaseline = deviation,
            summary = buildSummary(type, deviation, anomaly)
        )
        activeIncidents.add(incident)
        lastIncidentAt = System.currentTimeMillis()
        return incident
    }

    fun recordIncident(incident: Incident) {
        val existing = activeIncidents.find { it.id == incident.id }
        if (existing == null) {
            activeIncidents.add(incident)
        }
        lastIncidentAt = System.currentTimeMillis()
    }

    private fun reconstructTimeline(state: TwinState, blackBoxEvents: List<BlackBoxEvent>): List<IncidentTimelineEvent> {
        val timeline = mutableListOf<IncidentTimelineEvent>()
        val now = System.currentTimeMillis()
        val lookbackMs = 180_000L // 3 minutes

        // Pull from recent history samples
        val recentSamples = state.history.filter { now - it.wallMs < lookbackMs }
        if (recentSamples.isEmpty()) return timeline

        val baseline = state.dna.baselineByContext[AnomalyEngine.contextKey(recentSamples.first())]

        for (sample in recentSamples) {
            val tempDev = if (baseline != null && sample.batteryC != null) sample.batteryC - baseline.meanC else null

            // Detect state transitions
            val label = when {
                (sample.thermalStatus ?: 0) >= 3 -> "Thermal throttling risk"
                (sample.thermalStatus ?: 0) >= 2 -> "Thermal status elevated"
                tempDev != null && tempDev > 2.0 -> "Temperature above baseline"
                sample.lowMemory == true -> "Memory pressure"
                else -> "Normal observation"
            }

            timeline.add(IncidentTimelineEvent(
                timestamp = sample.wallMs,
                label = label,
                metric = "temperature",
                value = sample.batteryC,
                baselineValue = baseline?.meanC,
                deviation = tempDev
            ))
        }

        // Add workload/context change events from BlackBox
        val recentBlackBox = blackBoxEvents.filter { now - it.timestamp < lookbackMs && it.eventType in listOf("WORKLOAD_CHANGE", "INTERVENTION", "GOVERNOR") }
        for (event in recentBlackBox) {
            timeline.add(IncidentTimelineEvent(
                timestamp = event.timestamp,
                label = "${event.eventType}: ${event.notes}",
                metric = null
            ))
        }

        return timeline.sortedBy { it.timestamp }
    }

    private fun buildSummary(type: String, deviation: Double?, anomaly: AnomalyResult): String {
        val devStr = deviation?.let { String.format(java.util.Locale.US, "%+.1f°C from baseline", it) } ?: "deviation unknown"
        return when (type) {
            "THERMAL_SPIKE", "THERMAL_THROTTLE_RISK" -> "Thermal deviation detected ($devStr). ${anomaly.evidence.firstOrNull() ?: ""}"
            "MEMORY_PRESSURE_SPIKE" -> "Memory pressure event. Android reports low memory."
            "BATTERY_DRAIN_SPIKE" -> "Abnormal battery drain rate detected ($devStr)."
            else -> "Device behavioral anomaly detected ($devStr)."
        }
    }

    private fun resolveActiveIncidents(state: TwinState) {
        val iterator = activeIncidents.iterator()
        while (iterator.hasNext()) {
            val incident = iterator.next()
            val age = System.currentTimeMillis() - incident.timestamp
            if (age > 300_000 && state.anomaly.risk == Risk.NORMAL) {
                iterator.remove()
                resolvedIncidents.add(incident.copy(
                    status = IncidentStatus.UNRESOLVED,
                    resolvedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    fun getActiveIncidents(): List<Incident> = activeIncidents.toList()
    fun getResolvedIncidents(): List<Incident> = resolvedIncidents.toList()
    fun getAllIncidents(): List<Incident> = (activeIncidents + resolvedIncidents).sortedByDescending { it.timestamp }
}

// =========================================================
// HYPOTHESIS ENGINE (SECTION 10)
// =========================================================

object HypothesisEngine {
    fun generateHypotheses(incident: Incident, state: TwinState, overhead: Overhead?): List<Hypothesis> {
        val hypotheses = mutableListOf<Hypothesis>()

        when {
            incident.type.contains("THERMAL") -> {
                // H1: Workload caused thermal rise
                val workloadEvidence = mutableListOf<String>()
                val workloadContra = mutableListOf<String>()
                if (state.workload == Workload.GAMING || state.workload == Workload.EVERYDAY) {
                    workloadEvidence += "Active workload: ${state.workload.name}"
                }
                val timelineWorkloadChanges = incident.timelineEvents.filter { it.label.contains("WORKLOAD") }
                if (timelineWorkloadChanges.isNotEmpty()) {
                    workloadEvidence += "Workload changed during incident period"
                } else {
                    workloadContra += "No workload change detected in timeline"
                }
                hypotheses += Hypothesis(
                    id = "H1_${incident.id}",
                    incidentId = incident.id,
                    description = "Higher workload caused increased thermal response",
                    supportingEvidence = workloadEvidence,
                    contradictingEvidence = workloadContra,
                    requiredExperiment = "Workload reduction test",
                    risk = Risk.NORMAL,
                    cost = "LOW"
                )

                // H2: Charging contributed
                val chargingEvidence = mutableListOf<String>()
                val chargingContra = mutableListOf<String>()
                if (state.chargingState == true) {
                    chargingEvidence += "Device is currently charging"
                    chargingEvidence += "Charging adds thermal load to battery"
                } else {
                    chargingContra += "Device is not charging"
                }
                hypotheses += Hypothesis(
                    id = "H2_${incident.id}",
                    incidentId = incident.id,
                    description = "Charging contributed to thermal rise",
                    supportingEvidence = chargingEvidence,
                    contradictingEvidence = chargingContra,
                    risk = Risk.NORMAL,
                    cost = "LOW"
                )

                // H3: SOVARIX capture overhead
                val captureEvidence = mutableListOf<String>()
                val captureContra = mutableListOf<String>()
                if (overhead != null) {
                    val cpuStr = overhead.cpuOneCorePct?.let { "${String.format(java.util.Locale.US, "%.1f", it)}% CPU" }
                    val tempDelta = overhead.temperatureDeltaC?.let { String.format(java.util.Locale.US, "%+.1f°C session delta", it) }
                    if (overhead.cpuOneCorePct != null && overhead.cpuOneCorePct > 2.0) {
                        captureEvidence += "SOVARIX CPU usage: $cpuStr"
                    } else {
                        captureContra += "SOVARIX CPU usage is low${cpuStr?.let { " ($it)" } ?: ""}"
                    }
                    if (tempDelta != null) captureEvidence += "Session temperature delta: $tempDelta"
                    if (overhead.gamingCaptureState != null) {
                        captureEvidence += "Gaming capture active: ${overhead.gamingCaptureState}"
                    }
                } else {
                    captureContra += "No overhead data available"
                }
                hypotheses += Hypothesis(
                    id = "H3_${incident.id}",
                    incidentId = incident.id,
                    description = "SOVARIX observation/capture overhead contributed to thermal rise",
                    supportingEvidence = captureEvidence,
                    contradictingEvidence = captureContra,
                    requiredExperiment = "Capture overhead test: measure temperature with capture ON vs OFF",
                    risk = Risk.NORMAL,
                    cost = "LOW"
                )

                // H4: Environmental / context difference
                hypotheses += Hypothesis(
                    id = "H4_${incident.id}",
                    incidentId = incident.id,
                    description = "Environmental or context difference (ambient conditions, case, orientation)",
                    supportingEvidence = listOf("Cannot be measured by SOVARIX — ambient sensor unavailable"),
                    contradictingEvidence = emptyList(),
                    requiredExperiment = null,
                    risk = Risk.UNKNOWN,
                    cost = "UNSUPPORTED"
                )
            }
            incident.type.contains("MEMORY") -> {
                hypotheses += Hypothesis(
                    id = "H1_${incident.id}",
                    incidentId = incident.id,
                    description = "Background app memory pressure",
                    supportingEvidence = listOf("Android reports lowMemory flag"),
                    risk = Risk.WATCH
                )
            }
            incident.type.contains("BATTERY") -> {
                hypotheses += Hypothesis(
                    id = "H1_${incident.id}",
                    incidentId = incident.id,
                    description = "High workload caused increased battery drain",
                    supportingEvidence = if (state.workload == Workload.GAMING) listOf("Gaming workload active") else listOf("Workload: ${state.workload.name}"),
                    risk = Risk.WATCH
                )
            }
        }

        return hypotheses
    }
}

// =========================================================
// WHY ENGINE (SECTION 21)
// =========================================================

object WhyEngine {
    fun explain(incident: Incident, state: TwinState, overhead: Overhead? = null): WhyReport {
        val facts = mutableListOf<String>()
        val contributors = mutableListOf<PossibleContributor>()
        val supporting = mutableListOf<String>()
        val contradicting = mutableListOf<String>()
        val unknowns = mutableListOf<String>()

        val sample = state.latest
        val temp = sample?.batteryC
        val thermalStatus = sample?.thermalStatus ?: 0
        val headroom = sample?.headroom

        // 1. Observed facts
        if (thermalStatus > 0 || (temp != null && temp >= 38.0)) {
            facts += "Thermal response increased (Current: ${temp?.let { String.format(java.util.Locale.US, "%.1f°C", it) } ?: "elevated"})."
        }
        if (headroom != null && headroom >= 0.8) {
            facts += "Thermal headroom decreased (adpf headroom: ${String.format(java.util.Locale.US, "%.2f", headroom)})."
        }
        if (state.workload == Workload.GAMING) {
            facts += "Workload remained elevated during active gaming session."
        } else if (state.workload != Workload.IDLE) {
            facts += "Device workload was active (${state.workload.name})."
        }
        if (sample?.lowMemory == true) {
            facts += "Android system low-memory signal observed."
        }
        if (state.chargingState == true) {
            facts += "Device was connected to external power source."
        }
        if (facts.isEmpty()) {
            facts += "Telemetry deviation observed relative to context baseline."
        }

        // 2. Possible contributors
        val hasHighWorkload = state.workload == Workload.GAMING || state.workload == Workload.EVERYDAY
        contributors += PossibleContributor(
            title = "1. Sustained workload",
            evidenceRating = if (hasHighWorkload) "strong" else "weak",
            explanation = if (hasHighWorkload)
                "Evidence suggests sustained compute load is correlated with increased thermal dissipation."
            else
                "No sustained high compute workload was active during incident."
        )
        if (hasHighWorkload) supporting += "Workload state consistent with continuous CPU/GPU rendering."

        val hasThermalPressure = thermalStatus >= 2 || (temp != null && temp >= 38.5)
        contributors += PossibleContributor(
            title = "2. Thermal pressure",
            evidenceRating = if (hasThermalPressure) "moderate" else "weak",
            explanation = if (hasThermalPressure)
                "Consistent with heat buildup in physical battery and chassis."
            else
                "Thermal envelope remained within standard baseline variance."
        )
        if (hasThermalPressure) supporting += "Thermal slope correlates with elevated thermal state."

        val hasBgPressure = sample?.lowMemory == true || (overhead?.cpuOneCorePct != null && overhead.cpuOneCorePct > 3.0)
        contributors += PossibleContributor(
            title = "3. Background activity",
            evidenceRating = if (hasBgPressure) "moderate" else "weak",
            explanation = if (hasBgPressure)
                "Correlated with concurrent system tasks or memory reclamation cycles."
            else
                "Evidence indicates minimal background process interference."
        )

        contributors += PossibleContributor(
            title = "4. Unknown factors",
            evidenceRating = "present",
            explanation = "Ambient environmental conditions, device case, and RF signal strength are not exposed through public Android APIs."
        )
        unknowns += "Ambient room temperature (sensor unavailable)"
        unknowns += "Device case thermal conductivity"
        unknowns += "Display panel radiant heat"

        val recommendedTest = when {
            state.workload == Workload.GAMING -> "Run Workload Comparison in Lab: Measure thermal slope with vs without background sync."
            state.chargingState == true -> "Run Charging Impact Test: Compare thermal velocity on battery vs charger."
            else -> "Run Diagnostic Experiment in Lab: Measure idle cooldown slope vs active load."
        }

        return WhyReport(
            title = "WHY DID PERFORMANCE CHANGE?",
            observedFacts = facts,
            possibleContributors = contributors,
            supportingEvidence = supporting,
            contradictingEvidence = contradicting,
            unknownFactors = unknowns,
            recommendedTest = recommendedTest
        )
    }
}

// =========================================================
// GAMING PERFORMANCE ENGINE (SECTION 19)
// =========================================================

object GamingPerformanceEngine {
    fun evaluate(
        state: TwinState,
        sessionElapsedSec: Long,
        baselineTemp: Double?
    ): GamingPerformanceAnomaly? {
        val s = state.latest ?: return null
        val temp = s.batteryC
        val thermalStatus = s.thermalStatus ?: 0
        val headroom = s.headroom
        val batterySlope = state.batteryTrend?.slopePerMinute ?: 0.0

        if (thermalStatus >= 2 || (temp != null && baselineTemp != null && temp - baselineTemp >= 2.5)) {
            val deltaStr = temp?.let { t -> baselineTemp?.let { b -> String.format(java.util.Locale.US, "%+.1f°C", t - b) } } ?: "+2.5°C"
            return GamingPerformanceAnomaly(
                eventType = "THERMAL_EVENT",
                timestamp = s.wallMs,
                summary = "Thermal envelope increased under gaming load ($deltaStr from session start).",
                severity = if (thermalStatus >= 3) Risk.ANOMALY else Risk.WATCH,
                measuredValue = temp,
                baselineValue = baselineTemp,
                evidence = listOf("Thermal status: $thermalStatus", "Slope: ${state.temperatureTrend?.slopePerMinute?.let { String.format(java.util.Locale.US, "%.2f°C/min", it) } ?: "rising"}")
            )
        }

        if (headroom != null && headroom >= 0.85) {
            return GamingPerformanceAnomaly(
                eventType = "HEADROOM_DROP",
                timestamp = s.wallMs,
                summary = "Thermal headroom dropped to critical threshold (${String.format(java.util.Locale.US, "%.2f", headroom)}).",
                severity = Risk.WATCH,
                measuredValue = headroom,
                baselineValue = 0.5,
                evidence = listOf("ADPF thermal headroom reports near-throttling conditions.")
            )
        }

        if (sessionElapsedSec > 60 && batterySlope <= -0.5) {
            return GamingPerformanceAnomaly(
                eventType = "BATTERY_DRAIN_EVENT",
                timestamp = s.wallMs,
                summary = "Abnormal battery drain velocity (${String.format(java.util.Locale.US, "%.1f%%/min", batterySlope)}).",
                severity = Risk.WATCH,
                measuredValue = batterySlope,
                baselineValue = -0.2,
                evidence = listOf("Battery discharge rate exceeds typical game baseline.")
            )
        }

        return null
    }
}

enum class ActionPermission { ALLOWED_AUTOMATICALLY, USER_CONFIRMATION_REQUIRED, NOT_ALLOWED }
enum class TwinAction { REDUCE_OBSERVATION, DIM_THIS_APP, OPEN_DISPLAY_SETTINGS, RECORD_MANUAL_CHANGE, CPU_CONTROL, Q_CHIP_CONTROL }

object ActionPolicy {
    fun permission(action: TwinAction) = when (action) {
        TwinAction.REDUCE_OBSERVATION -> ActionPermission.ALLOWED_AUTOMATICALLY
        TwinAction.DIM_THIS_APP, TwinAction.OPEN_DISPLAY_SETTINGS, TwinAction.RECORD_MANUAL_CHANGE -> ActionPermission.USER_CONFIRMATION_REQUIRED
        TwinAction.CPU_CONTROL, TwinAction.Q_CHIP_CONTROL -> ActionPermission.NOT_ALLOWED
    }
}

object DecisionEngine {
    fun recommend(s: TwinState): String = when {
        !s.running -> "Start an observation session to build your device twin."
        s.policy.stopSession -> "Let the device recover. SOVARIX is stopping its observation session."
        s.anomaly.risk == Risk.ANOMALY -> "Reduce your current workload and allow recovery. SOVARIX has reduced its own observation cost."
        s.anomaly.risk == Risk.WATCH -> "Review the evidence. You can reduce your workload manually and record a before/after comparison."
        s.temperatureTrend == null -> "Collect at least two minutes in one workload and charging state for a first trend."
        else -> "Observe the trajectory, try a sensitivity scenario, then pin a forecast to compare with a future measurement."
    }

    /**
     * Evaluates action governance based on multiple intelligence inputs and TwinState.
     * Enforces strict sandbox limits: only legitimate public Android APIs.
     */
    fun decide(
        s: TwinState,
        forecast: ForecastResult? = null,
        anomaly: AnomalyResult? = null,
        simulation: SimulationResult? = null,
        insights: List<InsightResult> = emptyList()
    ): DecisionResult {
        val recAction: TwinAction?
        val permission: ActionPermission
        val rationale: String

        when {
            !s.running -> {
                recAction = null
                permission = ActionPermission.ALLOWED_AUTOMATICALLY
                rationale = "Start an observation session to build your device twin."
            }
            s.policy.stopSession -> {
                recAction = TwinAction.REDUCE_OBSERVATION
                permission = ActionPolicy.permission(TwinAction.REDUCE_OBSERVATION)
                rationale = "Critical thermal status reported by Android. Stop session and allow recovery."
            }
            anomaly?.risk == Risk.ANOMALY || s.anomaly.risk == Risk.ANOMALY -> {
                recAction = TwinAction.REDUCE_OBSERVATION
                permission = ActionPolicy.permission(TwinAction.REDUCE_OBSERVATION)
                rationale = "Abnormal device behavior detected. Automatically reducing SOVARIX self-observation frequency."
            }
            forecast?.risk == Risk.ANOMALY -> {
                recAction = TwinAction.DIM_THIS_APP
                permission = ActionPolicy.permission(TwinAction.DIM_THIS_APP)
                rationale = "Rapid thermal rise predicted. Recommend dimming app or reducing workload."
            }
            s.temperatureTrend == null -> {
                recAction = null
                permission = ActionPermission.ALLOWED_AUTOMATICALLY
                rationale = "Collect at least two minutes in one workload and charging state for a first trend."
            }
            else -> {
                recAction = null
                permission = ActionPermission.ALLOWED_AUTOMATICALLY
                rationale = "Observe trajectory, test sensitivity scenarios, and pin a forecast to verify."
            }
        }

        return DecisionResult(
            timestamp = s.timestamp.takeIf { it > 0 } ?: (s.latest?.wallMs ?: System.currentTimeMillis()),
            recommendation = rationale,
            recommendedAction = recAction,
            permission = permission,
            rationale = rationale
        )
    }
}

class VerificationEngine(private val config: GovernorConfig = GovernorConfig()) {
    fun resolve(ticket: PredictionTicket, sample: Sample, contextChanged: Boolean): Verification? {
        if (sample.elapsedMs < ticket.dueElapsedMs) return null
        val validTime = sample.elapsedMs - ticket.dueElapsedMs <= config.maxGapMs
        val actual = sample.batteryC.takeIf { validTime }
        val predicted = ticket.forecast.temperature?.value
        return Verification(ticket.id, sample.wallMs, ticket.forecast.horizonMinutes, predicted, actual,
            if (predicted != null && actual != null) actual - predicted else null,
            ticket.forecast.battery?.value, sample.batteryPct.takeIf { validTime }, ticket.action,
            when { !validTime -> "MISSED — sampling gap"; contextChanged -> "OBSERVED — context changed; excluded from calibration"
                actual == null || predicted == null -> "OBSERVED — thermal comparison unavailable"; else -> "VERIFIED" },
            contextChanged, ticket.baselineTemperature, ticket.forecast.temperatureBiasApplied)
    }

    /**
     * Resolves prediction vs actual into VerificationResult.
     */
    fun resolveResult(ticket: PredictionTicket, sample: Sample, contextChanged: Boolean): VerificationResult? {
        val v = resolve(ticket, sample, contextChanged) ?: return null
        return VerificationResult(
            ticketId = v.id,
            timestamp = v.wallMs,
            horizonMinutes = v.horizonMinutes,
            predictedC = v.predictedC,
            actualC = v.actualC,
            signedErrorC = v.signedErrorC,
            predictedBattery = v.predictedBattery,
            actualBattery = v.actualBattery,
            action = v.action,
            status = v.status,
            contextChanged = v.contextChanged,
            baselineTemperature = v.baselineTemperature,
            biasApplied = v.biasApplied,
            confidence = if (v.status == "VERIFIED") 0.95 else 0.50
        )
    }
}

object LearningEngine {
    fun completedSession(dna: DeviceDNA, summaries: List<Sample>): DeviceDNA {
        val baselines = dna.baselineByContext.toMutableMap()
        summaries.forEach { s -> s.batteryC?.let { baselines[AnomalyEngine.contextKey(s)] =
            (baselines[AnomalyEngine.contextKey(s)] ?: Baseline()).add(it) } }
        return dna.copy(sessions = dna.sessions + 1, observations = dna.observations + summaries.size, baselineByContext = baselines)
    }

    fun verified(dna: DeviceDNA, v: Verification): DeviceDNA {
        val err = v.signedErrorC ?: return dna
        if (v.status != "VERIFIED" || v.contextChanged || !err.isFinite()) return dna
        // Correct raw-model bias; do not add the same residual twice to a moving correction.
        val old = dna.temperatureBiasByHorizon[v.horizonMinutes] ?: 0.0
        val targetBias = v.biasApplied + err
        val updated = (0.8 * old + 0.2 * targetBias).coerceIn(-3.0, 3.0)
        return dna.copy(temperatureBiasByHorizon = dna.temperatureBiasByHorizon + (v.horizonMinutes to updated),
            verifiedCount = dna.verifiedCount + 1, totalAbsoluteErrorC = dna.totalAbsoluteErrorC + abs(err))
    }

    fun verified(dna: DeviceDNA, v: VerificationResult): DeviceDNA {
        val err = v.signedErrorC ?: return dna
        if (v.status != "VERIFIED" || v.contextChanged || !err.isFinite()) return dna
        val old = dna.temperatureBiasByHorizon[v.horizonMinutes] ?: 0.0
        val targetBias = v.biasApplied + err
        val updated = (0.8 * old + 0.2 * targetBias).coerceIn(-3.0, 3.0)
        return dna.copy(temperatureBiasByHorizon = dna.temperatureBiasByHorizon + (v.horizonMinutes to updated),
            verifiedCount = dna.verifiedCount + 1, totalAbsoluteErrorC = dna.totalAbsoluteErrorC + abs(err))
    }
}

interface ExplanationProvider { fun explain(state: TwinState): String }
class EvidenceExplanation : ExplanationProvider {
    override fun explain(state: TwinState): String {
        return LocalAIEngine.handleQuery("explain twin state", state)
    }
}

// =========================================================
// LOCAL AI ENGINE (SECTION 14)
// =========================================================

enum class LocalAIIntentType {
    SIMULATE,
    EXPLAIN_TWIN,
    EXPLAIN_FORECAST,
    EXPLAIN_ANOMALY,
    SUMMARIZE_BLACKBOX,
    EXPLAIN_OVERHEAD,
    EXPLAIN_DNA,
    EXPLAIN_AUTOPILOT,
    UNSUPPORTED_SIMULATION,
    GENERAL_QUERY
}

data class LocalAIIntent(
    val type: LocalAIIntentType,
    val simulationRequest: SimulationRequest? = null,
    val unsupportedVariable: String? = null,
    val rawQuery: String = ""
)

/**
 * On-device Local AI Engine:
 * Strictly zero cloud LLMs, zero Gemini API, zero Qwen.
 * Never in the continuous telemetry hot path.
 *
 * Roles:
 * 1. Understands natural-language user questions.
 * 2. Converts what-if questions into structured simulation requests.
 * 3. Explains actual numerical results (never hallucinates numbers).
 * 4. Summarizes Black Box events.
 * 5. Explains anomalies and forecasts.
 */
object LocalAIEngine {
    fun parseIntent(query: String): LocalAIIntent {
        val q = query.trim().lowercase(Locale.US)
        return when {
            // Check for unmodeled / unsupported simulation variables first
            q.contains("overclock") || q.contains("clock") || q.contains("ghz") -> {
                LocalAIIntent(
                    LocalAIIntentType.UNSUPPORTED_SIMULATION,
                    unsupportedVariable = "CPU/GPU Overclocking",
                    rawQuery = query
                )
            }
            q.contains("governor") || q.contains("kernel") -> {
                LocalAIIntent(
                    LocalAIIntentType.UNSUPPORTED_SIMULATION,
                    unsupportedVariable = "Kernel CPU Governor",
                    rawQuery = query
                )
            }
            q.contains("q-chip") || q.contains("qchip") || q.contains("display chip") -> {
                LocalAIIntent(
                    LocalAIIntentType.UNSUPPORTED_SIMULATION,
                    unsupportedVariable = "Proprietary Display Coprocessor",
                    rawQuery = query
                )
            }
            q.contains("root") || q.contains("bypass") || q.contains("disable throttling") -> {
                LocalAIIntent(
                    LocalAIIntentType.UNSUPPORTED_SIMULATION,
                    unsupportedVariable = "Privileged Thermal Throttling Bypass",
                    rawQuery = query
                )
            }

            // Check for supported What-If simulations
            q.contains("fps") || q.contains("frame rate") || q.contains("60") || q.contains("90") || q.contains("120") -> {
                val target = when {
                    q.contains("60") -> "60"
                    q.contains("90") -> "90"
                    q.contains("120") -> "120"
                    else -> "60"
                }
                LocalAIIntent(
                    LocalAIIntentType.SIMULATE,
                    simulationRequest = SimulationRequest(ControllableLever.FPS_CAP, target),
                    rawQuery = query
                )
            }
            q.contains("workload") || q.contains("intensity") || q.contains("reduce load") || q.contains("graphics") -> {
                val target = when {
                    q.contains("25") -> "25%"
                    q.contains("75") -> "75%"
                    q.contains("100") -> "100%"
                    else -> "50%"
                }
                LocalAIIntent(
                    LocalAIIntentType.SIMULATE,
                    simulationRequest = SimulationRequest(ControllableLever.WORKLOAD_INTENSITY, target),
                    rawQuery = query
                )
            }
            q.contains("dim") || q.contains("brightness") || q.contains("screen") -> {
                LocalAIIntent(
                    LocalAIIntentType.SIMULATE,
                    simulationRequest = SimulationRequest(ControllableLever.APP_DIMMING, "20%"),
                    rawQuery = query
                )
            }
            q.contains("resolution") || q.contains("720p") || q.contains("1080p") -> {
                val target = if (q.contains("720")) "720p" else "1080p"
                LocalAIIntent(
                    LocalAIIntentType.SIMULATE,
                    simulationRequest = SimulationRequest(ControllableLever.RESOLUTION_SCALE, target),
                    rawQuery = query
                )
            }

            // Explanations & Diagnostics (English, Telugu, Hindi)
            q.contains("anomaly") || q.contains("hot") || q.contains("heat") || q.contains("thermal") || q.contains("spike") ||
                q.contains("వేడెక్కుతోంది") || q.contains("వేడి") || q.contains("గర్మ") || q.contains("गर्म") || q.contains("तापमान") ->
                LocalAIIntent(LocalAIIntentType.EXPLAIN_ANOMALY, rawQuery = query)
            q.contains("future") || q.contains("forecast") || q.contains("predict") || q.contains("next") ||
                q.contains("భవిష్యత్తు") || q.contains("అంచనా") || q.contains("भविष्य") || q.contains("पूर्वानुमान") ->
                LocalAIIntent(LocalAIIntentType.EXPLAIN_FORECAST, rawQuery = query)
            q.contains("black box") || q.contains("history") || q.contains("log") || q.contains("evidence") || q.contains("what happened") ||
                q.contains("మెమొరీ") || q.contains("చరిత్ర") || q.contains("इतिहास") || q.contains("मेमोरी") ->
                LocalAIIntent(LocalAIIntentType.SUMMARIZE_BLACKBOX, rawQuery = query)
            q.contains("overhead") || q.contains("cost") || q.contains("battery drain") || q.contains("footprint") ||
                q.contains("ఖర్చు") || q.contains("వినియోగం") || q.contains("खपत") || q.contains("लागत") ->
                LocalAIIntent(LocalAIIntentType.EXPLAIN_OVERHEAD, rawQuery = query)
            q.contains("dna") || q.contains("behavior") || q.contains("insight") || q.contains("pattern") ||
                q.contains("ప్రవర్తన") || q.contains("పోకడ") || q.contains("व्यवहार") || q.contains("पैटर्न") ->
                LocalAIIntent(LocalAIIntentType.EXPLAIN_DNA, rawQuery = query)
            q.contains("autopilot") || q.contains("goal") || q.contains("policy") ||
                q.contains("లక్ష్యం") || q.contains("లక్ష్యాలు") || q.contains("लक्ष्य") || q.contains("ऑटोपायलट") ->
                LocalAIIntent(LocalAIIntentType.EXPLAIN_AUTOPILOT, rawQuery = query)
            else ->
                LocalAIIntent(LocalAIIntentType.EXPLAIN_TWIN, rawQuery = query)
        }
    }

    /** Dispatches and explains a user natural language query */
    fun handleQuery(
        query: String,
        state: TwinState,
        forecast: ForecastResult? = null,
        anomaly: AnomalyResult? = null,
        blackBoxEvents: List<BlackBoxEvent> = emptyList()
    ): String {
        val intent = parseIntent(query)
        return when (intent.type) {
            LocalAIIntentType.UNSUPPORTED_SIMULATION -> {
                val variable = intent.unsupportedVariable ?: "Requested variable"
                "Local AI · Simulation Rejected\n\n" +
                "Variable '$variable' cannot be controlled or reliably modeled via public Android APIs.\n\n" +
                "SOVARIX strictly refuses to fabricate simulations for unmodeled hardware settings or privileged system controls (no root, no undocumented hacks).\n\n" +
                "Supported controllable levers:\n" +
                "• FPS Cap (60, 90, 120 FPS)\n" +
                "• Workload Intensity (25%, 50%, 75%)\n" +
                "• App Dimming (20%, 50%)\n" +
                "• Resolution Scale (720p, 1080p)"
            }
            LocalAIIntentType.SIMULATE -> {
                val req = intent.simulationRequest ?: SimulationRequest(ControllableLever.FPS_CAP, "60")
                val outcome = SimulationEngine.simulateLever(state, req)
                explainSimulation(outcome)
            }
            LocalAIIntentType.EXPLAIN_ANOMALY -> {
                val a = anomaly ?: AnomalyEngine.evaluate(state)
                explainAnomaly(a)
            }
            LocalAIIntentType.EXPLAIN_FORECAST -> {
                val f = forecast ?: FutureEngine().evaluate(state)
                explainForecast(f)
            }
            LocalAIIntentType.SUMMARIZE_BLACKBOX -> {
                summarizeBlackBox(blackBoxEvents)
            }
            LocalAIIntentType.EXPLAIN_OVERHEAD -> {
                explainOverhead(state.overhead)
            }
            LocalAIIntentType.EXPLAIN_DNA -> {
                explainDeviceDNA(state.dna)
            }
            LocalAIIntentType.EXPLAIN_AUTOPILOT -> {
                explainAutopilot(state)
            }
            LocalAIIntentType.EXPLAIN_TWIN, LocalAIIntentType.GENERAL_QUERY -> {
                explainTwinState(state, forecast, anomaly)
            }
        }
    }

    fun explainDeviceDNA(dna: DeviceDNA): String {
        val insights = dna.behaviorModel.generateInsights()
        val scores = dna.behaviorModel.computeDNAScores()
        return "Local AI · Device DNA 2.0\n\n" +
            "Maturity: ${dna.maturity} (${dna.sessions} sessions, ${dna.behaviorModel.totalObservations} observations)\n\n" +
            "Behavioral Ratings:\n" +
            "• Thermal Response: ${(scores.thermalResponse * 100).toInt()}%\n" +
            "• Gaming Endurance: ${(scores.gamingEndurance * 100).toInt()}%\n" +
            "• Recovery Behavior: ${(scores.recoveryBehavior * 100).toInt()}%\n" +
            "• Battery Response: ${(scores.batteryResponse * 100).toInt()}%\n\n" +
            "Evidence-Backed Insights:\n" +
            insights.joinToString("\n") { "• $it" }
    }

    fun explainAutopilot(state: TwinState): String {
        return "Local AI · Phone Autopilot\n\n" +
            "Active Device Goal: ${state.deviceGoal.title}\n" +
            "Current State: ${state.productState.label}\n\n" +
            "Rationale:\n" +
            (state.autopilotDecision?.rationale ?: "Observing device telemetry within goal constraints.") + "\n\n" +
            "Permitted Actions Active:\n" +
            (state.autopilotDecision?.permittedActions?.joinToString("\n") { "• ${it.actionName}: ${it.description}" } ?: "• Nominal observation")
    }

    fun explainSimulation(outcome: SimulationOutcome): String = when (outcome) {
        is SimulationUnsupported -> {
            "Local AI · Simulation Unsupported\n\n" +
            "Cannot simulate '${outcome.variable}'.\n" +
            outcome.reason + "\n\n" +
            "SOVARIX does not fabricate unmodeled system predictions."
        }
        is SimulationResult -> {
            val leverName = outcome.activeLever?.label ?: "Workload intervention"
            val targetVal = outcome.requestedValue ?: "selected level"
            val base = outcome.scenarios.find { it.id == "scenario_a" }
            val counter = outcome.scenarios.find { it.id == "scenario_b" || it.id.startsWith("scenario_lever") } ?: outcome.recommendedScenario
            val baseTemp = base?.predictedTemperatureC
            val simTemp = counter?.predictedTemperatureC

            val tempDiff = if (baseTemp != null && simTemp != null) baseTemp - simTemp else null
            val diffStr = if (tempDiff != null) String.format(Locale.US, "%.1f°C lower", tempDiff) else "cooler"

            "Local AI · What-If Explanation\n\n" +
            "Scenario: Adjusting $leverName to $targetVal.\n\n" +
            "Numerical Outcome:\n" +
            "• Current Trajectory: ${baseTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable"}\n" +
            "• Counterfactual Projection: ${simTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable"} ($diffStr)\n" +
            "• Projected Thermal Risk: ${counter?.thermalRisk?.name ?: "NORMAL"}\n" +
            "• Battery Impact: ${counter?.predictedBatteryPct?.let { String.format(Locale.US, "%.1f%% remaining", it) } ?: "Consistent"}\n\n" +
            "Evaluation:\n" +
            (counter?.explanation ?: "The counterfactual model predicts reduced thermal climb.") + "\n\n" +
            "Note: Evaluated on a cloned copy of TwinState. Real smartphone hardware is unchanged."
        }
    }

    fun explainAnomaly(a: AnomalyResult): String {
        val devStr = a.deviation?.let { String.format(Locale.US, "%+.1f°C vs recorded baseline", it) } ?: "no baseline deviation"
        return "Local AI · Anomaly Analysis\n\n" +
            "Status: ${a.type} (${a.severity})\n" +
            "Observed Battery Temp: ${a.observedValue?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable"}\n" +
            "Context Baseline: ${a.baselineValue?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Not established"} ($devStr)\n\n" +
            "Evidence Facts:\n" +
            (if (a.evidence.isNotEmpty()) a.evidence.joinToString("\n") { "• $it" } else "• Telemetry is within normal parameters.")
    }

    fun explainForecast(f: ForecastResult): String {
        return "Local AI · Forecast Horizon (+${f.horizonMinutes} min)\n\n" +
            "Predicted Temperature: ${f.predictedTemperature?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Collecting history..."}\n" +
            "Predicted Battery Level: ${f.predictedBattery?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Unavailable"}\n" +
            "Confidence: ${(f.confidence * 100).toInt()}%\n" +
            "Risk Assessment: ${f.risk.name}\n\n" +
            "Methodology: ${f.evidence}"
    }

    fun summarizeBlackBox(events: List<BlackBoxEvent>): String {
        if (events.isEmpty()) return "Local AI · Black Box Summary\n\nNo recorded events in the audit ledger yet."
        val count = events.size
        val verifications = events.filter { it.eventType == "VERIFICATION" }
        val moments = events.filter { it.eventType == "GAMING_MOMENT" }
        val interventions = events.filter { it.eventType == "INTERVENTION" }

        val avgError = verifications.mapNotNull { it.predictionError }.takeIf { it.isNotEmpty() }?.map { abs(it) }?.average()

        return "Local AI · Black Box Ledger Summary\n\n" +
            "Total Events: $count\n" +
            "• Predictions Verified: ${verifications.size}\n" +
            "• Average Prediction Error: ${avgError?.let { String.format(Locale.US, "%.2f°C", it) } ?: "Pending validation"}\n" +
            "• Gaming Moments Captured: ${moments.size}\n" +
            "• Logged Interventions: ${interventions.size}\n\n" +
            "Latest Event: ${events.last().eventType} at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(events.last().timestamp))}"
    }

    fun explainOverhead(o: Overhead?): String {
        if (o == null) return "Local AI · Overhead Footprint\n\nObservation session not currently active."
        return "Local AI · Self-Measurement Footprint\n\n" +
            "SOVARIX monitors its own resource cost:\n" +
            "• CPU Cost: ${o.cpuOneCorePct?.let { String.format(Locale.US, "%.1f%% of one core", it) } ?: "Calculating..."}\n" +
            "• Memory (PSS): ${o.pssMb?.let { String.format(Locale.US, "%.1f MB", it) } ?: "Measuring..."}\n" +
            "• Telemetry Sampling Latency: ${String.format(Locale.US, "%.2f ms", o.processingMs)}\n" +
            "• Temperature Delta: ${o.temperatureDeltaC?.let { String.format(Locale.US, "%+.1f°C", it) } ?: "Negligible"}\n" +
            "• Polling Mode: ${o.scheduledIntervalMs / 1000}s adaptive"
    }

    fun explainTwinState(s: TwinState, f: ForecastResult? = null, a: AnomalyResult? = null): String {
        val t = s.temperature?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable"
        val b = s.battery?.let { String.format(Locale.US, "%.0f%%", it) } ?: "Unavailable"
        val status = a?.type ?: s.anomaly.risk.name
        return "Local AI · Twin State Synthesis\n\n" +
            "Device Status: $status\n" +
            "Battery: $b · Battery Temp: $t\n" +
            "Workload: ${s.workload.name} · Motion Intensity: ${String.format(Locale.US, "%.2f", s.motionIntensity)}\n" +
            "Performance State: ${s.performanceState}\n\n" +
            "Observation Mode: ${s.policy.mode} (${s.policy.reason})\n\n" +
            DecisionEngine.recommend(s)
    }
}

