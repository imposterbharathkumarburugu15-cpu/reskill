package dev.sovarix.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sovarix.app.ui.theme.*

data class HowItWorksStep(
    val stepNumber: String,
    val icon: String,
    val title: String,
    val headline: String,
    val explanation: String,
    val physicalSignal: String,
    val visualThemeColor: Color
)

@Composable
fun HowItWorksModal(
    onDismiss: () -> Unit
) {
    val steps = remember {
        listOf(
            HowItWorksStep(
                stepNumber = "STEP 1",
                icon = "👁️",
                title = "OBSERVE & SENSE",
                headline = "Reads real device telemetry without battery drain",
                explanation = "SOVARIX quietly reads real physical sensor values (battery temperature in °C, CPU thermal headroom, RAM pressure, and motion gyro). It consumes less than 0.1% CPU using adaptive sample timing.",
                physicalSignal = "Physical Sensor APIs · Monotonic Clock · BatteryManager",
                visualThemeColor = SovarixCyan
            ),
            HowItWorksStep(
                stepNumber = "STEP 2",
                icon = "🧠",
                title = "DIGITAL TWIN & DNA",
                headline = "Builds an empirical physical model of your phone",
                explanation = "Your phone has a unique physical personality. SOVARIX models how fast your device heats up during gaming, how quickly it cools, and how much battery it drains. It never fabricates numbers—it only learns from real sessions.",
                physicalSignal = "Empirical Curve Fitting · Heating Rate (°C/min) · Maturity Score",
                visualThemeColor = SovarixGreen
            ),
            HowItWorksStep(
                stepNumber = "STEP 3",
                icon = "📈",
                title = "PREDICT THE FUTURE",
                headline = "Multi-horizon trajectory calculation before throttling",
                explanation = "Instead of waiting until your phone is burning and lagging, the Future Engine calculates temperature trajectories (+10s, +30s, +60s, +5m, +15m) using dampened polynomial math so you stay ahead of throttling.",
                physicalSignal = "Velocity (°C/min) · Acceleration (°C/min²) · Headroom Margin",
                visualThemeColor = SovarixAmber
            ),
            HowItWorksStep(
                stepNumber = "STEP 4",
                icon = "🛡️",
                title = "THERMAL SHIELD",
                headline = "Autonomous self-mitigation with legitimate Android APIs",
                explanation = "When heat spikes during gaming, Auto-Cool engages graduated protection: reducing SOVARIX sampling overhead, dampening background tasks, and alerting you with non-spammy localized notifications.",
                physicalSignal = "Graduated Safety Levels (0-3) · Zero Root · No Vendor Hacks",
                visualThemeColor = SovarixCyan
            ),
            HowItWorksStep(
                stepNumber = "STEP 5",
                icon = "🔒",
                title = "VERIFY & REMEMBER",
                headline = "SHA-256 tamper-evident Black Box audit trail",
                explanation = "Every prediction is verified against actual reality: Was the prediction accurate? The result is chained into an immutable SHA-256 cryptographic ledger that serves as a permanent device health certificate.",
                physicalSignal = "SHA-256 Hash Chain · Closed-Loop Learning · JSON Export",
                visualThemeColor = SovarixGreen
            )
        )
    }

    var selectedStepIndex by remember { mutableIntStateOf(0) }
    val activeStep = steps[selectedStepIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "HOW SOVARIX WORKS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = SovarixCyan
                        )
                        Text(
                            "The 5-Phase Adaptive Digital Twin Cycle",
                            fontSize = 11.sp,
                            color = SovarixTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SovarixSurface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 12.sp, color = SovarixTextMuted)
                    }
                }

                // Interactive Horizontal Step Selector
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(steps) { idx, step ->
                        val isSelected = idx == selectedStepIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) step.visualThemeColor.copy(alpha = 0.18f) else SovarixSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) step.visualThemeColor else SovarixBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedStepIndex = idx }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(step.icon, fontSize = 14.sp)
                                Text(
                                    step.stepNumber,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) step.visualThemeColor else SovarixTextSecondary
                                )
                            }
                        }
                    }
                }

                // Visual Interactive Stage Card
                AnimatedContent(
                    targetState = activeStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "stepAnimation"
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SovarixSurface)
                            .border(1.dp, step.visualThemeColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Title & Icon Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(step.visualThemeColor.copy(alpha = 0.2f))
                                    .border(1.dp, step.visualThemeColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(step.icon, fontSize = 20.sp)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    step.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = SovarixTextPrimary
                                )
                                Text(
                                    step.headline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = step.visualThemeColor
                                )
                            }
                        }

                        // Explanation Body
                        Text(
                            text = step.explanation,
                            fontSize = 12.sp,
                            color = SovarixTextSecondary,
                            lineHeight = 18.sp
                        )

                        // Hardware / Physical Signal Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SovarixDark)
                                .border(1.dp, SovarixBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "PHYSICAL SIGNALS & ALGORITHMS",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = SovarixTextMuted
                                )
                                Text(
                                    step.physicalSignal,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = SovarixTextPrimary
                                )
                            }
                        }
                    }
                }

                // Architecture Flow Diagram Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "DETECT → PREDICT → DECIDE → MITIGATE → VERIFY",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = SovarixTextMuted
                    )

                    Text(
                        "${selectedStepIndex + 1} of ${steps.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovarixCyan
                    )
                }

                // Navigation Buttons (Prev / Next)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedStepIndex > 0) {
                        Button(
                            onClick = { selectedStepIndex-- },
                            colors = ButtonDefaults.buttonColors(containerColor = SovarixSurface),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("← PREVIOUS", fontSize = 11.sp, color = SovarixTextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedStepIndex < steps.size - 1) {
                                selectedStepIndex++
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SovarixCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (selectedStepIndex < steps.size - 1) "NEXT STEP →" else "GOT IT!",
                            fontSize = 11.sp,
                            color = SovarixDark,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
