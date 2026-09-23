package dev.sovarix.app.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.sovarix.app.SovarixApp
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.AutoCoolStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Locale

class ThermalOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val repo: TwinRepository by lazy { (application as SovarixApp).repository }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @SuppressLint("RtlHardcoded")
    private fun initOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 40
            y = 200
        }

        val container = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ThermalOverlayService)
            setViewTreeViewModelStoreOwner(this@ThermalOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ThermalOverlayService)

            setContent {
                ThermalGuardOrb(
                    repo = repo,
                    onDrag = { dx, dy ->
                        params?.let { p ->
                            p.x += dx.toInt()
                            p.y += dy.toInt()
                            windowManager?.updateViewLayout(overlayView, p)
                        }
                    },
                    onClose = { stopSelf() }
                )
            }
        }

        container.addView(composeView)
        overlayView = container
        windowManager?.addView(overlayView, params)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()

        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // View might already be detached
            }
            overlayView = null
        }
        super.onDestroy()
    }
}

@Composable
fun ThermalGuardOrb(
    repo: TwinRepository,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val acm = repo.autoCoolManager
    val trend by acm.thermalTrend.collectAsStateWithLifecycle()
    val decision by acm.autoCoolDecision.collectAsStateWithLifecycle()
    val currentTemp = trend?.currentTemperature ?: s.temperature ?: s.latest?.batteryC ?: 37.5
    val strategy = decision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL
    val isIntervening = strategy != AutoCoolStrategy.LEVEL_0_NORMAL

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(8.dp)
    ) {
        // 1. Floating Pill / Orb
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(if (isIntervening) SovarixAmberSurface else SovarixDark.copy(alpha = 0.88f))
                .border(
                    1.dp,
                    if (isIntervening) SovarixAmber else SovarixCyan.copy(alpha = 0.6f),
                    RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isIntervening) SovarixAmber else SovarixCyan)
                )
                Text(
                    text = String.format(Locale.US, "%.1f°C", currentTemp),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    color = SovarixTextPrimary
                )
                Text(
                    text = if ((trend?.temperatureVelocity ?: 0.0) > 0.1) "↗" else "↘",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((trend?.temperatureVelocity ?: 0.0) > 0.1) SovarixRed else SovarixGreen
                )
            }
        }

        // 2. Expanded Quick HUD Card
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SovarixDark.copy(alpha = 0.94f))
                    .border(1.dp, SovarixBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SOVARIX HUD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = SovarixCyan
                    )
                    Text(
                        "✕",
                        fontSize = 12.sp,
                        color = SovarixTextMuted,
                        modifier = Modifier.clickable { isExpanded = false }
                    )
                }

                Text(
                    text = if (isIntervening) "Thermal Shield Active" else "Thermal Status: Nominal",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIntervening) SovarixAmber else SovarixGreen
                )

                val headroom = trend?.thermalHeadroom ?: s.thermalHeadroom
                if (headroom != null) {
                    Text(
                        text = String.format(Locale.US, "Thermal Headroom: %.1f°C", headroom),
                        fontSize = 10.sp,
                        color = SovarixTextSecondary
                    )
                }

                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Moment bookmark button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixSurface)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                repo.gamingManager.triggerMoment(
                                    manual = true,
                                    label = "HUD Manual Bookmark"
                                )
                                isExpanded = false
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("★ CLIP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SovarixCyan)
                    }

                    // Close overlay button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixRedSurface)
                            .border(1.dp, SovarixRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("HIDE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SovarixRed)
                    }
                }
            }
        }
    }
}
