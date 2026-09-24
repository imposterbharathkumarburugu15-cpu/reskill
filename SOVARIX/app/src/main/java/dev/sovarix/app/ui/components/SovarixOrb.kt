package dev.sovarix.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.sovarix.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class SovarixOrbState {
    IDLE,
    LISTENING,
    THINKING,
    ANSWERING
}

/**
 * SOVARIX CORE: The phone twin's glowing intelligence orb.
 *
 * Signature floating element:
 * - Idle: slow rhythmic breathing
 * - Listening: subtle expansion with gentle pulse
 * - Thinking: accelerated micro-particle orbit
 * - Answering: calm settled radiance
 *
 * Tap summons the "ASK SOVARIX" twin copilot bottom sheet.
 */
@Composable
fun SovarixOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: SovarixOrbState = SovarixOrbState.IDLE,
    isSpeakingOrActive: Boolean = false
) {
    val activeState = if (isSpeakingOrActive) SovarixOrbState.LISTENING else state

    val pulsePeriod = when (activeState) {
        SovarixOrbState.LISTENING -> 900
        SovarixOrbState.THINKING -> 600
        SovarixOrbState.ANSWERING -> 1800
        SovarixOrbState.IDLE -> 2800
    }

    val rotationPeriod = when (activeState) {
        SovarixOrbState.THINKING -> 2000
        SovarixOrbState.LISTENING -> 4000
        SovarixOrbState.ANSWERING -> 8000
        SovarixOrbState.IDLE -> 10000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sovarixCoreOrb")

    val pulse by infiniteTransition.animateFloat(
        initialValue = if (activeState == SovarixOrbState.LISTENING) 0.96f else 0.92f,
        targetValue = if (activeState == SovarixOrbState.LISTENING) 1.14f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulsePeriod, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationPeriod, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )

    Box(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) * 0.82f

            // 1. Ambient High-Tech HUD Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SovarixCyan.copy(alpha = if (activeState == SovarixOrbState.LISTENING) 0.40f * pulse else 0.22f * pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.45f * pulse
                ),
                center = center,
                radius = radius * 1.45f * pulse
            )

            // 2. Pure OLED Dark Core Foundation
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SovarixDarkElevated, SovarixSurface, SovarixBlack),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )

            // 3. Razor-thin outer reticle border (1px HUD ring)
            drawCircle(
                color = SovarixBorderAccent,
                center = center,
                radius = radius,
                style = Stroke(width = 1.dp.toPx())
            )

            // 4. Cardinal Crosshair Ticks (0°, 90°, 180°, 270°)
            val tickLen = 4.dp.toPx()
            drawLine(
                color = SovarixCyan.copy(alpha = 0.7f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y - radius + tickLen),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = SovarixCyan.copy(alpha = 0.7f),
                start = Offset(center.x, center.y + radius - tickLen),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = SovarixCyan.copy(alpha = 0.7f),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x - radius + tickLen, center.y),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = SovarixCyan.copy(alpha = 0.7f),
                start = Offset(center.x + radius - tickLen, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1.2.dp.toPx()
            )

            // 5. Dynamic Segmented Orbital Track
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        SovarixCyan,
                        SovarixCyan.copy(alpha = 0.10f),
                        SovarixCyanLight.copy(alpha = 0.85f),
                        SovarixCyan
                    ),
                    center = center
                ),
                center = center,
                radius = radius * 0.90f * pulse,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 6. Primary High-Tech Radar Ping Particle
            val rad1 = Math.toRadians(rotation.toDouble())
            val orb1X = center.x + (radius * 0.90f * pulse) * cos(rad1).toFloat()
            val orb1Y = center.y + (radius * 0.90f * pulse) * sin(rad1).toFloat()
            drawCircle(
                color = SovarixCyanLight,
                center = Offset(orb1X, orb1Y),
                radius = 2.4.dp.toPx()
            )

            // 7. Counter Radar Node (Active states)
            if (activeState == SovarixOrbState.THINKING || activeState == SovarixOrbState.LISTENING) {
                val rad2 = Math.toRadians((360.0 - rotation * 1.5))
                val orb2X = center.x + (radius * 0.70f) * cos(rad2).toFloat()
                val orb2Y = center.y + (radius * 0.70f) * sin(rad2).toFloat()
                drawCircle(
                    color = SovarixGreen.copy(alpha = 0.9f),
                    center = Offset(orb2X, orb2Y),
                    radius = 2.0.dp.toPx()
                )
            }
        }

        // Inner Intelligence Core Symbol
        Icon(
            imageVector = SovarixIcons.Sparkle,
            contentDescription = "Ask SOVARIX Twin",
            tint = SovarixCyan,
            modifier = Modifier.size(18.dp)
        )
    }
}
