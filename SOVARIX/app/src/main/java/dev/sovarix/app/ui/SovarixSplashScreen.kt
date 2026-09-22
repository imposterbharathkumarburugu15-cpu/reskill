package dev.sovarix.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sovarix.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val SplashBg = Color(0xFF070B12)
private val SplashGreen = Color(0xFF00FF66)
private val SplashLime = Color(0xFFB5ED82)
private val SplashCyan = Color(0xFF00E5FF)
private val SplashTextMuted = Color(0xFF64748B)
private val SplashTextWhite = Color(0xFFF8FAFC)
private val SplashDarkSurface = Color(0xFF101827)

/**
 * High-end cinematic startup splash animation for SOVARIX MK-1.
 * Plays dynamic shield assembly, orbital radar scanlines, glowing brand reveal,
 * and system telemetry boot sequence before transitioning to the dashboard.
 */
@Composable
fun SovarixSplashScreen(
    onAnimationComplete: () -> Unit
) {
    var isExiting by remember { mutableStateOf(false) }

    // Core animation orchestrator
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val bootProgress = remember { Animatable(0f) }
    var bootStepText by remember { mutableStateOf("SYSTEM BOOT MK-1...") }

    // Continuous ambient loops
    val infiniteTransition = rememberInfiniteTransition(label = "ambientRings")
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterRotation"
    )
    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseWave"
    )

    // Sequence trigger
    LaunchedEffect(Unit) {
        // Phase 1: Logo & Glow Intro
        launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 500)
            )
        }
        launch {
            glowAlpha.animateTo(
                targetValue = 0.9f,
                animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            )
        }

        delay(350)
        // Phase 2: Brand Text Reveal
        launch {
            textAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 500)
            )
        }

        // Phase 3: Telemetry Diagnostics Boot Run
        delay(200)
        bootStepText = "INITIALIZING SENSORS..."
        bootProgress.animateTo(0.35f, tween(350, easing = FastOutSlowInEasing))

        delay(150)
        bootStepText = "CALIBRATING NEURAL TWIN..."
        bootProgress.animateTo(0.70f, tween(350, easing = FastOutSlowInEasing))

        delay(150)
        bootStepText = "SOVARIX SYSTEM READY"
        bootProgress.animateTo(1.0f, tween(300, easing = FastOutSlowInEasing))

        delay(400)
        // Phase 4: Smooth dismiss
        isExiting = true
        delay(250)
        onAnimationComplete()
    }

    val exitScale by animateFloatAsState(
        targetValue = if (isExiting) 1.15f else 1.0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "exitScale"
    )
    val exitAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1.0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "exitAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Tap to skip immediately
                if (!isExiting) {
                    isExiting = true
                    onAnimationComplete()
                }
            }
            .scale(exitScale)
            .alpha(exitAlpha),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Cyber Background Grid & Ambient Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            // Ambient Radial Energy Bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SplashGreen.copy(alpha = 0.14f * glowAlpha.value),
                        SplashCyan.copy(alpha = 0.06f * glowAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.7f
                )
            )

            // Animated Telemetry Reticles
            rotate(orbitRotation, pivot = center) {
                drawRadarRings(
                    center = center,
                    radius = 110.dp.toPx() * pulseWave,
                    color = SplashGreen.copy(alpha = 0.22f * logoAlpha.value)
                )
            }

            rotate(counterRotation, pivot = center) {
                drawHexagonGuides(
                    center = center,
                    radius = 85.dp.toPx(),
                    color = SplashCyan.copy(alpha = 0.25f * logoAlpha.value)
                )
            }
        }

        // Center Content Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // Hex Core Emblem with Neon Aura
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            ) {
                // Outer blurred pulse glow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseWave)
                        .clip(CircleShape)
                        .background(SplashGreen.copy(alpha = 0.25f * glowAlpha.value))
                        .blur(20.dp)
                )

                // Central Hexagon Badge Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0F261B), Color(0xFF0A1612))
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(SplashGreen, SplashCyan.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_sovarix),
                        contentDescription = "SOVARIX Logo",
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Title Reveal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    text = "S O V A R I X",
                    color = SplashTextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(SplashGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEURAL TWIN & GAMING ENGINE",
                        color = SplashCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(SplashGreen)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // System Boot Telemetry Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .alpha(textAlpha.value)
            ) {
                // Animated Progress Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF162032))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bootProgress.value)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(SplashCyan, SplashGreen)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bootStepText,
                        color = SplashTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${(bootProgress.value * 100).toInt()}%",
                        color = SplashGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Bottom skip hint
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .alpha(textAlpha.value * 0.6f)
        ) {
            Text(
                text = "TAP TO SKIP",
                color = SplashTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
    }
}

/**
 * Draws technical HUD circular reticle rings and crosshair notches
 */
private fun DrawScope.drawRadarRings(center: Offset, radius: Float, color: Color) {
    // Outer dashed circle
    drawCircle(
        color = color,
        radius = radius,
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        )
    )

    // Inner subtle ring
    drawCircle(
        color = color.copy(alpha = color.alpha * 0.5f),
        radius = radius * 0.72f,
        style = Stroke(width = 1f)
    )

    // 4 Corner crosshair ticks
    val tickLength = 12f
    val angles = listOf(0f, 90f, 180f, 270f)
    for (angle in angles) {
        val rad = Math.toRadians(angle.toDouble())
        val startX = center.x + (radius - tickLength) * cos(rad).toFloat()
        val startY = center.y + (radius - tickLength) * sin(rad).toFloat()
        val endX = center.x + (radius + tickLength) * cos(rad).toFloat()
        val endY = center.y + (radius + tickLength) * sin(rad).toFloat()
        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 2f
        )
    }
}

/**
 * Draws hexagonal guidance vertices around the core
 */
private fun DrawScope.drawHexagonGuides(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 6) {
        val angleRad = Math.toRadians((60.0 * i) - 30.0)
        val x = center.x + radius * cos(angleRad).toFloat()
        val y = center.y + radius * sin(angleRad).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
        )
    )
}
