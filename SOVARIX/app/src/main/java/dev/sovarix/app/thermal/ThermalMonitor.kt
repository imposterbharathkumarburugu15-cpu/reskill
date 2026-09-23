package dev.sovarix.app.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import dev.sovarix.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Section 1 & 2: Real Android Thermal Monitor with Adaptive Sampling.
 * Uses official public Android APIs:
 * - PowerManager thermal status and OnThermalStatusChangedListener (API 29+)
 * - PowerManager thermal headroom (API 30+)
 * - BatteryManager battery temperature
 * - PerformanceHintManager (ADPF) query capability check (API 31+)
 *
 * Avoids aggressive polling; monitors adaptively so observation never heats the phone.
 */
class ThermalMonitor(private val context: Context) {

    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    @Volatile private var callbackThermalStatus: Int? = null
    private var lastHeadroomQueryMs = Long.MIN_VALUE
    private var cachedHeadroom: Double? = null
    private var cachedForecastHeadroom: Double? = null

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        callbackThermalStatus = status
    }

    private val _capabilities = MutableStateFlow(auditCapabilities())
    val capabilities: StateFlow<ThermalCapabilities> = _capabilities.asStateFlow()

    private val _samplingMode = MutableStateFlow(AdaptiveSamplingMode.STABLE)
    val samplingMode: StateFlow<AdaptiveSamplingMode> = _samplingMode.asStateFlow()

    fun open() {
        runCatching {
            power?.addThermalStatusListener(context.mainExecutor, thermalListener)
        }
        _capabilities.value = auditCapabilities()
    }

    fun close() {
        runCatching {
            power?.removeThermalStatusListener(thermalListener)
        }
    }

    /**
     * Audits and returns actual public Android API availability.
     * Never assumes or fabricates availability.
     */
    fun auditCapabilities(): ThermalCapabilities {
        val thermalStatusOk = runCatching {
            power?.currentThermalStatus != null
        }.getOrDefault(false)

        val headroomOk = if (Build.VERSION.SDK_INT >= 30 && power != null) {
            runCatching {
                val h = power.getThermalHeadroom(0)
                h.isFinite() && h >= 0
            }.getOrDefault(false)
        } else false

        val forecastOk = if (Build.VERSION.SDK_INT >= 30 && power != null) {
            runCatching {
                val h = power.getThermalHeadroom(30)
                h.isFinite() && h >= 0
            }.getOrDefault(false)
        } else false

        val adpfOk = if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                context.getSystemService("performance_hint") != null
            }.getOrDefault(false)
        } else false

        val batteryTempOk = runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.hasExtra(BatteryManager.EXTRA_TEMPERATURE) == true
        }.getOrDefault(false)

        return ThermalCapabilities(
            thermalStatusAvailable = thermalStatusOk,
            thermalHeadroomAvailable = headroomOk,
            thermalForecastAvailable = forecastOk,
            cpuHeadroomAvailable = adpfOk,
            gpuHeadroomAvailable = false, // Not exposed via public Android SDK
            batteryTemperatureAvailable = batteryTempOk
        )
    }

    /**
     * Reads real device thermal telemetry.
     * Strictly bounds headroom queries to >= 10s to obey Android API guidelines.
     */
    fun sampleRealThermal(): RealThermalReading {
        val now = SystemClock.elapsedRealtime()

        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val batteryTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
            ?.div(10.0)
            ?.takeIf { it in -20.0..85.0 }

        val thermalStatus = runCatching { power?.currentThermalStatus }
            .getOrNull()
            ?.takeIf { it in 0..6 }
            ?: callbackThermalStatus

        // Android guideline: Query thermal headroom at most once every 10 seconds
        if (Build.VERSION.SDK_INT >= 30 && power != null) {
            if (lastHeadroomQueryMs == Long.MIN_VALUE || now - lastHeadroomQueryMs >= 10_000L) {
                cachedHeadroom = runCatching {
                    power.getThermalHeadroom(0).toDouble()
                }.getOrNull()?.takeIf { it.isFinite() && it >= 0 }

                cachedForecastHeadroom = runCatching {
                    power.getThermalHeadroom(30).toDouble()
                }.getOrNull()?.takeIf { it.isFinite() && it >= 0 }

                lastHeadroomQueryMs = now
            }
        }

        return RealThermalReading(
            timestamp = System.currentTimeMillis(),
            elapsedMs = now,
            thermalStatus = thermalStatus,
            thermalHeadroom = cachedHeadroom,
            forecastHeadroom30s = cachedForecastHeadroom,
            batteryTemperature = batteryTemp
        )
    }

    /**
     * Updates adaptive sampling mode based on thermal trend and status.
     * Prevents thermal monitoring itself from contributing to heating.
     */
    fun updateSamplingMode(trend: ThermalTrend, currentStatus: Int?): AdaptiveSamplingMode {
        val mode = when {
            (currentStatus ?: 0) >= 4 || (trend.currentTemperature ?: 0.0) >= 45.0 -> AdaptiveSamplingMode.CRITICAL
            (currentStatus ?: 0) >= 2 || (trend.currentTemperature ?: 0.0) >= 40.0 || (trend.thermalHeadroom ?: 0.0) >= 0.85 -> AdaptiveSamplingMode.HOT
            trend.temperatureVelocity >= 0.35 || (trend.currentTemperature ?: 0.0) >= 37.5 -> AdaptiveSamplingMode.RISING
            else -> AdaptiveSamplingMode.STABLE
        }
        _samplingMode.value = mode
        return mode
    }
}

data class RealThermalReading(
    val timestamp: Long,
    val elapsedMs: Long,
    val thermalStatus: Int?,
    val thermalHeadroom: Double?,
    val forecastHeadroom30s: Double?,
    val batteryTemperature: Double?
)
