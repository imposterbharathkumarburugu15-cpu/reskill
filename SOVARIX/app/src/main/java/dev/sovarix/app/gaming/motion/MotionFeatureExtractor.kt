package dev.sovarix.app.gaming.motion

import dev.sovarix.core.MotionSnapshot
import kotlin.math.abs

class MotionFeatureExtractor {

    fun extract(
        timestampMs: Long,
        gyro: GyroscopeMonitor,
        accel: AccelerometerMonitor,
        linear: LinearAccelerationMonitor,
        rot: RotationVectorMonitor
    ): MotionSnapshot {
        val gyroMag = gyro.angularVelocityMagnitude
        val accelMag = accel.accelerationMagnitude
        val linearMag = linear.linearMagnitude

        // Compute normalized movement intensity [0.0 .. 1.0]
        val rotScore = (gyroMag / 4.0f).coerceIn(0f, 1f)
        val accelScore = if (linear.isAvailable) {
            (linearMag / 8.0f).coerceIn(0f, 1f)
        } else {
            (abs(accelMag - 9.8f) / 7.0f).coerceIn(0f, 1f)
        }

        val intensity = (0.55f * rotScore + 0.45f * accelScore).coerceIn(0f, 1f)

        val suddenRot = gyro.suddenRotation
        val suddenMot = accel.suddenMotion || (linear.isAvailable && linearMag > 5.0f)
        val confidence = when {
            gyro.isAvailable && accel.isAvailable -> 0.95f
            gyro.isAvailable || accel.isAvailable -> 0.80f
            else -> 0.40f
        }

        return MotionSnapshot(
            timestamp = timestampMs,
            gyroAvailable = gyro.isAvailable,
            accelerometerAvailable = accel.isAvailable,
            linearAccelerationAvailable = linear.isAvailable,
            rotationVectorAvailable = rot.isAvailable,
            gyroX = gyro.x,
            gyroY = gyro.y,
            gyroZ = gyro.z,
            angularVelocityMagnitude = gyroMag,
            gyroSpike = gyro.isSpike,
            accelX = accel.x,
            accelY = accel.y,
            accelZ = accel.z,
            accelerationMagnitude = accelMag,
            accelerationSpike = accel.isSpike,
            linearAccelX = linear.x,
            linearAccelY = linear.y,
            linearAccelZ = linear.z,
            linearAccelerationMagnitude = linearMag,
            rotationVector = rot.values,
            movementIntensity = intensity,
            suddenRotationDetected = suddenRot,
            suddenMotionDetected = suddenMot,
            confidence = confidence
        )
    }
}
