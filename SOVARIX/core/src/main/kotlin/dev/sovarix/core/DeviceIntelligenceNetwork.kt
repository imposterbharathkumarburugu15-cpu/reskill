package dev.sovarix.core

/**
 * Privacy Policy guarantees for future Fleet Learning:
 * - Strictly LOCAL-FIRST: all inferences and learning run locally on device.
 * - ZERO upload by default.
 * - Explicit opt-in only.
 * - Never uploads raw video, audio, screen frames, or private telemetry.
 * - Only anonymized statistical parameter aggregations (e.g. mean cooling rate per SoC model).
 */
data class FleetLearningPrivacyPolicy(
    val optInEnabled: Boolean = false,
    val localFirstGuaranteed: Boolean = true,
    val zeroRawDataUpload: Boolean = true,
    val anonymizeDeviceIdentifier: Boolean = true
)

/**
 * Anonymous aggregated model patterns across device classes.
 */
data class AnonymousAggregatedPatterns(
    val deviceSoc: String,
    val averageGamingHeatingRateCPerMin: Double,
    val averageRecoveryCoolingRateCPerMin: Double,
    val sampleSessionCount: Int,
    val modelConfidence: Double
)

/**
 * Interface contract for future anonymous fleet intelligence calibration.
 */
interface DeviceIntelligenceNetwork {
    fun getPrivacyPolicy(): FleetLearningPrivacyPolicy
    fun setOptIn(enabled: Boolean)
    fun getFleetPatterns(deviceSoc: String): AnonymousAggregatedPatterns?
    fun exportAnonymousSummary(model: DeviceBehaviorModel): Map<String, Any>?
}

/**
 * Default local-first implementation: offline-safe, zero networking.
 */
class LocalOnlyIntelligenceNetwork : DeviceIntelligenceNetwork {
    private var policy = FleetLearningPrivacyPolicy()

    override fun getPrivacyPolicy(): FleetLearningPrivacyPolicy = policy

    override fun setOptIn(enabled: Boolean) {
        policy = policy.copy(optInEnabled = enabled)
    }

    override fun getFleetPatterns(deviceSoc: String): AnonymousAggregatedPatterns? {
        // Safe local offline defaults per major SoC families
        return when {
            deviceSoc.contains("Snapdragon", ignoreCase = true) || deviceSoc.contains("SM8", ignoreCase = true) ->
                AnonymousAggregatedPatterns(deviceSoc, 0.26, 0.24, 1500, 0.90)
            deviceSoc.contains("Dimensity", ignoreCase = true) || deviceSoc.contains("MT6", ignoreCase = true) ->
                AnonymousAggregatedPatterns(deviceSoc, 0.28, 0.22, 1200, 0.88)
            else -> null
        }
    }

    override fun exportAnonymousSummary(model: DeviceBehaviorModel): Map<String, Any>? {
        if (!policy.optInEnabled) return null
        return mapOf(
            "obs" to model.totalObservations,
            "gaming_heat" to (model.workloadHeatingRates[Workload.GAMING] ?: 0.28),
            "recovery_cool" to (model.interventionRecoveryRateCPerMin ?: 0.22),
            "recording_delta" to (model.recordingThermalDeltaCPerMin ?: 0.0)
        )
    }
}
