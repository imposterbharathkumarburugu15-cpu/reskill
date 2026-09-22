package dev.sovarix.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.sovarix.app.telemetry.AndroidTelemetry
import dev.sovarix.app.telemetry.TwinService
import dev.sovarix.app.ui.SovarixScreen
import dev.sovarix.app.ui.SovarixSplashScreen
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val repo get() = (application as SovarixApp).repository
    private var pendingExport: String? = null
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport; pendingExport = null
        if (uri != null && text != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val stream = contentResolver.openOutputStream(uri) ?: error("Destination unavailable")
                stream.bufferedWriter().use { it.write(text) }
            } }
            toast(if (result.isSuccess) "Session exported" else "Could not export. Choose another destination.")
        }
    }
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            repo.gamingManager.onProjectionGranted(result.resultCode, result.data!!)
            toast("Screen capture authorized for Special Moments")
        } else {
            toast("Screen capture not granted. Moments will be recorded as Device Events.")
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Notifications are off. End the session using Stop inside SOVARIX.")
        beginSession()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            Crossfade(
                targetState = showSplash,
                animationSpec = tween(400),
                label = "SplashCrossfade"
            ) { isSplash ->
                if (isSplash) {
                    SovarixSplashScreen(
                        onAnimationComplete = { showSplash = false }
                    )
                } else {
                    SovarixScreen(
                        repo,
                        onStart = { requestSession() },
                        onStop = { startService(Intent(this, TwinService::class.java).setAction(TwinService.STOP)) },
                        onRequestScreenCapture = { requestScreenCapture() },
                        onRequestUsageAccess = {
                            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                .onFailure { toast("Usage access settings unavailable") }
                        },
                        onExport = { lifecycleScope.launch { pendingExport = repo.export(); exportDocument.launch("SOVARIX-session-${System.currentTimeMillis()}.json") } },
                        onDim = {
                            window.attributes = window.attributes.apply { screenBrightness = 0.2f }
                            lifecycleScope.launch { repo.intervention("User dimmed SOVARIX window to 20%. Other apps and system brightness are unchanged.") }
                        },
                        onSettings = { runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }.onFailure { toast("Display settings unavailable") } },
                        onMessage = { toast(it) }
                    )
                }
            }
        }
    }
    private fun requestScreenCapture() {
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        if (manager != null) {
            try {
                projectionLauncher.launch(manager.createScreenCaptureIntent())
            } catch (e: Exception) {
                toast("Screen capture unavailable: ${e.javaClass.simpleName}")
            }
        }
    }
    private fun requestSession() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else beginSession()
    }
    private fun beginSession() {
        runCatching { ContextCompat.startForegroundService(this, Intent(this, TwinService::class.java)) }
            .onFailure { lifecycleScope.launch { repo.fail("Android rejected the observation service: ${it.javaClass.simpleName}") } }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                val tel = AndroidTelemetry(this@MainActivity)
                val sample = tel.sample(repo.state.value.workload)
                repo.accept(sample, repo.gamingManager.motionManager.getLatest())
            }
        }
    }
    override fun onStop() {
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        super.onStop()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
