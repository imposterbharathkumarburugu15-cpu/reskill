package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.components.LiveDeviceVisualizer
import dev.sovarix.app.ui.components.SovarixIcons
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.AutoCoolStrategy
import dev.sovarix.core.GamingCaptureState
import java.util.Locale

/**
 * LIVING DIGITAL TWIN: Continuous Living State Composition.
 *
 * One continuous fluid surface governed by the phone's physical state:
 * ATMOSPHERE + DIGITAL TWIN HERO + LIVE THERMAL TRACE + STATE RIBBON + CONTEXTUAL NOW + INSIGHT + LEARNING
 * Zero card containers. Zero dashboard boxes.
 */
@Composable
fun HomeScreen(
    repo: TwinRepository,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onNavigateToGame: () -> Unit,
    onNavigateToLab: () -> Unit,
    onOpenThermalDetail: () -> Unit,
    onOpenHowItWorks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val liveSample by repo.liveTelemetry.collectAsStateWithLifecycle()
    val currentSample = s.latest ?: liveSample

    val currentLang by repo.selectedLanguage.collectAsStateWithLifecycle()
    val acm = repo.autoCoolManager
    val trend by acm.thermalTrend.collectAsStateWithLifecycle()
    val decision by acm.autoCoolDecision.collectAsStateWithLifecycle()
    val gm = repo.gamingManager
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val selectedGame by gm.selectedGame.collectAsStateWithLifecycle()
    val sessionElapsedSec by gm.sessionElapsedSec.collectAsStateWithLifecycle()
    val captureState by gm.captureState.collectAsStateWithLifecycle()
    val moments by gm.moments.collectAsStateWithLifecycle()

    val currentTemp = trend?.currentTemperature ?: currentSample?.batteryC ?: s.temperature
    val batteryPct = currentSample?.batteryPct?.toInt() ?: s.battery?.toInt()
    val freeRamGb = currentSample?.availableMemoryBytes?.let { it.toDouble() / 1_073_741_824.0 }
    val strategy = decision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL
    val velocityPerMin = trend?.temperatureVelocity ?: 0.0

    // Thermal States
    val isAutoCoolActive = strategy != AutoCoolStrategy.LEVEL_0_NORMAL
    val isCritical = (s.thermalState ?: currentSample?.thermalStatus ?: 0) >= 4 || (currentTemp ?: 37.0) >= 45.0
    val isHot = !isCritical && ((s.thermalState ?: currentSample?.thermalStatus ?: 0) >= 2 || (currentTemp ?: 37.0) >= 40.0)
    val isWarming = !isCritical && !isHot && velocityPerMin >= 0.15
    val isRecovery = !isCritical && !isHot && !isWarming && (trend?.isCooling == true || velocityPerMin <= -0.1)

    val isRecording = captureState == GamingCaptureState.BUFFERING || captureState == GamingCaptureState.PRESERVING

    // Dynamic living background atmosphere
    val atmosphericColor = when {
        isCritical -> Color(0xFF220808)
        isHot -> Color(0xFF241006)
        isWarming -> Color(0xFF1E1406)
        isAutoCoolActive -> Color(0xFF041924)
        isRecovery -> Color(0xFF061B14)
        else -> Color(0xFF061118)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(atmosphericColor, SovarixBlack, SovarixBlack)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // =========================================================================
            // 1. TOP AREA
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SOVARIX",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.8.sp,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "Your phone knows.\nNow it understands.",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        lineHeight = 15.sp,
                        color = SovarixTextSecondary
                    )
                }

                // Top-right: ● LIVE and small info icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Small info icon trigger
                    Icon(
                        imageVector = SovarixIcons.Sparkle,
                        contentDescription = "Intelligence Details",
                        tint = SovarixTextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenHowItWorks() }
                    )

                    // Minimal live status toggle pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SovarixDarkElevated)
                            .clickable {
                                if (s.running) onStop() else onStart()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (s.running) SovarixGreen else SovarixCyan)
                        )
                        Text(
                            text = if (s.running) "LIVE" else "READY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = if (s.running) SovarixGreen else SovarixCyan
                        )
                    }
                }
            }

            // =========================================================================
            // 2. THE DIGITAL TWIN HERO & LIVE THERMAL TRACE
            // =========================================================================
            LiveDeviceVisualizer(
                temperatureC = currentTemp,
                thermalStatus = s.thermalState ?: currentSample?.thermalStatus,
                trend = trend,
                strategy = strategy,
                onClick = onOpenThermalDetail
            )

            // =========================================================================
            // 3. FLOATING PHONE STATE RIBBON (Pure typography and separators)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = batteryPct?.let { "$it% Battery" } ?: "--% Battery",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SovarixTextPrimary
                )
                Text("•", fontSize = 11.sp, color = SovarixTextMuted)
                Text(
                    text = freeRamGb?.let { String.format(Locale.US, "%.1f GB RAM", it) } ?: "-- GB RAM",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SovarixTextPrimary
                )
                Text("•", fontSize = 11.sp, color = SovarixTextMuted)
                val perfLabel = when {
                    isCritical -> "Throttled"
                    isHot -> "Thermal Stress"
                    isWarming -> "Performance Active"
                    else -> "Performance Stable"
                }
                val perfColor = when {
                    isCritical -> SovarixRed
                    isHot -> SovarixOrange
                    else -> SovarixGreen
                }
                Text(
                    text = perfLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = perfColor
                )
            }

            // Subtle dividing hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SovarixBorder)
            )

            // =========================================================================
            // 4. CONTEXTUAL "NOW" (Living state based on real phone activity)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToGame() },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = SovarixTextMuted
                )

                when {
                    isGaming -> {
                        // Gaming State
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "🎮 ${selectedGame.uppercase(Locale.US)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = SovarixTextPrimary
                                )
                                val mm = sessionElapsedSec / 60
                                val ss = sessionElapsedSec % 60
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SovarixCyan
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f°", currentTemp ?: 38.5)} • Performance stable",
                                    fontSize = 11.5.sp,
                                    color = SovarixTextSecondary
                                )
                                if (moments.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "↓  SPECIAL MOMENTS\n${moments.size} captured",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = SovarixTextPrimary
                                    )
                                }
                            }
                            Text("→", fontSize = 18.sp, color = SovarixCyan)
                        }
                    }
                    isRecording -> {
                        // Screen Recording State
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(SovarixRed)
                                    )
                                    Text(
                                        text = "RECORDING",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                        color = SovarixRed
                                    )
                                }
                                Text(
                                    text = "Screen capture active • Thermal stable",
                                    fontSize = 11.5.sp,
                                    color = SovarixTextSecondary
                                )
                            }
                            Text("→", fontSize = 18.sp, color = SovarixTextSecondary)
                        }
                    }
                    isAutoCoolActive -> {
                        // Auto-Cool Mitigating State
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "❄ AUTO-COOL",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = SovarixCyan
                                )
                                Text(
                                    text = "Reducing available workload • Monitoring recovery",
                                    fontSize = 11.5.sp,
                                    color = SovarixTextSecondary
                                )
                            }
                            Text("→", fontSize = 18.sp, color = SovarixCyan)
                        }
                    }
                    else -> {
                        // Phone at Rest (Default Standby)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "PHONE AT REST",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = SovarixTextPrimary
                                )
                                Text(
                                    text = "Battery efficient • Thermal stable",
                                    fontSize = 11.5.sp,
                                    color = SovarixTextSecondary
                                )
                            }
                            Text("→", fontSize = 18.sp, color = SovarixTextMuted)
                        }
                    }
                }
            }

            // =========================================================================
            // 5. SOVARIX INSIGHT (Conversational Digital Twin Observation)
            // =========================================================================
            val insights = s.dna.behaviorModel.generateInsights(currentLang)
            val primaryInsight = insights.firstOrNull() ?: if (isGaming) {
                "You're in a gaming session. Thermal behavior is still within your normal range."
            } else if (isWarming) {
                "Your phone is warming faster than your usual baseline."
            } else {
                "Thermal behavior is optimal for current workload."
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SOVARIX INSIGHT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp,
                    color = SovarixCyan
                )

                Text(
                    text = "\"$primaryInsight\"",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 19.sp,
                    color = SovarixTextPrimary
                )

                val subLine = when {
                    isAutoCoolActive -> "Auto-Cool is active."
                    isWarming || isHot -> "Auto-Cool is ready."
                    else -> "Baseline verified."
                }
                Text(
                    text = subLine,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAutoCoolActive) SovarixCyan else SovarixTextMuted
                )
            }

            // =========================================================================
            // 6. DEVICE LEARNING STATE (Subtle personalization progress)
            // =========================================================================
            val sessionCount = s.dna.sessions
            val isEstablished = sessionCount >= 3

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEstablished) "Device baseline established" else "Learning your device",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SovarixTextSecondary
                    )

                    // Learning dots indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val activeDots = (sessionCount.coerceIn(0, 5))
                        for (i in 0 until 5) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i < activeDots) SovarixCyan else SovarixBorder
                                    )
                            )
                        }
                    }
                }

                Text(
                    text = if (isEstablished) {
                        "\"Your thermal behavior is now personalized.\""
                    } else {
                        val remaining = (3 - sessionCount).coerceAtLeast(1)
                        "\"$remaining more gaming sessions will improve your personal thermal baseline.\""
                    },
                    fontSize = 11.sp,
                    color = SovarixTextMuted
                )
            }

            Spacer(Modifier.height(50.dp))
        }
    }
}
