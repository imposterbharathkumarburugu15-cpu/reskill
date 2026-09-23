package dev.sovarix.app.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.GameProfileRegistry
import dev.sovarix.core.SpecialMoment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Flagship Gaming Copilot Screen.
 *
 * Provides a media-first, hardware-accelerated gaming experience:
 * - Standby: Clean prompt to start session
 * - Live: Large game title, duration timer, temperature, performance status
 * - Minimal, truthful screen recording state
 * - Rich media horizontal carousel for Special Moments
 * - Video playback bottom sheet with telemetry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    repo: TwinRepository,
    onRequestScreenCapture: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gm = repo.gamingManager
    val s by repo.state.collectAsStateWithLifecycle()
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val selectedGame by gm.selectedGame.collectAsStateWithLifecycle()
    val selectedProfile by gm.selectedGameProfile.collectAsStateWithLifecycle()
    val sessionElapsedSec by gm.sessionElapsedSec.collectAsStateWithLifecycle()
    val moments by gm.moments.collectAsStateWithLifecycle()
    val hasProjection = gm.captureManager.hasProjection()

    val currentTemp = s.temperature ?: s.latest?.batteryC ?: 38.2
    val mm = sessionElapsedSec / 60
    val ss = sessionElapsedSec % 60
    val durationFormatted = String.format(Locale.US, "%02d:%02d", mm, ss)

    var selectedMomentForPlayback by remember { mutableStateOf<SpecialMoment?>(null) }
    var showGamePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // =========================================================================
        // 1. GAME INTELLIGENCE / LIVE SESSION HUD
        // =========================================================================
        if (isGaming) {
            // LIVE GAMING HUD
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LIVE indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SovarixGreen)
                        )
                        Text(
                            text = "LIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = SovarixGreen
                        )
                    }

                    Text(
                        text = "END SESSION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = SovarixRed,
                        modifier = Modifier
                            .clickable {
                                gm.autoStopSession()
                                onMessage("Gaming session ended")
                            }
                            .padding(4.dp)
                    )
                }

                // Large Game Title
                Text(
                    text = selectedGame.uppercase(Locale.US),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = SovarixTextPrimary
                )

                // Session Duration & Thermals
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = durationFormatted,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        color = SovarixTextPrimary
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.1f°", currentTemp),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = thermalSemanticColor(currentTemp, s.thermalState)
                        )
                        Text(
                            text = "PERFORMANCE STABLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SovarixGreen
                        )
                    }
                }

                // Minimal Screen Capture Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SovarixDark)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (hasProjection) SovarixRed else SovarixTextMuted)
                        )
                        Text(
                            text = if (hasProjection) "SCREEN CAPTURE ACTIVE" else "SCREEN CAPTURE STANDBY",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasProjection) SovarixTextPrimary else SovarixTextMuted
                        )
                    }

                    if (!hasProjection) {
                        Text(
                            text = "AUTHORIZE",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            color = SovarixCyan,
                            modifier = Modifier.clickable { onRequestScreenCapture() }
                        )
                    }
                }
            }
        } else {
            // STANDBY MODE
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "GAME INTELLIGENCE",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "Ready for your session.",
                        fontSize = 13.sp,
                        color = SovarixTextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SovarixCyan)
                            .clickable {
                                gm.autoStartSession(selectedProfile.packageName)
                                onMessage("Session started for ${selectedProfile.gameName}")
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "START SESSION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = SovarixDark
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SovarixDark)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(12.dp))
                            .clickable { showGamePicker = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedProfile.gameName.take(12).uppercase(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SovarixTextSecondary
                        )
                    }
                }
            }
        }

        // Thin separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SovarixBorder)
        )

        // =========================================================================
        // 2. SPECIAL MOMENTS (Media-First Video Gallery)
        // =========================================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                        Text(
                            text = "✦",
                            fontSize = 13.sp,
                            color = SovarixCyan
                        )
                        Text(
                            text = "SPECIAL MOMENTS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = SovarixTextPrimary
                        )
                    }
                    Text(
                        text = "Captured automatically.",
                        fontSize = 11.5.sp,
                        color = SovarixTextMuted
                    )
                }

                if (isGaming) {
                    Text(
                        text = "+ Capture Moment",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovarixCyan,
                        modifier = Modifier.clickable {
                            gm.triggerMoment(manual = true)
                            onMessage("Moment triggered!")
                        }
                    )
                }
            }

            if (moments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Clutch gameplay moments, rotation spikes, and key events will appear here.",
                        fontSize = 12.sp,
                        color = SovarixTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(moments) { moment ->
                        MomentMediaTile(
                            moment = moment,
                            onClick = { selectedMomentForPlayback = moment }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // =========================================================================
    // 3. VIDEO PLAYBACK BOTTOM SHEET
    // =========================================================================
    selectedMomentForPlayback?.let { moment ->
        ModalBottomSheet(
            onDismissRequest = { selectedMomentForPlayback = null },
            containerColor = SovarixDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = moment.gameName.uppercase(Locale.US),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "Score: ${moment.momentScore}/100",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovarixCyan
                    )
                }

                // Video Surface
                val file = moment.clipUri?.let { File(it) }
                if (file != null && file.exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.fromFile(file))
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SovarixSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Moment preserved as Device Event: ${moment.eventType}",
                            fontSize = 12.5.sp,
                            color = SovarixTextSecondary
                        )
                    }
                }

                // Telemetry Facts Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Confidence: ${(moment.confidence * 100).toInt()}%",
                        fontSize = 11.5.sp,
                        color = SovarixTextMuted
                    )
                    Text(
                        text = "Thermal: ${moment.temperature?.let { String.format(Locale.US, "%.1f°C", it) } ?: "38.5°C"}",
                        fontSize = 11.5.sp,
                        color = SovarixTextSecondary
                    )
                }

                Button(
                    onClick = { selectedMomentForPlayback = null },
                    colors = ButtonDefaults.buttonColors(containerColor = SovarixSurfaceElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = SovarixTextPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Game profile picker
    if (showGamePicker) {
        AlertDialog(
            onDismissRequest = { showGamePicker = false },
            containerColor = SovarixSurface,
            title = { Text("Select Game", color = SovarixTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameProfileRegistry.getAllProfiles().forEach { profile ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (profile.packageName == selectedProfile.packageName) SovarixCyanSurface else SovarixDark)
                                .clickable {
                                    gm.autoStartSession(profile.packageName)
                                    showGamePicker = false
                                    onMessage("Selected: ${profile.gameName}")
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = profile.gameName,
                                color = if (profile.packageName == selectedProfile.packageName) SovarixCyan else SovarixTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGamePicker = false }) {
                    Text("Cancel", color = SovarixTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun MomentMediaTile(
    moment: SpecialMoment,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SovarixDark)
            .border(1.dp, SovarixBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Thumbnail preview container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SovarixSurface),
            contentAlignment = Alignment.Center
        ) {
            // Play icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SovarixDark.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", fontSize = 13.sp, color = SovarixCyan)
            }

            // Score tag
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SovarixGreenSurface)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${moment.momentScore}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovarixGreen
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = moment.gameName,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = SovarixTextPrimary
            )
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(moment.timestamp)),
                fontSize = 10.sp,
                color = SovarixTextMuted
            )
        }
    }
}
