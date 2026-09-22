package dev.sovarix.app.gaming.motion

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.pow
import kotlin.math.sqrt

class GyroscopeMonitor : SensorEventListener {

    @Volatile var isAvailable: Boolean = false
    @Volatile var x: Float = 0f
    @Volatile var y: Float = 0f
    @Volatile var z: Float = 0f
    @Volatile var angularVelocityMagnitude: Float = 0f
    @Volatile var isSpike: Boolean = false
    @Volatile var suddenRotation: Boolean = false

    private val history = FloatArray(16)
    private var historyIndex = 0
    private var historyCount = 0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        x = event.values[0]
        y = event.values[1]
        z = event.values[2]

        val mag = sqrt(x * x + y * y + z * z)
        angularVelocityMagnitude = mag

        // Rolling stats
        history[historyIndex] = mag
        historyIndex = (historyIndex + 1) % history.size
        if (historyCount < history.size) historyCount++

        var sum = 0f
        for (i in 0 until historyCount) sum += history[i]
        val mean = sum / historyCount

        var varSum = 0f
        for (i in 0 until historyCount) varSum += (history[i] - mean).pow(2)
        val std = if (historyCount > 1) sqrt(varSum / (historyCount - 1)) else 0.5f

        // Threshold for sudden gaming flick / rotation: > 2.5 rad/sec and > 2 std dev
        val spike = mag > 2.2f && (mag > mean + 2.0f * std)
        isSpike = spike
        suddenRotation = mag > 3.0f || spike
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        x = 0f; y = 0f; z = 0f
        angularVelocityMagnitude = 0f
        isSpike = false
        suddenRotation = false
        historyCount = 0
        historyIndex = 0
    }
}
