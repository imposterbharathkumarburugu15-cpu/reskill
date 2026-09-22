package dev.sovarix.app.gaming

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.sovarix.app.MainActivity
import dev.sovarix.app.R

class GamingCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try {
                activeMediaProjection?.stop()
            } catch (_: Exception) {}
            activeMediaProjection = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TRIGGER_MOMENT) {
            (application as? dev.sovarix.app.SovarixApp)?.repository?.gamingManager?.triggerMoment(manual = true)
            return START_STICKY
        }

        val channelId = "sovarix_gaming_capture"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(channelId, "Gaming Capture", NotificationManager.IMPORTANCE_LOW)
        )

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GamingCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerIntent = PendingIntent.getService(
            this, 2,
            Intent(this, GamingCaptureService::class.java).setAction(ACTION_TRIGGER_MOMENT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_sovarix)
            .setContentTitle("SOVARIX · Gaming Video Engine")
            .setContentText("Rolling gameplay buffer active · Ready to capture moments")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "⚡ Capture Highlight", triggerIntent)
            .addAction(0, "End Session", stopIntent)
            .build()

        // Android 14 requirement: startForeground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        // MUST be called before getMediaProjection()!
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    27,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(27, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Now acquire MediaProjection safely from inside the active foreground service
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != 0 && resultData != null) {
            try {
                val projMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                val proj = projMgr?.getMediaProjection(resultCode, resultData)
                if (proj != null) {
                    activeMediaProjection = proj
                    onProjectionReadyListener?.invoke(proj)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        try {
            activeMediaProjection?.stop()
        } catch (_: Exception) {}
        activeMediaProjection = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.sovarix.gaming.START_CAPTURE"
        const val ACTION_STOP = "dev.sovarix.gaming.STOP_CAPTURE"
        const val ACTION_TRIGGER_MOMENT = "dev.sovarix.gaming.TRIGGER_MOMENT"
        const val EXTRA_RESULT_CODE = "dev.sovarix.gaming.EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "dev.sovarix.gaming.EXTRA_RESULT_DATA"

        @Volatile
        var activeMediaProjection: MediaProjection? = null
            internal set

        @Volatile
        var onProjectionReadyListener: ((MediaProjection?) -> Unit)? = null
    }
}
