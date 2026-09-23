package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.R
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.components.LiveDeviceVisualizer
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.AutoCoolStrategy
import java.util.Locale

/**
 * Flagship Home Screen: "How is my phone right now?"
 *
 * Replaces card clutter and dashboard grids with:
 * - Fluid typography
 * - Generous whitespace
 * - Central Thermal Hero in a dynamic circular energy field
 * - Minimal horizontal status strip with typographic separators
 * - Contextual Now activity
 * - Subtle, intelligent device insight
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

    val currentTemp = trend?.currentTemperature ?: currentSample?.batteryC ?: s.temperature
    val batteryPct = currentSample?.batteryPct?.toInt() ?: s.battery?.toInt()
    val freeRamGb = currentSample?.availableMemoryBytes?.let { it.toDouble() / 1_073_741_824.0 }
    val strategy = decision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // =========================================================================
        // 1. HOME HEADER
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SOVARIX",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "YOUR PHONE KNOWS.\nNOW IT UNDERSTANDS.",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    lineHeight = 13.sp,
                    color = SovarixTextMuted
                )
            }

            // Minimal Live indicator & Observation action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Subtle How It Works hint
                Text(
                    text = "INFO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = SovarixTextMuted,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenHowItWorks() }
                        .padding(vertical = 4.dp)
                )

                // Live status toggle pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SovarixDark)
                        .clickable {
                            if (s.running) onStop() else onStart()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (s.running) SovarixGreen else SovarixCyan)
                    )
                    Text(
                        text = if (s.running) "LIVE" else "READY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        color = if (s.running) SovarixGreen else SovarixCyan
                    )
                }
            }
        }

        // =========================================================================
        // 2. THERMAL HERO (Visual Centerpiece - Tap to Open Thermal Detail)
        // =========================================================================
        LiveDeviceVisualizer(
            temperatureC = currentTemp,
            thermalStatus = s.thermalState ?: currentSample?.thermalStatus,
            trend = trend,
            strategy = strategy,
            onClick = onOpenThermalDetail
        )

        // =========================================================================
        // 3. MINIMAL STATUS STRIP (Pure typography and bullet separators)
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
            val isWarm = (s.thermalState ?: currentSample?.thermalStatus ?: 0) >= 2
            Text(
                text = if (isWarm) "Thermal Warm" else "Thermal Light",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isWarm) SovarixAmber else SovarixCyan
            )
            Text("•", fontSize = 11.sp, color = SovarixTextMuted)
            val isThrottled = (s.thermalState ?: currentSample?.thermalStatus ?: 0) >= 3
            Text(
                text = if (isThrottled) "Throttled" else "Performance Stable",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isThrottled) SovarixRed else SovarixGreen
            )
        }

        // Thin elegant separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SovarixBorder)
        )

        // =========================================================================
        // 4. CURRENT ACTIVITY ("NOW")
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onNavigateToGame() },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "NOW",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = SovarixTextMuted
            )

            if (isGaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "🎮  ${selectedGame.uppercase(Locale.US)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = SovarixTextPrimary
                        )
                        val mm = sessionElapsedSec / 60
                        val ss = sessionElapsedSec % 60
                        Text(
                            text = "${String.format(Locale.US, "%d min", mm)} session • ${String.format(Locale.US, "%.1f°", currentTemp ?: 38.5)} • Performance stable",
                            fontSize = 11.5.sp,
                            color = SovarixTextSecondary
                        )
                    }
                    Text("→", fontSize = 16.sp, color = SovarixCyan)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "DEVICE STANDBY",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = SovarixTextSecondary
                        )
                        Text(
                            text = "Ready for your next session.",
                            fontSize = 11.5.sp,
                            color = SovarixTextMuted
                        )
                    }
                    Text("→", fontSize = 16.sp, color = SovarixTextMuted)
                }
            }
        }

        // =========================================================================
        // 5. SOVARIX INSIGHT (Subtle, intelligent section - No giant boxes)
        // =========================================================================
        val insights = s.dna.behaviorModel.generateInsights(currentLang)
        val primaryInsight = insights.firstOrNull()

        if (!primaryInsight.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(SovarixCyan)
                    )
                    Text(
                        text = "SOVARIX INSIGHT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = SovarixCyan
                    )
                }

                Text(
                    text = primaryInsight,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 18.sp,
                    color = SovarixTextPrimary
                )

                Text(
                    text = if (strategy != AutoCoolStrategy.LEVEL_0_NORMAL) "Auto-Cool is active." else "Auto-Cool is ready.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (strategy != AutoCoolStrategy.LEVEL_0_NORMAL) SovarixAmber else SovarixTextMuted
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
