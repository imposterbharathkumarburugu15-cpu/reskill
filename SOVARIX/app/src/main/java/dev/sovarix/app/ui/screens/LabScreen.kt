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
import androidx.compose.foundation.shape.CircleShape
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
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Flagship LAB Screen: "What could happen?"
 *
 * An elegant scenario interface for counterfactual predictions.
 * Avoids spreadsheet/dashboard grid aesthetics in favor of:
 * - Direct scenario query chips
 * - Current State vs Predicted vs Possible Response
 * - Interactive parameter sliders (Duration, Screen Capture, Charge)
 * - Safe Diagnostic Experiments kept accessible in a clean bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    repo: TwinRepository,
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val liveSample by repo.liveTelemetry.collectAsStateWithLifecycle()
    val currentSample = s.latest ?: liveSample

    val simOutcome by repo.simulationResult.collectAsStateWithLifecycle()
    val currentLang by repo.selectedLanguage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val activeExp by repo.activeExperiment.collectAsStateWithLifecycle()
    val expHistory by repo.experimentHistory.collectAsStateWithLifecycle()
    val availableExps = remember { repo.getAvailableExperiments() }

    val currentTemp = currentSample?.batteryC ?: s.temperature ?: 37.8
    val currentBattery = currentSample?.batteryPct ?: s.battery ?: 75.0

    val scenarios = listOf(
        "What if I play for another hour?",
        "What if I record while gaming?",
        "What if I charge while gaming?",
        "What if I reduce the workload?",
        "What if thermal load continues?"
    )

    var activeScenario by remember { mutableStateOf(scenarios[0]) }
    var gamingDurationMin by remember { mutableFloatStateOf(60f) }
    var isRecordingEnabled by remember { mutableStateOf(false) }
    var showExperimentsSheet by remember { mutableStateOf(false) }
    var consentTargetExp by remember { mutableStateOf<Experiment?>(null) }

    // Run simulation whenever active scenario or slider changes
    LaunchedEffect(activeScenario, gamingDurationMin, isRecordingEnabled) {
        val query = if (activeScenario == scenarios[0]) {
            "What happens if I game for ${gamingDurationMin.roundToInt()} minutes?"
        } else {
            activeScenario
        }
        repo.simulateQuery(query)
    }

    val result = simOutcome as? SimulationResult
    val predictedTemp = result?.recommendedScenario?.predictedTemperatureC
        ?: result?.scenarios?.firstOrNull()?.predictedTemperatureC
        ?: (currentTemp + (gamingDurationMin / 60.0) * 1.8)

    val predictedBattery = result?.recommendedScenario?.predictedBatteryPct
        ?: result?.scenarios?.firstOrNull()?.predictedBatteryPct
        ?: (currentBattery - (gamingDurationMin / 60.0) * 16.0).coerceAtLeast(5.0)

    val possibleResponse = when {
        predictedTemp >= 42.0 -> "Auto-Cool Aggressive Mitigation"
        predictedTemp >= 40.0 -> "Auto-Cool available"
        predictedTemp >= 38.5 -> "Background sampling reduction"
        else -> "Stable baseline monitoring"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // =========================================================================
        // 1. HEADER
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "LAB",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "What could happen?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SovarixCyan
                )
            }

            // Quick button to open diagnostic safety tests
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
                    .clickable { showExperimentsSheet = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeExp != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SovarixGreen)
                        )
                    }
                    Text(
                        text = if (activeExp != null) "TEST ACTIVE" else "EXPERIMENTS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = if (activeExp != null) SovarixGreen else SovarixTextSecondary
                    )
                }
            }
        }

        // =========================================================================
        // 2. SCENARIO SELECTOR: "What do you want to test?"
        // =========================================================================
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "WHAT DO YOU WANT TO TEST?",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = SovarixTextMuted
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(scenarios) { scenario ->
                    val isSelected = scenario == activeScenario
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) SovarixCyanSurface else SovarixDark)
                            .border(
                                1.dp,
                                if (isSelected) SovarixCyan else SovarixBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { activeScenario = scenario }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = scenario,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SovarixCyan else SovarixTextSecondary
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 3. ELEGANT PREDICTION SUMMARY (Clean typography & hierarchy, NOT a spreadsheet)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selected Query Callout
            Text(
                text = "\"$activeScenario\"",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                color = SovarixTextPrimary
            )

            // Dynamic 3-Part Outcome Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // CURRENT STATE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CURRENT STATE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SovarixTextMuted
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f°C", currentTemp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "${currentBattery.toInt()}% Battery",
                        fontSize = 11.sp,
                        color = SovarixTextSecondary
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(SovarixBorder)
                )

                // PREDICTED
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PREDICTED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SovarixCyan
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f°C", predictedTemp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (predictedTemp >= 40.0) SovarixAmber else SovarixCyan
                    )
                    val delta = predictedTemp - currentTemp
                    Text(
                        text = "${if (delta >= 0) "+" else ""}${String.format(Locale.US, "%.1f°C", delta)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (delta > 1.5) SovarixAmber else SovarixGreen
                    )
                }
            }

            // Thin Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SovarixBorder)
            )

            // POSSIBLE RESPONSE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "POSSIBLE RESPONSE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SovarixTextMuted
                    )
                    Text(
                        text = possibleResponse,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (predictedTemp >= 40.0) SovarixAmber else SovarixGreen
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SovarixSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "EMPIRICAL MODEL",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SovarixTextMuted
                    )
                }
            }
        }

        // =========================================================================
        // 4. INTERACTIVE CONTROLS & SLIDERS
        // =========================================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "INTERACTIVE PARAMETERS",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = SovarixTextMuted
            )

            // Duration Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SovarixSurface)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Duration",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "${gamingDurationMin.roundToInt()} min",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovarixCyan
                    )
                }

                Slider(
                    value = gamingDurationMin,
                    onValueChange = { gamingDurationMin = it },
                    valueRange = 15f..120f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = SovarixCyan,
                        activeTrackColor = SovarixCyan,
                        inactiveTrackColor = SovarixDark
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("15 min", fontSize = 10.sp, color = SovarixTextMuted)
                    Text("60 min", fontSize = 10.sp, color = SovarixTextMuted)
                    Text("120 min", fontSize = 10.sp, color = SovarixTextMuted)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // =========================================================================
    // 5. SAFETY EXPERIMENTS MODAL BOTTOM SHEET
    // =========================================================================
    if (showExperimentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExperimentsSheet = false },
            containerColor = SovarixDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SAFE DIAGNOSTIC EXPERIMENTS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = SovarixCyan
                )

                Text(
                    text = "Measure real battery drain and thermal variance between normal baseline and active features under safe 30-second tests.",
                    fontSize = 12.sp,
                    color = SovarixTextSecondary,
                    lineHeight = 17.sp
                )

                // Active test status
                activeExp?.let { exp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SovarixGreenSurface)
                            .border(1.dp, SovarixGreen, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "RUNNING: ${exp.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SovarixGreen
                            )
                            Text(
                                text = "Observations: ${exp.baselineObservations.size + exp.treatmentObservations.size}/20",
                                fontSize = 11.sp,
                                color = SovarixTextPrimary
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        repo.cancelExperiment()
                                        onMessage("Diagnostic test cancelled safely.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SovarixRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Test", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Available tests
                availableExps.forEach { exp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SovarixSurface)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = exp.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SovarixTextPrimary
                                )
                                Text("30s", fontSize = 10.sp, color = SovarixCyan)
                            }
                            Text(
                                text = exp.question,
                                fontSize = 11.5.sp,
                                color = SovarixTextSecondary
                            )
                            Button(
                                onClick = {
                                    showExperimentsSheet = false
                                    scope.launch {
                                        repo.startExperiment(exp.id)
                                        onMessage("Started test: ${exp.title}")
                                    }
                                },
                                enabled = activeExp == null,
                                colors = ButtonDefaults.buttonColors(containerColor = SovarixCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Run 30s Safe Test",
                                    color = SovarixDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
