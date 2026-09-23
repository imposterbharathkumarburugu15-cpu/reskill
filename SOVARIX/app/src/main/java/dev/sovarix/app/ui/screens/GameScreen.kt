package dev.sovarix.app.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Brush
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
// File used for video playback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MEDIA-FIRST GAMING HUB
 *
 * Designed as a premium gaming companion:
 * - Minimal live gaming HUD with real duration & thermals
 * - Unobtrusive `● REC` indicator
 * - Subtle auto-capture toast confirmation
 * - Cinematic horizontal media carousel for Special Moments
 * - Full-screen video player bottom sheet with telemetry
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
    val isPreservingMoment by gm.isPreservingMoment.collectAsStateWithLifecycle()
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
        // 1. MOMENT CAPTURED NOTIFICATION PILL
        // =========================================================================
        AnimatedVisibility(
            visible = isPreservingMoment,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SovarixCyanSurface)
                    .border(1.dp, SovarixCyan, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✦", fontSize = 14.sp, color = SovarixCyan)
                Text(
                    text = "MOMENT CAPTURED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = SovarixCyanLight
                )
                Text("● SAVED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SovarixGreen)
            }
        }

        // =========================================================================
        // 2. LIVE GAMING SESSION HUD OR STANDBY PROMPT
        // =========================================================================
        if (isGaming) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live status & Game Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        if (hasProjection) {
                            Text(
                                text = "● REC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = SovarixRed
                            )
                        }
                    }

                    Text(
                        text = "END SESSION",
                        fontSize = 10.5.sp,
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

                Text(
                    text = selectedGame.uppercase(Locale.US),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = SovarixTextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = durationFormatted,
                        fontSize = 38.sp,
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
            }
        } else {
            // STANDBY MODE (Poetic & Clean)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "GAME",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.5.sp,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "Ready when you are.\nStart a session to let SOVARIX understand your gaming behavior.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = SovarixTextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(14.dp))
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
                            color = SovarixBlack
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SovarixDarkElevated)
                            .border(1.dp, SovarixBorder, RoundedCornerShape(14.dp))
                            .clickable { showGamePicker = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedProfile.gameName.take(14).uppercase(Locale.US),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SovarixTextSecondary
                        )
                    }
                }

                if (!hasProjection) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SovarixDark)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Screen highlight capture",
                            fontSize = 11.sp,
                            color = SovarixTextMuted
                        )
                        Text(
                            text = "AUTHORIZE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = SovarixCyan,
                            modifier = Modifier.clickable { onRequestScreenCapture() }
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
        // 3. SPECIAL MOMENTS (Cinematic Media-First Horizontal Carousel)
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
                    Text(
                        text = "SPECIAL MOMENTS",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = SovarixTextPrimary
                    )
                    Text(
                        text = "Captured automatically.",
                        fontSize = 11.sp,
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
                            onMessage("Moment captured!")
                        }
                    )
                }
            }

            if (moments.isEmpty()) {
                // Poetic empty state without empty boxes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Nothing captured yet.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SovarixTextSecondary
                    )
                    Text(
                        text = "Your next great moment could be here.",
                        fontSize = 11.5.sp,
                        color = SovarixTextMuted
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(moments) { moment ->
                        MomentCard(
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
    // 4. FULL SCREEN VIDEO & CONTEXTUAL BOTTOM SHEET
    // =========================================================================
    selectedMomentForPlayback?.let { moment ->
        ModalBottomSheet(
            onDismissRequest = { selectedMomentForPlayback = null },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Video Player
                val videoFile = moment.clipUri?.let { File(it) }
                if (videoFile != null && videoFile.exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setVideoURI(Uri.fromFile(videoFile))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Text(
                    text = moment.momentType.replace("_", " "),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = SovarixTextPrimary
                )

                // Moment Metadata
                val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                val timeStr = sdf.format(Date(moment.timestamp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SovarixDark)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetadataRow("GAME", moment.gamePackage.substringAfterLast('.'))
                    MetadataRow("TIMESTAMP", timeStr)
                    MetadataRow("THERMAL STATE", "${String.format(Locale.US, "%.1f°C", moment.temperature ?: 0.0)} (Status: ${moment.thermalState ?: 0})")
                    MetadataRow("BATTERY", "${(moment.battery ?: 0.0).toInt()}%")
                    MetadataRow("PERFORMANCE", if ((moment.confidence) < 0.6) "High Intensity" else "Stable")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Game Picker Dialog
    if (showGamePicker) {
        AlertDialog(
            onDismissRequest = { showGamePicker = false },
            title = { Text("SELECT GAME PROFILE", fontWeight = FontWeight.Black, color = SovarixTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameProfileRegistry.getAllProfiles().forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    gm.autoStartSession(profile.packageName)
                                    showGamePicker = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(profile.gameName, fontWeight = FontWeight.Bold, color = SovarixTextPrimary)
                            Text(profile.genre.name, fontSize = 10.sp, color = SovarixTextMuted)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = SovarixDarkElevated
        )
    }
}

@Composable
private fun MomentCard(
    moment: SpecialMoment,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(170.dp)
            .height(115.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(SovarixDarkElevated, SovarixDark)
                )
            )
            .border(1.dp, SovarixBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "▶ VIDEO",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = SovarixCyan
            )
            Text(
                text = moment.momentType.replace("_", " "),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = SovarixTextPrimary,
                maxLines = 1
            )
            Text(
                text = String.format(Locale.US, "%.1f°C", moment.temperature ?: 0.0),
                fontSize = 10.sp,
                color = SovarixTextSecondary
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SovarixTextMuted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SovarixTextPrimary)
    }
}
