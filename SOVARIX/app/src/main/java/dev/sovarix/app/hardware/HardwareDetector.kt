package dev.sovarix.app.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import dev.sovarix.app.gaming.haptics.HapticCapabilityManager
import dev.sovarix.core.*

/** Extend with a documented vendor SDK adapter only after verifying its actual runtime result. */
interface VendorCapabilityProbe { fun inspect(): VendorEvidence? }
data class VendorEvidence(val chipName: String, val telemetryAccessible: Boolean, val evidence: String)

class HardwareDetector(
    private val context: Context? = null,
    private val vendorProbe: VendorCapabilityProbe? = null
) {
    fun inspect(): CapabilityProfile {
        val vendor = runCatching { vendorProbe?.inspect() }.getOrNull()
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeUnless { it == Build.UNKNOWN } else null

        val sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasGyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val hasAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasLinAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null
        val hasRotVec = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

        val hapticCaps = context?.let { ctx: Context -> runCatching { HapticCapabilityManager(ctx).audit() }.getOrNull() }
        val hasHaptics = hapticCaps?.hardwareAvailable == true
        val canObserveHaptics = hapticCaps?.canObserveExternalEvents == true

        return CapabilityProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            soc = soc,
            abi = Build.SUPPORTED_ABIS.joinToString(),
            logicalCores = Runtime.getRuntime().availableProcessors(),
            secondaryChip = if (vendor == null) Availability.UNKNOWN else Availability.AVAILABLE,
            secondaryChipName = vendor?.chipName,
            vendorTelemetryVerified = vendor?.telemetryAccessible == true,
            evidence = vendor?.evidence ?: "Android does not expose a generic iQOO Q-chip detector. No vendor SDK adapter is bundled. Standard twin remains active.",
            gyroAvailable = hasGyro,
            accelerometerAvailable = hasAccel,
            linearAccelerationAvailable = hasLinAccel,
            rotationVectorAvailable = hasRotVec,
            hapticHardwareAvailable = hasHaptics,
            hapticObservationSupported = canObserveHaptics
        )
    }
}
