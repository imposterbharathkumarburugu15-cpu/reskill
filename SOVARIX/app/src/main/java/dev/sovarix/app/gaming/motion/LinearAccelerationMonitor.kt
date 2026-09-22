package dev.sovarix.app.gaming.motion

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.sqrt

class LinearAccelerationMonitor : SensorEventListener {

    @Volatile var isAvailable: Boolean = false
    @Volatile var x: Float = 0f
    @Volatile var y: Float = 0f
    @Volatile var z: Float = 0f
    @Volatile var linearMagnitude: Float = 0f

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        x = event.values[0]
        y = event.values[1]
        z = event.values[2]

        linearMagnitude = sqrt(x * x + y * y + z * z)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        x = 0f; y = 0f; z = 0f
        linearMagnitude = 0f
    }
}
