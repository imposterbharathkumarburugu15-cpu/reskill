package dev.sovarix.app.telemetry

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dev.sovarix.app.*
import kotlinx.coroutines.*

class TwinService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: Job? = null
    private lateinit var telemetry: AndroidTelemetry
    private val repo get() = (application as SovarixApp).repository
    override fun onCreate() { super.onCreate(); telemetry = AndroidTelemetry(this) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            val active = session
            scope.launch { active?.cancelAndJoin(); repo.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            return START_NOT_STICKY
        }
        if (session?.isActive == true) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Observation session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, TwinService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_sovarix)
            .setContentTitle("SOVARIX · observing this device").setContentText("Local telemetry session · tap to view · Stop to end")
            .setContentIntent(open).setOngoing(true).setSilent(true).addAction(0, "Stop", stop).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(17, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(17, notification)
        } catch (e: Exception) {
            scope.launch { repo.fail("Android could not start the session: ${e.javaClass.simpleName}"); stopSelf() }
            return START_NOT_STICKY
        }
        session = scope.launch {
            try {
                repo.start(); telemetry.open()
                while (isActive) {
                    val start = SystemClock.elapsedRealtime()
                    val motion = repo.gamingManager.motionManager.getLatest()
                    val state = repo.accept(telemetry.sample(repo.state.value.workload), motion)
                    if (!state.running) break
                    delay((state.policy.intervalMs - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(1_000))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { repo.fail("Observation stopped: ${e.javaClass.simpleName}. You can start a new session.") }
            finally {
                telemetry.close()
                withContext(NonCancellable) { repo.stop() }
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); telemetry.close(); super.onDestroy() }
    companion object { const val STOP = "dev.sovarix.STOP"; private const val CHANNEL = "sovarix_observation" }
}
