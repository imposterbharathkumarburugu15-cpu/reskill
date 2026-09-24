package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * LAB: "WHAT-IF MACHINE"
 *
 * An interactive scenario laboratory showing diverging counterfactual realities.
 * - Interactive prompts: "Play for 60 minutes", "Lower workload", etc.
 * - Diverging Timelines Canvas: Current Reality vs Simulated Future
 * - Possible twin response & parameters
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
    val scope = rememberCoroutineScope()

    val activeExp by repo.activeExperiment.collectAsStateWithLifecycle()
    val expHistory by repo.experimentHistory.collectAsStateWithLifecycle()
    val availableExps = remember { repo.getAvailableExperiments() }

    val currentTemp = currentSample?.batteryC ?: s.temperature ?: 37.8
    val currentBattery = currentSample?.batteryPct ?: s.battery ?: 75.0

    val scenarios = listOf(
        "+15 MIN GAMING",
        "REDUCE WORKLOAD",
        "REDUCE BRIGHTNESS",
        "CONTINUE CURRENT LOAD",
        "CUSTOM"
    )

    var activeScenario by remember { mutableStateOf(scenarios[0]) }
    var customQueryText by remember { mutableStateOf("What if I stop monitoring?") }
    var gamingDurationMin by remember { mutableFloatStateOf(15f) }
    var showExperimentsSheet by remember { mutableStateOf(false) }
    var consentTargetExp by remember { mutableStateOf<Experiment?>(null) }

    // Run simulation query
    LaunchedEffect(activeScenario, gamingDurationMin) {
        val query = when (activeScenario) {
            "+15 MIN GAMING" -> "+15 min gaming"
            "REDUCE WORKLOAD" -> "What if workload is reduced?"
            "REDUCE BRIGHTNESS" -> "What if screen brightness is reduced?"
            "CONTINUE CURRENT LOAD" -> "What happens if this thermal trend continues?"
            "CUSTOM" -> customQueryText
            else -> activeScenario
        }
        repo.simulateQuery(query)
    }

    val result = simOutcome as? SimulationResult
    val predictedTemp = when (activeScenario) {
        "REDUCE WORKLOAD" -> (currentTemp - 1.2).coerceAtLeast(35.5)
        "REDUCE BRIGHTNESS" -> (currentTemp - 0.8).coerceAtLeast(36.0)
        "CONTINUE CURRENT LOAD" -> (currentTemp + 2.2)
        else -> result?.recommendedScenario?.predictedTemperatureC
            ?: result?.scenarios?.firstOrNull()?.predictedTemperatureC
            ?: (currentTemp + 2.4)
    }

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
        // 1. HERO HEADER (Section 17)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PHONE LAB",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "Experiment before you act.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SovarixCyan
                )
            }

            // Quick button to open diagnostic safety tests
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SovarixDarkElevated)
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
        // 2. INTERACTIVE PROMPTS: "WHAT DO YOU WANT TO TEST?"
        // =========================================================================
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "WHAT DO YOU WANT TO TEST?",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
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
                            .background(if (isSelected) SovarixCyanSurface else SovarixDarkElevated)
                            .border(
                                1.dp,
                                if (isSelected) SovarixCyan else SovarixBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { activeScenario = scenario }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
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

            if (activeScenario == "CUSTOM") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customQueryText,
                        onValueChange = { customQueryText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g., What if I stop monitoring?", fontSize = 12.sp, color = SovarixTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SovarixCyan,
                            unfocusedBorderColor = SovarixBorder,
                            focusedTextColor = SovarixTextPrimary,
                            unfocusedTextColor = SovarixTextPrimary
                        )
                    )
                    Button(
                        onClick = { scope.launch { repo.simulateQuery(customQueryText) } },
                        colors = ButtonDefaults.buttonColors(containerColor = SovarixCyan)
                    ) {
                        Text("TEST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SovarixBlack)
                    }
                }
            }
        }

        // =========================================================================
        // 3. DIVERGING TIMELINES VISUALIZATION (CURRENT FUTURE vs ALTERNATIVE FUTURE)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Diverging state figures: CURRENT FUTURE vs ALTERNATIVE FUTURE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // CURRENT FUTURE
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "CURRENT FUTURE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SovarixTextMuted
                    )
                    val baseScenario = result?.scenarios?.find { it.id == "current_path" || it.id == "scenario_a" }
                    val baseTemp = baseScenario?.predictedTemperatureC ?: currentTemp
                    Text(
                        text = String.format(Locale.US, "%.1f°", baseTemp),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = baseScenario?.name ?: "Current Trajectory",
                        fontSize = 10.sp,
                        color = SovarixTextSecondary
                    )
                }

                // ALTERNATIVE FUTURE
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "ALTERNATIVE FUTURE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SovarixCyan
                    )
                    val altScenario = result?.scenarios?.find { it.id == "alternative_path" || it.id.startsWith("scenario_lever") } ?: result?.recommendedScenario
                    val altTemp = altScenario?.predictedTemperatureC ?: predictedTemp
                    val futureColor = if (altTemp >= 40.0) SovarixOrange else SovarixCyan
                    Text(
                        text = String.format(Locale.US, "%.1f°", altTemp),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = futureColor
                    )
                    val baseScenario = result?.scenarios?.find { it.id == "current_path" || it.id == "scenario_a" }
                    val delta = altTemp - (baseScenario?.predictedTemperatureC ?: currentTemp)
                    Text(
                        text = "${if (delta >= 0) "+" else ""}${String.format(Locale.US, "%.1f°C", delta)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (delta > 1.5) SovarixOrange else SovarixGreen
                    )
                }
            }

            // Diverging Timelines Animation Canvas
            DivergingTimelinesCanvas(
                currentTemp = currentTemp,
                predictedTemp = predictedTemp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            // Divider
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
                        .background(SovarixDarkElevated)
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
        // 4. INTERACTIVE DURATION SLIDER (When duration testing is active)
        // =========================================================================
        if (activeScenario == scenarios[0]) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SIMULATION DURATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = SovarixTextSecondary
                    )
                    Text(
                        text = "${gamingDurationMin.roundToInt()} min",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
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
                        inactiveTrackColor = SovarixBorder
                    )
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // =========================================================================
    // 5. DIAGNOSTIC EXPERIMENTS BOTTOM SHEET
    // =========================================================================
    if (showExperimentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExperimentsSheet = false },
            containerColor = SovarixDarkElevated,
            contentColor = SovarixTextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(SovarixBorder)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SAFE DIAGNOSTIC EXPERIMENTS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "Runs bounded 30-second thermal tests with strict automatic safety rollbacks.",
                    fontSize = 11.5.sp,
                    color = SovarixTextSecondary
                )

                availableExps.forEach { exp ->
                    val isRunning = activeExp?.id == exp.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SovarixDark)
                            .border(1.dp, if (isRunning) SovarixGreen else SovarixBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = exp.title,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SovarixTextPrimary
                            )
                            Text(
                                text = exp.question,
                                fontSize = 10.5.sp,
                                color = SovarixTextMuted
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        if (isRunning) {
                            Button(
                                onClick = { scope.launch { repo.cancelExperiment() } },
                                colors = ButtonDefaults.buttonColors(containerColor = SovarixRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("ABORT", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Button(
                                onClick = { consentTargetExp = exp },
                                colors = ButtonDefaults.buttonColors(containerColor = SovarixCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("START", fontSize = 10.sp, fontWeight = FontWeight.Black, color = SovarixBlack)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Safety Consent Dialog
    consentTargetExp?.let { target ->
        AlertDialog(
            onDismissRequest = { consentTargetExp = null },
            title = {
                Text("Start ${target.title}?", fontWeight = FontWeight.Black, color = SovarixTextPrimary)
            },
            text = {
                Text(
                    "This test will run for 30 seconds to calibrate the digital twin baseline. Safe temperature bounds will abort instantly if exceeded.",
                    color = SovarixTextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val exp = consentTargetExp
                        consentTargetExp = null
                        showExperimentsSheet = false
                        if (exp != null) {
                            scope.launch {
                                repo.startExperiment(exp.id)
                            }
                        }
                    }
                ) {
                    Text("AUTHORIZE", fontWeight = FontWeight.Bold, color = SovarixCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { consentTargetExp = null }) {
                    Text("CANCEL", color = SovarixTextMuted)
                }
            },
            containerColor = SovarixDarkElevated
        )
    }
}

/**
 * Diverging Timelines Canvas:
 * Visually illustrates the bifurcation between Current Reality and Simulated Future.
 */
@Composable
private fun DivergingTimelinesCanvas(
    currentTemp: Double,
    predictedTemp: Double,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "divergingTimeline")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val startX = 16.dp.toPx()
        val forkX = w * 0.35f
        val endX = w - 16.dp.toPx()
        val midY = h * 0.55f

        val delta = predictedTemp - currentTemp
        val targetY = (midY - (delta * 14.0).coerceIn((-h * 0.38f).toDouble(), (h * 0.38f).toDouble())).toFloat()

        // 1. Current Reality Path (Baseline horizontal solid line)
        val currentPath = Path().apply {
            moveTo(startX, midY)
            lineTo(endX, midY)
        }
        drawLine(
            color = SovarixCyan.copy(alpha = 0.5f),
            start = Offset(startX, midY),
            end = Offset(endX, midY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 2. Simulated Future Diverging Path (Curved arc branching off from forkX)
        val futureColor = if (predictedTemp >= 40.0) SovarixOrange else SovarixCyanLight
        val futurePath = Path().apply {
            moveTo(forkX, midY)
            cubicTo(
                forkX + (endX - forkX) * 0.4f, midY,
                forkX + (endX - forkX) * 0.6f, targetY,
                endX, targetY
            )
        }
        drawPath(
            path = futurePath,
            color = futureColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        )

        // 3. Origin Anchor Node
        drawCircle(
            color = SovarixCyan,
            center = Offset(startX, midY),
            radius = 3.5.dp.toPx()
        )

        // 4. Branching Fork Node
        drawCircle(
            color = SovarixCyan,
            center = Offset(forkX, midY),
            radius = 4.dp.toPx()
        )

        // 5. Current Reality Endpoint
        drawCircle(
            color = SovarixCyan.copy(alpha = 0.7f),
            center = Offset(endX, midY),
            radius = 3.dp.toPx()
        )

        // 6. Simulated Future Endpoint (Pulsing glow)
        drawCircle(
            color = futureColor,
            center = Offset(endX, targetY),
            radius = 4.5.dp.toPx()
        )
        drawCircle(
            color = futureColor.copy(alpha = 0.35f),
            center = Offset(endX, targetY),
            radius = 8.5.dp.toPx()
        )
    }
}
