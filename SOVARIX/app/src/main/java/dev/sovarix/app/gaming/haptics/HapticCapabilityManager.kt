package dev.sovarix.app.gaming.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.sovarix.core.HapticCapabilities

class HapticCapabilityManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun audit(): HapticCapabilities {
        val hasHw = vibrator?.hasVibrator() == true

        // Three distinct concepts:
        // 1. Hardware available: does the phone have a vibrator?
        // 2. Can control: can SOVARIX trigger a test vibration?
        // 3. Can observe external: can SOVARIX intercept when Free Fire vibrates?
        // On Android public APIs, #3 is strictly impossible without root/OS mods.
        // We report this honestly as FALSE.
        return HapticCapabilities(
            hardwareAvailable = hasHw,
            canControl = hasHw,
            canObserveExternalEvents = false,
            reason = "Android public APIs do not permit background observation of third-party game haptics. Never faked."
        )
    }

    /**
     * Optional tactical test pulse for user verification in Sensor Test mode.
     */
    fun triggerTestPulse(durationMs: Long = 40L) {
        if (vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
