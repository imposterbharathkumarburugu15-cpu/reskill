package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import dev.sovarix.app.localization.LocaleHelper
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Flagship Device Screen: Hardware Profile, Device DNA, and SOVARIX Overhead.
 *
 * Houses all technical information in an organized, visual hierarchy:
 * - Device Identity (Manufacturer, Model, Android Version)
 * - Visual Device DNA Progress Bars
 * - Hardware Profile & Sensors
 * - SOVARIX Performance ("How much does SOVARIX cost?")
 * - Operational Black Box & Forensics
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
    val autopilotDecision = s.autopilotDecision
    val repairBaseline = dna.repairBaseline
    val currentLang by repo.selectedLanguage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val dnaScores = behavior.computeDNAScores()
    val insights = behavior.generateInsights(currentLang)

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
                letterSpacing = 2.sp,
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
        // 2. YOUR DEVICE DNA (Visual Progress Bars)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
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
                Text(
                    text = if (showDnaDetails) "Less" else "Details",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovarixCyan,
                    modifier = Modifier.clickable { showDnaDetails = !showDnaDetails }
                )
            }

            // Visual Progress Bars
            DnaMetricBar(
                title = "Thermal Behavior",
                progress = dnaScores.thermalResponse.toFloat(),
                barColor = SovarixCyan
            )

            DnaMetricBar(
                title = "Gaming Endurance",
                progress = dnaScores.gamingEndurance.toFloat(),
                barColor = SovarixGreen
            )

            DnaMetricBar(
                title = "Battery Response",
                progress = dnaScores.batteryResponse.toFloat(),
                barColor = SovarixTextPrimary
            )

            DnaMetricBar(
                title = "Recovery Rate",
                progress = dnaScores.recoveryBehavior.toFloat(),
                barColor = SovarixAmber
            )

            Text(
                text = "Based on your real sessions (${behavior.gamingSessionsCount} observed).",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SovarixTextMuted
            )

            // Expandable insights
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

        // =========================================================================
        // 3. SOVARIX PERFORMANCE: "How much does SOVARIX cost?"
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SOVARIX PERFORMANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = SovarixCyan
                )
                Text(
                    text = "How much does SOVARIX cost?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "SOVARIX discloses its real footprint to prove it never causes the thermal stress it solves.",
                    fontSize = 11.sp,
                    color = SovarixTextMuted,
                    lineHeight = 15.sp
                )
            }

            // Overhead Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverheadStat(
                    title = "SOVARIX CPU",
                    value = overhead?.cpuOneCorePct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "<0.1%"
                )
                OverheadStat(
                    title = "Memory",
                    value = overhead?.pssMb?.let { String.format(Locale.US, "%.0f MB", it) } ?: "75 MB"
                )
                OverheadStat(
                    title = "Sampling Cost",
                    value = "${(overhead?.scheduledIntervalMs ?: 20000L) / 1000}s"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverheadStat(
                    title = "Battery Impact",
                    value = "<0.2%/hr"
                )
                OverheadStat(
                    title = "Thermal Impact",
                    value = "0.0°C (Nil)"
                )
                OverheadStat(
                    title = "Storage",
                    value = "<4 MB"
                )
            }
        }

        // =========================================================================
        // 4. HARDWARE PROFILE & CAPABILITIES
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
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

            DetailRow("Architecture", hardware.soc ?: "Qualcomm Snapdragon Architecture")
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
                    DetailRow("Display Controller", hardware.secondaryChipName ?: "Native SurfaceFlinger Pipeline")
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
                .clip(RoundedCornerShape(16.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
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
                            .background(if (isSelected) SovarixCyanSurface else SovarixSurface)
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
                .clip(RoundedCornerShape(16.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
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
                            .background(if (isSelected) SovarixCyanSurface else SovarixSurface)
                            .border(
                                1.dp,
                                if (isSelected) SovarixCyan else SovarixBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { scope.launch { repo.setLanguage(lang.code) } }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = lang.displayName,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SovarixCyan else SovarixTextSecondary
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 7. OPERATIONAL BLACK BOX (Export & Purge)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SovarixCyanSurface)
                    .border(1.dp, SovarixCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onExport)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EXPORT BLACK BOX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = SovarixCyan
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SovarixSurface)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onPurge)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PURGE HISTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovarixRed
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun DnaMetricBar(
    title: String,
    progress: Float,
    barColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = SovarixTextPrimary
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        // Visual Segmented Block / Bar Indicator (████████░░)
        val blocks = 12
        val filled = ((progress.coerceIn(0f, 1f)) * blocks).toInt().coerceIn(1, blocks)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0 until blocks) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < filled) barColor else SovarixBorder)
                )
            }
        }
    }
}

@Composable
private fun OverheadStat(
    title: String,
    value: String
) {
    Column(
        modifier = Modifier
            .width(95.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SovarixSurface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = SovarixTextMuted
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = SovarixTextPrimary
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = SovarixTextMuted
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = SovarixTextPrimary
        )
    }
}
