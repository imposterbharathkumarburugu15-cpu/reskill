package dev.sovarix.core

import kotlin.math.abs

/**
 * Experiment Engine (Sections 11, 12, 13, 14, 45)
 *
 * Manages the lifecycle of safe diagnostic experiments:
 * PLANNED → AWAITING_CONSENT → RUNNING_BASELINE → RUNNING_TREATMENT → COMPLETED
 *
 * Safety constraints enforced at every observation:
 * - Abort on thermal status >= 3
 * - Abort on battery < 10%
 * - Maximum duration bounded
 * - All changes reversible
 */
class ExperimentEngine {

    private val experiments = mutableListOf<Experiment>()
    private var activeExperiment: Experiment? = null
    private var phaseStartMs: Long = 0L

    // =========================================================
    // EXPERIMENT CATALOG — available safe experiments
    // =========================================================

    fun getAvailableExperiments(): List<Experiment> = listOf(
        Experiment(
            id = "EXP_CAPTURE_OVERHEAD",
            title = "Capture Overhead Test",
            question = "Does SOVARIX observation/capture overhead contribute to thermal increase?",
            independentVariable = "SOVARIX observation mode",
            controlCondition = "Economy observation (reduced sensors, no capture)",
            treatmentCondition = "Full observation (all sensors, capture active)",
            baselineDurationMs = 15_000,
            treatmentDurationMs = 15_000,
            measurements = listOf("temperature", "cpu", "memory", "battery"),
            safetyConstraints = listOf("Abort on thermal status >= 3", "Abort on battery < 10%"),
            consentRequired = true,
            status = ExperimentStatus.PLANNED
        ),
        Experiment(
            id = "EXP_SAMPLING_RATE",
            title = "Sampling Rate Impact Test",
            question = "Does higher sampling frequency measurably affect device temperature?",
            independentVariable = "Sampling interval",
            controlCondition = "30-second sampling interval (LOW_POWER)",
            treatmentCondition = "5-second sampling interval (HIGH_ACTIVITY)",
            baselineDurationMs = 15_000,
            treatmentDurationMs = 15_000,
            measurements = listOf("temperature", "cpu", "memory"),
            safetyConstraints = listOf("Abort on thermal status >= 3", "Abort on battery < 10%"),
            consentRequired = true,
            status = ExperimentStatus.PLANNED
        )
    )

    // =========================================================
    // LIFECYCLE
    // =========================================================

    fun startExperiment(experimentId: String): Experiment? {
        if (activeExperiment != null) return null // One at a time
        val template = getAvailableExperiments().find { it.id == experimentId } ?: return null
        val experiment = template.copy(
            status = ExperimentStatus.RUNNING_BASELINE,
            startedAt = System.currentTimeMillis()
        )
        activeExperiment = experiment
        phaseStartMs = System.currentTimeMillis()
        return experiment
    }

    fun getActiveExperiment(): Experiment? = activeExperiment

    /**
     * Feed a telemetry observation during an active experiment.
     * Returns the updated experiment (may transition phases or complete).
     */
    fun observe(sample: Sample, overhead: Overhead?): Experiment? {
        val exp = activeExperiment ?: return null
        val now = System.currentTimeMillis()

        // Safety check
        if ((sample.thermalStatus ?: 0) >= 3 || (sample.batteryPct ?: 100.0) < 10.0) {
            return cancelExperiment("Safety constraint: thermal=${sample.thermalStatus}, battery=${sample.batteryPct}")
        }

        val observation = ExperimentObservation(
            timestamp = now,
            phase = if (exp.status == ExperimentStatus.RUNNING_BASELINE) "BASELINE" else "TREATMENT",
            temperature = sample.batteryC,
            cpuPct = overhead?.cpuOneCorePct,
            memoryPssMb = overhead?.pssMb,
            batteryPct = sample.batteryPct,
            thermalStatus = sample.thermalStatus
        )

        val updated = when (exp.status) {
            ExperimentStatus.RUNNING_BASELINE -> {
                val withObs = exp.copy(baselineObservations = exp.baselineObservations + observation)
                if (now - phaseStartMs >= exp.baselineDurationMs) {
                    // Transition to treatment phase
                    phaseStartMs = now
                    withObs.copy(status = ExperimentStatus.RUNNING_TREATMENT)
                } else {
                    withObs
                }
            }
            ExperimentStatus.RUNNING_TREATMENT -> {
                val withObs = exp.copy(treatmentObservations = exp.treatmentObservations + observation)
                if (now - phaseStartMs >= exp.treatmentDurationMs) {
                    // Experiment complete — analyze
                    val result = analyze(withObs)
                    withObs.copy(
                        status = ExperimentStatus.COMPLETED,
                        endedAt = now,
                        result = result,
                        evidenceLevel = result.evidenceLevel
                    )
                } else {
                    withObs
                }
            }
            else -> exp
        }

        activeExperiment = if (updated.status == ExperimentStatus.COMPLETED) {
            experiments.add(updated)
            null
        } else {
            updated
        }
        return updated
    }

    fun cancelExperiment(reason: String = "User cancelled"): Experiment? {
        val exp = activeExperiment ?: return null
        val cancelled = exp.copy(
            status = ExperimentStatus.CANCELLED,
            endedAt = System.currentTimeMillis(),
            result = ExperimentResult(
                experimentId = exp.id,
                conclusion = "Cancelled: $reason",
                evidenceLevel = EvidenceLevel.UNKNOWN,
                uncertainty = "Experiment did not complete"
            )
        )
        experiments.add(cancelled)
        activeExperiment = null
        return cancelled
    }

    // =========================================================
    // ANALYSIS
    // =========================================================

    private fun analyze(experiment: Experiment): ExperimentResult {
        val baseline = experiment.baselineObservations
        val treatment = experiment.treatmentObservations

        if (baseline.size < 3 || treatment.size < 3) {
            return ExperimentResult(
                experimentId = experiment.id,
                controlSamples = baseline.size,
                treatmentSamples = treatment.size,
                conclusion = "Insufficient observations for analysis",
                evidenceLevel = EvidenceLevel.UNKNOWN,
                uncertainty = "Need at least 3 observations per phase"
            )
        }

        // Temperature analysis
        val controlTemps = baseline.mapNotNull { it.temperature }
        val treatmentTemps = treatment.mapNotNull { it.temperature }
        val controlMeanTemp = controlTemps.takeIf { it.isNotEmpty() }?.average()
        val treatmentMeanTemp = treatmentTemps.takeIf { it.isNotEmpty() }?.average()
        val tempEffect = if (controlMeanTemp != null && treatmentMeanTemp != null) treatmentMeanTemp - controlMeanTemp else null

        // CPU analysis
        val controlCpu = baseline.mapNotNull { it.cpuPct }
        val treatmentCpu = treatment.mapNotNull { it.cpuPct }
        val controlMeanCpu = controlCpu.takeIf { it.isNotEmpty() }?.average()
        val treatmentMeanCpu = treatmentCpu.takeIf { it.isNotEmpty() }?.average()
        val cpuEffect = if (controlMeanCpu != null && treatmentMeanCpu != null) treatmentMeanCpu - controlMeanCpu else null

        // Memory analysis
        val controlMem = baseline.mapNotNull { it.memoryPssMb }
        val treatmentMem = treatment.mapNotNull { it.memoryPssMb }
        val controlMeanMem = controlMem.takeIf { it.isNotEmpty() }?.average()
        val treatmentMeanMem = treatmentMem.takeIf { it.isNotEmpty() }?.average()
        val memEffect = if (controlMeanMem != null && treatmentMeanMem != null) treatmentMeanMem - controlMeanMem else null

        // Evidence level determination
        val evidenceLevel = when {
            tempEffect != null && abs(tempEffect) > 0.3 -> EvidenceLevel.INTERVENTION_SUPPORTED
            cpuEffect != null && abs(cpuEffect) > 1.0 -> EvidenceLevel.INTERVENTION_SUPPORTED
            tempEffect != null || cpuEffect != null -> EvidenceLevel.CORRELATED
            else -> EvidenceLevel.UNKNOWN
        }

        // Build conclusion
        val conclusions = mutableListOf<String>()
        if (tempEffect != null) {
            conclusions += String.format(java.util.Locale.US,
                "Temperature: treatment was %+.2f°C vs control (control=%.1f°C, treatment=%.1f°C)",
                tempEffect, controlMeanTemp!!, treatmentMeanTemp!!)
        }
        if (cpuEffect != null) {
            conclusions += String.format(java.util.Locale.US,
                "CPU: treatment was %+.1f%% vs control (control=%.1f%%, treatment=%.1f%%)",
                cpuEffect, controlMeanCpu!!, treatmentMeanCpu!!)
        }
        if (memEffect != null) {
            conclusions += String.format(java.util.Locale.US,
                "Memory PSS: treatment was %+.1f MB vs control (control=%.1f MB, treatment=%.1f MB)",
                memEffect, controlMeanMem!!, treatmentMeanMem!!)
        }

        val uncertaintyFactors = mutableListOf<String>()
        if (baseline.size < 6) uncertaintyFactors += "Limited baseline samples (${baseline.size})"
        if (treatment.size < 6) uncertaintyFactors += "Limited treatment samples (${treatment.size})"
        uncertaintyFactors += "Single-run experiment; repeated runs would increase confidence"

        return ExperimentResult(
            experimentId = experiment.id,
            controlMeanTemp = controlMeanTemp,
            treatmentMeanTemp = treatmentMeanTemp,
            controlMeanCpu = controlMeanCpu,
            treatmentMeanCpu = treatmentMeanCpu,
            controlMeanMemory = controlMeanMem,
            treatmentMeanMemory = treatmentMeanMem,
            temperatureEffectSize = tempEffect,
            cpuEffectSize = cpuEffect,
            memoryEffectSize = memEffect,
            controlSamples = baseline.size,
            treatmentSamples = treatment.size,
            conclusion = conclusions.joinToString(". "),
            evidenceLevel = evidenceLevel,
            uncertainty = uncertaintyFactors.joinToString("; ")
        )
    }

    // =========================================================
    // HISTORY
    // =========================================================

    fun getCompletedExperiments(): List<Experiment> = experiments.filter { it.status == ExperimentStatus.COMPLETED }
    fun getAllExperiments(): List<Experiment> = experiments.toList()

    /**
     * Convert experiment results to CausalRelationships for DeviceDNA.
     */
    fun toCausalRelationships(): List<CausalRelationship> {
        return getCompletedExperiments().mapNotNull { exp ->
            val result = exp.result ?: return@mapNotNull null
            if (result.evidenceLevel == EvidenceLevel.UNKNOWN) return@mapNotNull null
            CausalRelationship(
                cause = exp.independentVariable,
                effect = "temperature/cpu/memory",
                observationalSessions = 0,
                controlledExperiments = 1,
                evidenceStrength = result.evidenceLevel,
                effectEstimate = result.temperatureEffectSize,
                uncertainty = result.uncertainty,
                lastUpdated = exp.endedAt ?: System.currentTimeMillis()
            )
        }
    }
}
