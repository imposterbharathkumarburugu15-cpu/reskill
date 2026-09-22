package dev.sovarix.app.gaming.motion

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

class RotationVectorMonitor : SensorEventListener {

    @Volatile var isAvailable: Boolean = false
    @Volatile var values: FloatArray? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        values = event.values.clone()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        values = null
    }
}
