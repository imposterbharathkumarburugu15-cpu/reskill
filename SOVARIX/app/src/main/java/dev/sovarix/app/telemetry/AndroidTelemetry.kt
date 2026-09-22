package dev.sovarix.app.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import dev.sovarix.core.*

class AndroidTelemetry(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val memory = context.getSystemService(ActivityManager::class.java)
    private var lastHeadroomMs = Long.MIN_VALUE
    private var cachedHeadroom: Double? = null
    private var lastPssMs = Long.MIN_VALUE
    private var cachedPss: Int? = null
    @Volatile private var callbackThermal: Int? = null
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { callbackThermal = it }
    fun open() { runCatching { power.addThermalStatusListener(context.mainExecutor, thermalListener) } }
    fun close() { runCatching { power.removeThermalStatusListener(thermalListener) } }
    fun sample(workload: Workload): Sample {
        val start = SystemClock.elapsedRealtimeNanos()
        val now = SystemClock.elapsedRealtime()
        val intent = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        fun extra(name: String): Int? = if (intent?.hasExtra(name) == true) intent.getIntExtra(name, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE } else null
        val scale = extra(BatteryManager.EXTRA_SCALE)?.takeIf { it > 0 }
        val level = extra(BatteryManager.EXTRA_LEVEL)?.takeIf { it >= 0 }
        val pct = if (scale != null && level != null) (level * 100.0 / scale).takeIf { it in 0.0..100.0 } else null
        val temperature = extra(BatteryManager.EXTRA_TEMPERATURE)?.div(10.0)?.takeIf { it in -20.0..80.0 }
        val status = extra(BatteryManager.EXTRA_STATUS)
        val charging = status?.takeUnless { it == BatteryManager.BATTERY_STATUS_UNKNOWN }?.let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }
        // Android specifies an unavailable sentinel. Zero can be a legitimate reading.
        val current = runCatching { battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
            .getOrNull()?.takeUnless { it == Int.MIN_VALUE }
        val thermal = runCatching { power.currentThermalStatus }.getOrNull()?.takeIf { it in 0..6 } ?: callbackThermal
        // Thermal headroom must never be queried more than once every ten seconds.
        if (Build.VERSION.SDK_INT >= 30 && (lastHeadroomMs == Long.MIN_VALUE || now - lastHeadroomMs >= 10_000)) {
            cachedHeadroom = runCatching { power.getThermalHeadroom(0).toDouble() }.getOrNull()?.takeIf { it.isFinite() && it >= 0 }
            lastHeadroomMs = now
        }
        val info = runCatching { ActivityManager.MemoryInfo().also { memory.getMemoryInfo(it) } }.getOrNull()
        // PSS collection is bounded to once a minute; it is not cheap enough for every sample.
        if (lastPssMs == Long.MIN_VALUE || now - lastPssMs >= 60_000) {
            cachedPss = runCatching { Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss }.getOrNull()
            lastPssMs = now
        }
        return Sample(now, System.currentTimeMillis(), pct, temperature, charging, current, thermal,
            cachedHeadroom, info?.availMem, info?.totalMem, info?.lowMemory, cachedPss,
            Process.getElapsedCpuTime(), power.isPowerSaveMode, power.isInteractive, workload,
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
    }
}
