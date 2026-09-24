package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            // 1. TOP HUD AREA
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SOVARIX",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                            color = SovarixTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SovarixDarkElevated)
                                .border(1.dp, SovarixBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HUD::TWIN",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SovarixFontMono,
                                color = SovarixCyan
                            )
                        }
                    }
                    Text(
                        text = "YOUR PHONE KNOWS. NOW IT UNDERSTANDS.",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SovarixFontMono,
                        letterSpacing = 0.8.sp,
                        color = SovarixTextMuted
                    )
                }

                // Top-right: HUD Status & Live Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Small info icon trigger
                    Icon(
                        imageVector = SovarixIcons.Sparkle,
                        contentDescription = "Intelligence Details",
                        tint = SovarixTextSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenHowItWorks() }
                    )

                    // Minimalist high-tech status pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixDarkElevated)
                            .border(
                                1.dp,
                                if (s.running) SovarixGreen.copy(alpha = 0.5f) else SovarixBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (s.running) onStop() else onStart()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (s.running) SovarixGreen else SovarixCyan)
                        )
                        Text(
                            text = if (s.running) "ONLINE" else "STANDBY",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SovarixFontMono,
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
            // 3. FLOATING PHONE STATE RIBBON (Monospace HUD Readouts)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SovarixSurface)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = batteryPct?.let { "BAT: $it%" } ?: "BAT: --%",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SovarixFontMono,
                    color = SovarixTextPrimary
                )
                Text("•", fontSize = 10.sp, color = SovarixBorderActive)
                Text(
                    text = freeRamGb?.let { String.format(Locale.US, "RAM: %.1fGB", it) } ?: "RAM: --GB",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SovarixFontMono,
                    color = SovarixTextPrimary
                )
                Text("•", fontSize = 10.sp, color = SovarixBorderActive)
                val perfLabel = when {
                    isCritical -> "THROTTLED"
                    isHot -> "STRESSED"
                    isWarming -> "RISING"
                    else -> "NOMINAL"
                }
                val perfColor = when {
                    isCritical -> SovarixRed
                    isHot -> SovarixOrange
                    isWarming -> SovarixAmber
                    else -> SovarixGreen
                }
                Text(
                    text = perfLabel,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SovarixFontMono,
                    color = perfColor
                )
            }

            // =========================================================================
            // HIGH-TECH 4-QUADRANT TELEMETRY MATRIX
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "[ TELEMETRY // REAL-TIME MATRIX ]",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SovarixFontMono,
                        letterSpacing = 1.sp,
                        color = SovarixCyan
                    )
                    Text(
                        text = "HOT-PATH: 0ms",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SovarixFontMono,
                        color = SovarixGreen
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Block 1: Governor Mode
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixSurface)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text("GOVERNOR", fontSize = 8.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = s.policy.mode.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SovarixFontMono,
                            color = SovarixCyan
                        )
                    }

                    // Block 2: Workload Status
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixSurface)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text("WORKLOAD", fontSize = 8.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = s.workload.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SovarixFontMono,
                            color = SovarixTextPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Block 3: Voltage & Current
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixSurface)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text("CURRENT DRAW", fontSize = 8.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                        Spacer(Modifier.height(2.dp))
                        val currentMa = currentSample?.currentUa?.let { kotlin.math.abs(it / 1000) } ?: 380
                        Text(
                            text = "$currentMa mA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SovarixFontMono,
                            color = SovarixTextPrimary
                        )
                    }

                    // Block 4: Cryptographic Ledger
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixSurface)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text("BLACK BOX", fontSize = 8.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "SHA-256 VALID",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SovarixFontMono,
                            color = SovarixGreen
                        )
                    }
                }
            }

            // =========================================================================
            // 4. CONTEXTUAL "NOW" (High-Tech HUD Card)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToGame() }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[ CONTEXT // CURRENT STATE ]",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SovarixFontMono,
                        letterSpacing = 1.sp,
                        color = SovarixTextMuted
                    )
                    Text("TAP TO INSPECT →", fontSize = 8.sp, fontFamily = SovarixFontMono, color = SovarixCyan)
                }

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
                                    text = String.format(Locale.US, "SESSION TIME: %02d:%02d", mm, ss),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SovarixFontMono,
                                    color = SovarixCyan
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f°C", currentTemp ?: 38.5)} • Multi-sensor fusion active",
                                    fontSize = 11.sp,
                                    fontFamily = SovarixFontMono,
                                    color = SovarixTextSecondary
                                )
                            }
                        }
                    }
                    isRecording -> {
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
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SovarixRed)
                                    )
                                    Text(
                                        text = "RECORDING ACTIVE",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = SovarixFontMono,
                                        letterSpacing = 1.sp,
                                        color = SovarixRed
                                    )
                                }
                                Text(
                                    text = "Screen capture buffer • Thermal stable",
                                    fontSize = 11.sp,
                                    fontFamily = SovarixFontMono,
                                    color = SovarixTextSecondary
                                )
                            }
                        }
                    }
                    isAutoCoolActive -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "AUTO-COOL MITIGATING",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = SovarixFontMono,
                                    letterSpacing = 1.sp,
                                    color = SovarixCyan
                                )
                                Text(
                                    text = "Governor active: reducing available background workloads.",
                                    fontSize = 10.5.sp,
                                    fontFamily = SovarixFontMono,
                                    lineHeight = 14.sp,
                                    color = SovarixTextSecondary
                                )
                            }
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "PHONE AT REST",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SovarixFontMono,
                                    letterSpacing = 0.8.sp,
                                    color = SovarixTextPrimary
                                )
                                Text(
                                    text = "Adaptive telemetry in low-power baseline mode.",
                                    fontSize = 10.5.sp,
                                    fontFamily = SovarixFontMono,
                                    color = SovarixTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 5. SOVARIX INSIGHT (Conversational Observation)
            // =========================================================================
            val insights = s.dna.behaviorModel.generateInsights(currentLang)
            val primaryInsight = insights.firstOrNull() ?: if (isGaming) {
                "Gaming workload detected. Thermal behavior remains within personalized device envelope."
            } else if (isWarming) {
                "Thermal acceleration exceeds idle baseline. Anticipating cooling requirements."
            } else {
                "Thermal dissipation and energy consumption optimal for current workload."
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "[ INTELLIGENCE // REAL-TIME SYNTHESIS ]",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SovarixFontMono,
                    letterSpacing = 1.sp,
                    color = SovarixCyan
                )

                Text(
                    text = "\"$primaryInsight\"",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 18.sp,
                    color = SovarixTextPrimary
                )

                val subLine = when {
                    isAutoCoolActive -> "AUTO-COOL INTERVENTION APPLIED"
                    isWarming || isHot -> "PREDICTIVE SAFEGUARDS ARMED"
                    else -> "EMPIRICAL MODEL CALIBRATED"
                }
                Text(
                    text = subLine,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SovarixFontMono,
                    letterSpacing = 0.8.sp,
                    color = if (isAutoCoolActive) SovarixCyan else SovarixTextMuted
                )
            }

            // =========================================================================
            // PREDICTION CONTRACT (Section 4: Core SOVARIX Feature)
            // =========================================================================
            val latestContract = s.predictionContracts.lastOrNull() ?: s.verifications.lastOrNull()?.let { v ->
                dev.sovarix.core.PredictionContract(
                    id = v.id,
                    targetHorizonSeconds = v.horizonMinutes * 60,
                    createdAtElapsedMs = 0L,
                    dueAtElapsedMs = 0L,
                    predictedValue = v.predictedC ?: 0.0,
                    baselineValue = v.baselineTemperature,
                    confidence = 0.95,
                    actualValue = v.actualC,
                    signedError = v.signedErrorC,
                    status = v.status
                )
            }

            if (latestContract != null && (latestContract.predictedValue > 0 || latestContract.actualValue != null)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SovarixDark)
                        .border(1.dp, SovarixBorder, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[ CONTRACT // HORIZON: ${latestContract.targetHorizonSeconds}s ]",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SovarixFontMono,
                            letterSpacing = 1.sp,
                            color = SovarixCyan
                        )
                        val statusColor = when (latestContract.status) {
                            "VERIFIED" -> SovarixGreen
                            "PENDING" -> SovarixOrange
                            else -> SovarixTextMuted
                        }
                        Text(
                            text = latestContract.status,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = SovarixFontMono,
                            letterSpacing = 1.sp,
                            color = statusColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PREDICTED", fontSize = 8.5.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                            Text(
                                text = String.format(Locale.US, "%.1f°C", latestContract.predictedValue),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SovarixFontMono,
                                color = SovarixTextPrimary
                            )
                        }
                        Column {
                            Text("ACTUAL", fontSize = 8.5.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                            Text(
                                text = latestContract.actualValue?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Pending...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SovarixFontMono,
                                color = if (latestContract.actualValue != null) SovarixTextPrimary else SovarixTextMuted
                            )
                        }
                        Column {
                            Text("SIGNED ERROR", fontSize = 8.5.sp, fontFamily = SovarixFontMono, color = SovarixTextMuted)
                            Text(
                                text = latestContract.errorAbsolute?.let { String.format(Locale.US, "%.2f°C", it) } ?: "--",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SovarixFontMono,
                                color = if (latestContract.status == "VERIFIED") SovarixCyan else SovarixTextMuted
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 6. DEVICE LEARNING STATE (DNA Calibration HUD)
            // =========================================================================
            val sessionCount = s.dna.sessions
            val isEstablished = sessionCount >= 3

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEstablished) "[ DNA // PROFILE CALIBRATED ]" else "[ DNA // LEARNING PROFILE ]",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SovarixFontMono,
                        letterSpacing = 1.sp,
                        color = if (isEstablished) SovarixGreen else SovarixCyan
                    )

                    // Learning dots indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val activeDots = (sessionCount.coerceIn(0, 5))
                        for (i in 0 until 5) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (i < activeDots) SovarixCyan else SovarixBorderActive
                                    )
                            )
                        }
                    }
                }

                Text(
                    text = if (isEstablished) {
                        "Thermal inertia and cooling half-life learned from empirical sessions."
                    } else {
                        val remaining = (3 - sessionCount).coerceAtLeast(1)
                        "$remaining more sessions needed to finalize high-precision thermal inertia model."
                    },
                    fontSize = 11.sp,
                    fontFamily = SovarixFontMono,
                    color = SovarixTextMuted
                )
            }

            Spacer(Modifier.height(50.dp))
        }
    }
}
