package dev.sovarix.app.thermal

import dev.sovarix.core.CoolingActionCapabilities
import dev.sovarix.core.ThermalCapabilities

/**
 * Section 18: Hardware adaptation abstraction.
 * Allows vendor extensions where legitimate public SDKs exist, while falling back gracefully.
 */
interface ThermalAdapter {
    val name: String
    val isVendorSpecific: Boolean
    val statusDescription: String
    fun getCapabilities(base: ThermalCapabilities): ThermalCapabilities
    fun getActionCapabilities(): CoolingActionCapabilities
}

/**
 * Standard implementation using official public Android APIs.
 * Runs safely on any certified Android device (including iQOO, Pixel, Samsung, etc.).
 */
class StandardAndroidThermalAdapter : ThermalAdapter {
    override val name: String = "Standard Android Thermal Adapter"
    override val isVendorSpecific: Boolean = false
    override val statusDescription: String = "Using official Android PowerManager and BatteryManager APIs"

    override fun getCapabilities(base: ThermalCapabilities): ThermalCapabilities = base

    override fun getActionCapabilities(): CoolingActionCapabilities = CoolingActionCapabilities(
        canReduceSovarixWorkload = true,
        canReduceCaptureRate = true,
        canStopNonEssentialSovarixProcessing = true,
        canAdjustOwnRendering = true,
        canAdjustOwnFrameRate = true,
        canUseSupportedGamePerformanceAPI = false,
        canRequestUserSystemSetting = true,
        canUseOfficialOEMSDK = false
    )
}

/**
 * Section 18: OEM / Vendor Thermal Adapter.
 * Inspects whether verified public vendor SDKs (e.g. vivo/iQOO Game SDK, Qualcomm Performance SDK) are present.
 * Never claims or fakes unsupported proprietary hardware controls.
 */
class VendorThermalAdapter(
    private val manufacturer: String,
    private val model: String
) : ThermalAdapter {

    private val isIqooOrVivo = manufacturer.contains("vivo", ignoreCase = true) ||
            manufacturer.contains("iqoo", ignoreCase = true) ||
            model.contains("iqoo", ignoreCase = true)

    // Check for any public vendor performance libraries via reflection
    private val hasVerifiedVendorSDK: Boolean = runCatching {
        // e.g. com.vivo.gamemode or com.iqoo.thermal.sdk
        Class.forName("com.vivo.gamemode.GameModeService")
        true
    }.getOrDefault(false)

    override val name: String = if (isIqooOrVivo) "iQOO/vivo Hardware Adapter" else "Generic Hardware Adapter"
    override val isVendorSpecific: Boolean = isIqooOrVivo

    override val statusDescription: String = when {
        isIqooOrVivo && hasVerifiedVendorSDK -> "Verified OEM Game SDK connected"
        isIqooOrVivo -> "iQOO device detected · Standard Android API fallback active (No public OEM thermal SDK exposed)"
        else -> "Standard Android fallback (Non-OEM device)"
    }

    override fun getCapabilities(base: ThermalCapabilities): ThermalCapabilities {
        return base.copy(
            // Only claim vendor capabilities if verified public interface exists
            cpuHeadroomAvailable = base.cpuHeadroomAvailable && hasVerifiedVendorSDK
        )
    }

    override fun getActionCapabilities(): CoolingActionCapabilities {
        return CoolingActionCapabilities(
            canReduceSovarixWorkload = true,
            canReduceCaptureRate = true,
            canStopNonEssentialSovarixProcessing = true,
            canAdjustOwnRendering = true,
            canAdjustOwnFrameRate = true,
            canUseSupportedGamePerformanceAPI = hasVerifiedVendorSDK,
            canRequestUserSystemSetting = true,
            canUseOfficialOEMSDK = hasVerifiedVendorSDK
        )
    }
}
