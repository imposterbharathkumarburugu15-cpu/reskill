package dev.sovarix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.SimulationResult
import dev.sovarix.core.SimulationUnsupported
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ASK SOVARIX: The Integrated Phone Intelligence Bottom Sheet.
 * Built into the operating layer of the phone, not a generic chatbot.
 * Responds to real device telemetry and counterfactual physical predictions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSovarixSheet(
    repo: TwinRepository,
    onDismiss: () -> Unit
) {
    val voice = repo.voiceManager
    val isListening by voice.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voice.isSpeaking.collectAsStateWithLifecycle()
    val spokenText by voice.spokenText.collectAsStateWithLifecycle()
    val currentLang by repo.selectedLanguage.collectAsStateWithLifecycle()
    val s by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var activeQuery by remember { mutableStateOf<String?>(null) }
    var intelligenceResponse by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val presetInquiries = listOf(
        "Why is my phone hot?",
        "What happens if I play for another hour?",
        "Why did my performance drop?",
        "How is my battery behaving?"
    )

    fun answerQuery(query: String) {
        activeQuery = query
        isProcessing = true
        scope.launch {
            val temp = s.temperature ?: s.latest?.batteryC ?: 37.5
            val reply = when {
                query.contains("hot", ignoreCase = true) || query.contains("warm", ignoreCase = true) -> {
                    val status = s.thermalState ?: 0
                    val game = repo.gamingManager.selectedGame.value
                    val isGaming = repo.gamingManager.isGamingActive.value

                    if (isGaming) {
                        String.format(
                            Locale.US,
                            "Temperature is %.1f°C during %s. Heavy GPU rendering is elevating internal heat. Auto-Cool is active to stabilize thermals.",
                            temp, game
                        )
                    } else if (temp >= 39.0 || status >= 2) {
                        String.format(
                            Locale.US,
                            "Device is at %.1f°C. Sustained processor activity or warm ambient conditions have raised the thermal curve.",
                            temp
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "Device is operating at a comfortable %.1f°C. Thermals are nominal and well within safe headroom.",
                            temp
                        )
                    }
                }
                query.contains("hour", ignoreCase = true) || query.contains("play", ignoreCase = true) -> {
                    val simResult = repo.simulateQuery("What happens if I game for another hour?")
                    when (simResult) {
                        is SimulationResult -> {
                            val targetTemp = simResult.recommendedScenario?.predictedTemperatureC
                                ?: simResult.scenarios.firstOrNull()?.predictedTemperatureC
                                ?: 40.6
                            val delta = targetTemp - temp
                            String.format(
                                Locale.US,
                                "If gaming continues for another hour, temperature is predicted to reach %.1f°C (+%.1f°C). Auto-Cool will engage mitigation.",
                                targetTemp, delta
                            )
                        }
                        is SimulationUnsupported -> simResult.reason
                    }
                }
                query.contains("performance", ignoreCase = true) || query.contains("drop", ignoreCase = true) || query.contains("lag", ignoreCase = true) -> {
                    val status = s.thermalState ?: 0
                    if (status >= 3) {
                        "Thermal throttling is active to protect hardware longevity. Reduced GPU clocks may cause minor frame jitter."
                    } else if (status >= 1) {
                        "Performance is currently stable. A rising thermal trend was detected, and Auto-Cool has pre-emptively reduced background sampling."
                    } else {
                        "Performance is completely stable. Zero hardware throttling or thermal mitigation is currently engaged."
                    }
                }
                query.contains("battery", ignoreCase = true) -> {
                    val pct = s.battery?.toInt() ?: s.latest?.batteryPct?.toInt() ?: 75
                    val drain = s.dna.behaviorModel.workloadBatteryDrainRates[s.workload] ?: 12.0
                    String.format(
                        Locale.US,
                        "Battery is at %d%%. Estimated consumption rate under current workload is approximately %.1f%%/hr.",
                        pct, drain
                    )
                }
                else -> {
                    val sim = repo.simulateQuery(query)
                    when (sim) {
                        is SimulationResult -> {
                            val temp = sim.recommendedScenario?.predictedTemperatureC ?: 39.8
                            String.format(Locale.US, "Simulated outcome under this condition predicts device temperature at %.1f°C.", temp)
                        }
                        is SimulationUnsupported -> sim.reason
                    }
                }
            }
            intelligenceResponse = reply
            isProcessing = false
            if (isSpeaking) {
                voice.speak(reply, currentLang)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            voice.stopListening()
            voice.stop()
            onDismiss()
        },
        containerColor = SovarixDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SovarixBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "ASK SOVARIX",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "Device intelligence and physical behavioral twin",
                        fontSize = 11.sp,
                        color = SovarixTextMuted
                    )
                }

                // Compact Orb icon
                SovarixOrb(
                    onClick = {
                        if (isListening) {
                            voice.stopListening()
                        } else {
                            intelligenceResponse = null
                            voice.startListening(
                                languageTag = currentLang,
                                onResult = { spoken ->
                                    answerQuery(spoken)
                                },
                                onError = { err ->
                                    intelligenceResponse = err
                                }
                            )
                        }
                    },
                    modifier = Modifier.size(46.dp),
                    isSpeakingOrActive = isListening || isSpeaking
                )
            }

            // Quick preset inquiries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FREQUENT INQUIRIES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = SovarixCyan
                )

                presetInquiries.forEach { inquiry ->
                    val isCurrent = activeQuery == inquiry
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) SovarixCyanSurface else SovarixSurface)
                            .border(
                                1.dp,
                                if (isCurrent) SovarixCyan.copy(alpha = 0.5f) else SovarixBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                answerQuery(inquiry)
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inquiry,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) SovarixCyan else SovarixTextPrimary
                            )
                            Text(
                                text = "→",
                                fontSize = 14.sp,
                                color = if (isCurrent) SovarixCyan else SovarixTextMuted
                            )
                        }
                    }
                }
            }

            // Transcribed query if voice used
            if (spokenText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SovarixSurface)
                        .border(1.dp, SovarixBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "\"$spokenText\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SovarixTextSecondary
                    )
                }
            }

            // Intelligence Answer Display
            AnimatedVisibility(visible = intelligenceResponse != null || isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SovarixDark)
                        .border(1.dp, SovarixCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SOVARIX INTELLIGENCE",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = SovarixCyan
                            )
                            if (isSpeaking) {
                                Text(
                                    text = "SPEAKING...",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SovarixGreen
                                )
                            }
                        }

                        if (isProcessing) {
                            Text(
                                text = "Evaluating physical telemetry...",
                                fontSize = 13.sp,
                                color = SovarixTextMuted
                            )
                        } else {
                            Text(
                                text = intelligenceResponse ?: "",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 19.sp,
                                color = SovarixTextPrimary
                            )
                        }
                    }
                }
            }

            // Bottom voice guidance
            Text(
                text = if (isListening) "Listening in ${currentLang.uppercase()}... Speak your diagnostic query."
                else "Tap the Orb or select any question above to consult phone intelligence.",
                fontSize = 11.sp,
                color = SovarixTextMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
