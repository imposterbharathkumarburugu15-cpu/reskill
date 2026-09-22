package dev.sovarix.app.gaming.motion

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class AccelerometerMonitor : SensorEventListener {

    @Volatile var isAvailable: Boolean = false
    @Volatile var x: Float = 0f
    @Volatile var y: Float = 0f
    @Volatile var z: Float = 0f
    @Volatile var accelerationMagnitude: Float = 9.8f
    @Volatile var isSpike: Boolean = false
    @Volatile var suddenMotion: Boolean = false

    private val history = FloatArray(16)
    private var historyIndex = 0
    private var historyCount = 0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        x = event.values[0]
        y = event.values[1]
        z = event.values[2]

        val mag = sqrt(x * x + y * y + z * z)
        accelerationMagnitude = mag

        // Rolling stats
        history[historyIndex] = mag
        historyIndex = (historyIndex + 1) % history.size
        if (historyCount < history.size) historyCount++

        var sum = 0f
        for (i in 0 until historyCount) sum += history[i]
        val mean = sum / historyCount

        var varSum = 0f
        for (i in 0 until historyCount) varSum += (history[i] - mean).pow(2)
        val std = if (historyCount > 1) sqrt(varSum / (historyCount - 1)) else 1.0f

        // Acceleration spike above gravity baseline (9.8 m/s^2)
        val deltaFromGravity = abs(mag - 9.8f)
        val spike = deltaFromGravity > 5.0f && (mag > mean + 2.0f * std)
        isSpike = spike
        suddenMotion = deltaFromGravity > 6.5f || spike
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        x = 0f; y = 0f; z = 0f
        accelerationMagnitude = 9.8f
        isSpike = false
        suddenMotion = false
        historyCount = 0
        historyIndex = 0
    }
}
