package dev.sovarix.app.gaming.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dev.sovarix.core.MotionSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MotionSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    val gyroMonitor = GyroscopeMonitor()
    val accelMonitor = AccelerometerMonitor()
    val linearMonitor = LinearAccelerationMonitor()
    val rotMonitor = RotationVectorMonitor()
    private val extractor = MotionFeatureExtractor()

    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    init {
        gyroMonitor.isAvailable = gyroSensor != null
        accelMonitor.isAvailable = accelSensor != null
        linearMonitor.isAvailable = linearSensor != null
        rotMonitor.isAvailable = rotSensor != null
    }

    private val _snapshot = MutableStateFlow(extractor.extract(System.currentTimeMillis(), gyroMonitor, accelMonitor, linearMonitor, rotMonitor))
    val snapshot = _snapshot.asStateFlow()

    @Volatile private var isRunning = false
    private var updateJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun isListening(): Boolean = isRunning
    fun hasGyro(): Boolean = gyroSensor != null
    fun hasAccel(): Boolean = accelSensor != null
    fun hasLinear(): Boolean = linearSensor != null
    fun hasRotationVector(): Boolean = rotSensor != null

    fun start(delayUs: Int = SensorManager.SENSOR_DELAY_GAME) {
        if (isRunning || sensorManager == null) return
        isRunning = true

        gyroSensor?.let { sensorManager.registerListener(gyroMonitor, it, delayUs) }
        accelSensor?.let { sensorManager.registerListener(accelMonitor, it, delayUs) }
        linearSensor?.let { sensorManager.registerListener(linearMonitor, it, delayUs) }
        rotSensor?.let { sensorManager.registerListener(rotMonitor, it, delayUs) }

        // Start lightweight polling loop (approx 20Hz / 50ms) to emit clean MotionSnapshots
        updateJob = scope.launch {
            while (isActive && isRunning) {
                val s = extractor.extract(
                    System.currentTimeMillis(),
                    gyroMonitor,
                    accelMonitor,
                    linearMonitor,
                    rotMonitor
                )
                _snapshot.value = s
                delay(50L)
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        updateJob?.cancel()
        updateJob = null

        sensorManager?.unregisterListener(gyroMonitor)
        sensorManager?.unregisterListener(accelMonitor)
        sensorManager?.unregisterListener(linearMonitor)
        sensorManager?.unregisterListener(rotMonitor)

        gyroMonitor.reset()
        accelMonitor.reset()
        linearMonitor.reset()
        rotMonitor.reset()

        _snapshot.value = extractor.extract(
            System.currentTimeMillis(),
            gyroMonitor,
            accelMonitor,
            linearMonitor,
            rotMonitor
        )
    }

    fun getLatest(): MotionSnapshot {
        return extractor.extract(
            System.currentTimeMillis(),
            gyroMonitor,
            accelMonitor,
            linearMonitor,
            rotMonitor
        )
    }
}
