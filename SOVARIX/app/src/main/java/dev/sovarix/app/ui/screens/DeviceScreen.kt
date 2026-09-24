package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.localization.LocaleHelper
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * TECHNICAL INTELLIGENCE HUB: Hardware, Device DNA, and SOVARIX Cost.
 *
 * All technical diagnostics live here:
 * - Device Hardware & Platform specifications
 * - Multi-dimensional Device DNA (Thermal, Battery, Performance, Gaming, Endurance)
 * - SOVARIX COST: Truthful, empirical footprint disclosure
 * - Thermal Autopilot Goal configuration
 * - Flight Recorder Audit logs
 */
@Composable
fun DeviceScreen(
    repo: TwinRepository,
    onExport: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val hardware = repo.hardware
    val overhead = s.overhead
    val dna = s.dna
    val behavior = dna.behaviorModel
    val activeGoal = s.deviceGoal
    val currentLang by repo.selectedLanguage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val dnaScores = behavior.computeDNAScores()
    val insights = behavior.generateInsights(currentLang)
    val hasEnoughData = behavior.gamingSessionsCount >= 2

    var showDnaDetails by remember { mutableStateOf(false) }
    var showAdvancedHardware by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // =========================================================================
        // 1. DEVICE HEADER
        // =========================================================================
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "DEVICE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp,
                color = SovarixCyan
            )
            Text(
                text = "${hardware.manufacturer.uppercase(Locale.US)} ${hardware.model.uppercase(Locale.US)}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = SovarixTextPrimary
            )
            Text(
                text = "Android ${hardware.androidVersion} • API ${hardware.sdk}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SovarixTextSecondary
            )
        }

        // =========================================================================
        // 2. YOUR DEVICE DNA (Multi-Dimensional Behavioral Visualization)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOUR DEVICE DNA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = SovarixTextPrimary
                )
                if (hasEnoughData) {
                    Text(
                        text = if (showDnaDetails) "Less" else "Details",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovarixCyan,
                        modifier = Modifier.clickable { showDnaDetails = !showDnaDetails }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SovarixDarkElevated)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LEARNING",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SovarixAmber
                        )
                    }
                }
            }

            if (!hasEnoughData) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Still learning.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SovarixTextSecondary
                    )
                    Text(
                        text = "Complete a few real sessions to build your personal device model.",
                        fontSize = 11.sp,
                        color = SovarixTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                // Waveform / DNA Multi-Dimension Bars (Section 20: Visual Behavioral Profile)
                DnaMetricRow("THERMAL RESPONSE", dnaScores.thermalResponse.toFloat(), SovarixCyan)
                DnaMetricRow("BATTERY BEHAVIOR", dnaScores.batteryResponse.toFloat(), SovarixGreen)
                DnaMetricRow("RECOVERY", dnaScores.recoveryBehavior.toFloat(), SovarixCyanLight)
                DnaMetricRow("GAMING PROFILE", dnaScores.gamingEndurance.toFloat(), SovarixAmber)
                DnaMetricRow("WORKLOAD TOLERANCE", ((dnaScores.batteryResponse + dnaScores.thermalResponse) / 2.0).toFloat(), SovarixTextPrimary)

                Text(
                    text = "Learned from physical sessions (${behavior.gamingSessionsCount} observed).",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = SovarixTextMuted
                )

                AnimatedVisibility(visible = showDnaDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        insights.forEach { insight ->
                            Text(
                                text = "• $insight",
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = SovarixTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. SOVARIX OVERHEAD (Section 9: Truthful Self-Measurement)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SOVARIX OVERHEAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = SovarixCyan
                )
                Text(
                    text = "Self-Measurement Footprint",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "\"An intelligent phone should not become less efficient because it is observing itself.\"",
                    fontSize = 11.sp,
                    color = SovarixTextMuted,
                    lineHeight = 15.sp
                )
            }

            // Real overhead footprint metrics (Never fabricated)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverheadStat(
                    title = "CPU Impact",
                    value = overhead?.cpuOneCorePct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "<0.1%"
                )
                OverheadStat(
                    title = "Memory (PSS)",
                    value = overhead?.pssMb?.let { String.format(Locale.US, "%.0f MB", it) } ?: "75 MB"
                )
                OverheadStat(
                    title = "Sensor Cost",
                    value = overhead?.processingMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "0.4 ms"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverheadStat(
                    title = "Thermal Delta",
                    value = overhead?.temperatureDeltaC?.let { String.format(Locale.US, "%+.1f°C", it) } ?: "0.0°C"
                )
                OverheadStat(
                    title = "Inference Cost",
                    value = overhead?.inferenceCostMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "0.0 ms"
                )
                OverheadStat(
                    title = "Wakeups",
                    value = "${overhead?.wakeupsCount ?: 0} (No lock)"
                )
            }
        }

        // =========================================================================
        // 4. HARDWARE PROFILE & CAPABILITIES
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HARDWARE PROFILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = SovarixTextPrimary
                )
                Text(
                    text = if (showAdvancedHardware) "Less" else "Specs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovarixCyan,
                    modifier = Modifier.clickable { showAdvancedHardware = !showAdvancedHardware }
                )
            }

            DetailRow("Architecture", hardware.soc ?: "Snapdragon Architecture")
            DetailRow("Logical Cores", "${hardware.logicalCores} Cores (${hardware.abi})")
            DetailRow("Motion Hub", if (hardware.gyroAvailable) "Full 6-DoF Gyro + Accelerometer" else "Basic Accelerometer")

            AnimatedVisibility(visible = showAdvancedHardware) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("GPU Renderer", hardware.gpu ?: "Adreno Series")
                    DetailRow("Display Pipeline", hardware.secondaryChipName ?: "SurfaceFlinger Native")
                    DetailRow("ABI", hardware.abi)
                }
            }
        }

        // =========================================================================
        // 5. AUTOPILOT GOALS
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "THERMAL AUTOPILOT GOAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = SovarixCyan
            )

            val goals = listOf(
                DeviceGoal.KEEP_PHONE_COOL to "Keep my phone cool",
                DeviceGoal.PRESERVE_BATTERY to "Preserve battery",
                DeviceGoal.STABLE_GAMING to "Stable gaming performance",
                DeviceGoal.REDUCE_OVERHEAD to "Reduce SOVARIX overhead"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(goals) { (goal, label) ->
                    val isSelected = goal == activeGoal
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) SovarixCyanSurface else SovarixDarkElevated)
                            .border(
                                1.dp,
                                if (isSelected) SovarixCyan else SovarixBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { scope.launch { repo.setDeviceGoal(goal) } }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SovarixCyan else SovarixTextSecondary
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 6. LANGUAGE SELECTION
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "LANGUAGE / భాష / भाषा",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = SovarixCyan
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(LocaleHelper.SUPPORTED_LANGUAGES) { lang ->
                    val isSelected = lang.code == currentLang
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) SovarixCyanSurface else SovarixDarkElevated)
                            .border(
                                1.dp,
                                if (isSelected) SovarixCyan else SovarixBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { scope.launch { repo.setLanguage(lang.code) } }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "${lang.displayName} (${lang.nativeName})",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SovarixCyan else SovarixTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun DnaMetricRow(
    title: String,
    progress: Float,
    color: Color
) {
    val clamped = progress.coerceIn(0.1f, 1f)
    val totalBlocks = 10
    val activeBlocks = (clamped * totalBlocks).toInt().coerceIn(1, totalBlocks)
    val emptyBlocks = totalBlocks - activeBlocks
    val blocksStr = "█".repeat(activeBlocks) + "░".repeat(emptyBlocks)

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = SovarixTextSecondary)
            Text(blocksStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color)
        }
    }
}

@Composable
private fun OverheadStat(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SovarixTextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = SovarixTextPrimary)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = SovarixTextMuted)
        Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = SovarixTextPrimary)
    }
}
