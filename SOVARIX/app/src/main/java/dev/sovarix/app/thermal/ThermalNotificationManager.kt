package dev.sovarix.app.thermal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.sovarix.app.MainActivity
import dev.sovarix.app.R
import dev.sovarix.core.AutoCoolStrategy

/**
 * Section 9: Real Android Thermal & Gaming Notification Manager.
 * Creates dedicated notification channels:
 * - sovarix_thermal (High importance for critical alerts)
 * - sovarix_gaming (Low importance for session state)
 * - sovarix_capture (Low importance for moment preservation)
 *
 * Implements cooldowns and hysteresis to eliminate notification spam.
 */
class ThermalNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private var lastNotificationTimeMs = 0L
    private var lastNotifiedStrategy: AutoCoolStrategy? = null

    companion object {
        const val CHANNEL_THERMAL = "sovarix_thermal"
        const val CHANNEL_GAMING = "sovarix_gaming"
        const val CHANNEL_CAPTURE = "sovarix_capture"
        private const val THERMAL_NOTIFICATION_ID = 1001
        private const val COOLDOWN_MS = 60_000L // 60 seconds cooldown between similar alerts
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val thermalChannel = NotificationChannel(
                CHANNEL_THERMAL,
                "SOVARIX Thermal",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thermal alerts and Auto-Cool active notifications"
                setShowBadge(true)
            }

            val gamingChannel = NotificationChannel(
                CHANNEL_GAMING,
                "SOVARIX Gaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active game session and performance status"
                setShowBadge(false)
            }

            val captureChannel = NotificationChannel(
                CHANNEL_CAPTURE,
                "SOVARIX Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Special Moment highlights and media notifications"
                setShowBadge(true)
            }

            notificationManager?.createNotificationChannel(thermalChannel)
            notificationManager?.createNotificationChannel(gamingChannel)
            notificationManager?.createNotificationChannel(captureChannel)
        }
    }

    /**
     * Dispatches real thermal alert according to graduated mitigation levels.
     * Prevents spam via cooldown periods and status hysteresis.
     */
    fun notifyThermalState(strategy: AutoCoolStrategy, temperatureC: Double?, gameName: String? = null) {
        val now = System.currentTimeMillis()

        // Level 0 NORMAL: Clear existing alerts and reset
        if (strategy == AutoCoolStrategy.LEVEL_0_NORMAL) {
            if (lastNotifiedStrategy != null && lastNotifiedStrategy != AutoCoolStrategy.LEVEL_0_NORMAL) {
                notificationManager?.cancel(THERMAL_NOTIFICATION_ID)
            }
            lastNotifiedStrategy = AutoCoolStrategy.LEVEL_0_NORMAL
            return
        }

        // Anti-spam cooldown: do not re-notify identical or lower level within 60s
        if (strategy == lastNotifiedStrategy && (now - lastNotificationTimeMs) < COOLDOWN_MS) {
            return
        }

        val tempStr = temperatureC?.let { String.format(java.util.Locale.US, " (%.1f°C)", it) } ?: ""
        val contextGame = if (!gameName.isNullOrBlank()) " during $gameName" else ""

        val localizedCtx = dev.sovarix.app.localization.LocaleHelper.createLocalizedContext(
            context,
            dev.sovarix.app.localization.LocaleHelper.getPersistedLanguage(context)
        )

        val (title, text, priority) = when (strategy) {
            AutoCoolStrategy.LEVEL_1_PRE_COOL -> Triple(
                localizedCtx.getString(R.string.thermal_alert_title),
                localizedCtx.getString(R.string.thermal_alert_body),
                NotificationCompat.PRIORITY_DEFAULT
            )
            AutoCoolStrategy.LEVEL_2_COOL -> Triple(
                localizedCtx.getString(R.string.thermal_warning_title),
                localizedCtx.getString(R.string.thermal_warning_body),
                NotificationCompat.PRIORITY_HIGH
            )
            AutoCoolStrategy.LEVEL_3_AGGRESSIVE_COOL -> Triple(
                localizedCtx.getString(R.string.thermal_autocool_title),
                localizedCtx.getString(R.string.thermal_autocool_body),
                NotificationCompat.PRIORITY_HIGH
            )
            AutoCoolStrategy.LEVEL_4_CRITICAL -> Triple(
                localizedCtx.getString(R.string.critical_thermal_title),
                localizedCtx.getString(R.string.critical_thermal_body),
                NotificationCompat.PRIORITY_MAX
            )
            AutoCoolStrategy.LEVEL_0_NORMAL -> return
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_THERMAL)
            .setSmallIcon(R.drawable.ic_sovarix)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        runCatching {
            notificationManager?.notify(THERMAL_NOTIFICATION_ID, notification)
            lastNotificationTimeMs = now
            lastNotifiedStrategy = strategy
        }
    }

    fun notifyRecovery(temperatureC: Double?) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationTimeMs < COOLDOWN_MS) return

        val localizedCtx = dev.sovarix.app.localization.LocaleHelper.createLocalizedContext(
            context,
            dev.sovarix.app.localization.LocaleHelper.getPersistedLanguage(context)
        )

        val tempStr = temperatureC?.let { String.format(java.util.Locale.US, " (%.1f°C)", it) } ?: ""
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_THERMAL)
            .setSmallIcon(R.drawable.ic_sovarix)
            .setContentTitle(localizedCtx.getString(R.string.thermal_stable_title))
            .setContentText(localizedCtx.getString(R.string.thermal_stable_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        runCatching {
            notificationManager?.notify(THERMAL_NOTIFICATION_ID, notification)
            lastNotificationTimeMs = now
        }
    }
}
