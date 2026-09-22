package dev.sovarix.app.gaming

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.Vibrator
import android.os.VibratorManager
import dev.sovarix.core.GamingCaptureCapabilities

class GamingCapabilitiesDetector(private val context: Context) {

    fun detect(): GamingCaptureCapabilities {
        val audioPlaybackSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val screenCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        val recordingSupported = true

        // Honest haptic evaluation:
        // While the phone has a physical vibrator motor, Android's public SDK does NOT
        // expose an API to observe when other apps/games trigger vibration.
        // We report this honestly rather than fabricating data.
        val hapticObservationSupported = false

        // Check if PACKAGE_USAGE_STATS is granted for automatic foreground game detection
        val gameDetectionSupported = checkUsageStatsPermission()

        val notes = buildString {
            append("Screen capture: ").append(if (screenCaptureSupported) "Supported (MediaProjection)" else "Unsupported")
            append(" · Audio: ").append(if (audioPlaybackSupported) "Android 10+ AudioPlaybackCapture" else "Requires Android 10+")
            append(" · Haptics: Android public APIs do not expose third-party game haptics (honestly reported as unavailable)")
            append(" · Game detection: ").append(if (gameDetectionSupported) "UsageStats active" else "Manual game selection (Free Fire)")
        }

        return GamingCaptureCapabilities(
            audioPlaybackCaptureSupported = audioPlaybackSupported,
            screenCaptureSupported = screenCaptureSupported,
            hapticObservationSupported = hapticObservationSupported,
            gameDetectionSupported = gameDetectionSupported,
            recordingSupported = recordingSupported,
            notes = notes
        )
    }

    fun checkUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
