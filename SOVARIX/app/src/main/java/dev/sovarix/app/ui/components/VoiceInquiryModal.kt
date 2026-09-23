package dev.sovarix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.R
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun VoiceInquiryModal(
    repo: TwinRepository,
    onDismiss: () -> Unit
) {
    val voice = repo.voiceManager
    val isListening by voice.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voice.isSpeaking.collectAsStateWithLifecycle()
    val spokenText by voice.spokenText.collectAsStateWithLifecycle()
    val currentLang by repo.selectedLanguage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var responseText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Pulsing animation for active microphone
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Automatically trigger voice recognition upon modal opening
    LaunchedEffect(Unit) {
        errorMessage = null
        responseText = null
        voice.startListening(
            languageTag = currentLang,
            onResult = { text ->
                scope.launch {
                    val simResult = repo.simulateQuery(text)
                    val reply = when (simResult) {
                        is dev.sovarix.core.SimulationResult -> {
                            val temp = simResult.recommendedScenario?.predictedTemperatureC
                                ?: simResult.scenarios.firstOrNull()?.predictedTemperatureC
                                ?: 39.5
                            when (currentLang) {
                                "te" -> "ఈ మార్పుతో ఫోన్ ఉష్ణోగ్రత సుమారు %.1f°C అవుతుంది.".format(temp)
                                "hi" -> "इस बदलाव से फोन का तापमान लगभग %.1f°C हो जाएगा।".format(temp)
                                else -> "Simulated temperature under this condition reaches %.1f°C.".format(temp)
                            }
                        }
                        is dev.sovarix.core.SimulationUnsupported -> {
                            when (currentLang) {
                                "te" -> "ఈ అంశానికి సిమ్యులేషన్ ప్రస్తుతం అందుబాటులో లేదు."
                                "hi" -> "इस चर के लिए सिमुलेशन वर्तमान में उपलब्ध नहीं है।"
                                else -> "Simulation is unavailable for this specific variable."
                            }
                        }
                        else -> "Scenario simulation complete."
                    }
                    responseText = reply
                    voice.speak(reply, currentLang)
                }
            },
            onError = { err ->
                errorMessage = err
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            voice.stopListening()
            voice.stop()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SOVARIX VOICE COPILOT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = SovarixCyan
                    )
                    Text(
                        currentLang.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovarixTextMuted
                    )
                }

                // Pulsing Mic Visualizer
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(if (isListening) SovarixCyanSurface else SovarixSurface)
                        .border(
                            2.dp,
                            if (isListening) SovarixCyan else if (isSpeaking) SovarixGreen else SovarixBorder,
                            CircleShape
                        )
                        .clickable {
                            if (isListening) {
                                voice.stopListening()
                            } else {
                                errorMessage = null
                                voice.startListening(
                                    currentLang,
                                    onResult = { text ->
                                        scope.launch {
                                            val simResult = repo.simulateQuery(text)
                                            val reply = when (simResult) {
                                                is dev.sovarix.core.SimulationResult -> {
                                                    val temp = simResult.recommendedScenario?.predictedTemperatureC
                                                        ?: simResult.scenarios.firstOrNull()?.predictedTemperatureC
                                                        ?: 39.5
                                                    "Simulated temperature reaches %.1f°C.".format(temp)
                                                }
                                                is dev.sovarix.core.SimulationUnsupported -> simResult.reason
                                                else -> "Simulation complete."
                                            }
                                            responseText = reply
                                            voice.speak(reply, currentLang)
                                        }
                                    },
                                    onError = { errorMessage = it }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSpeaking) "🔊" else "🎙️",
                        fontSize = 28.sp
                    )
                }

                Text(
                    text = when {
                        isSpeaking -> "Speaking diagnosis..."
                        isListening -> "Listening in ${currentLang.uppercase()}..."
                        errorMessage != null -> errorMessage!!
                        else -> "Tap to speak a diagnostic query"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (errorMessage != null) SovarixRed else SovarixTextSecondary
                )

                // Transcribed spoken query
                if (spokenText.isNotEmpty()) {
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
                            fontWeight = FontWeight.Medium,
                            color = SovarixTextPrimary
                        )
                    }
                }

                // Spoken or generated diagnostic reply
                if (responseText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SovarixCyanSurface)
                            .border(1.dp, SovarixCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "INTELLIGENCE RESPONSE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SovarixCyan
                            )
                            Text(
                                text = responseText!!,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SovarixTextPrimary
                            )
                        }
                    }
                }

                // Dismiss Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SovarixSurfaceElevated)
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "DISMISS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = SovarixTextPrimary
                    )
                }
            }
        }
    }
}
