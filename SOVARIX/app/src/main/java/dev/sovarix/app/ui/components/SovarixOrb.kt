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

/**
 * SOVARIX Orb: The intelligence core of the device.
 * A polished, breathing orbital energy sphere that invites interaction
 * and opens the "ASK SOVARIX" intelligence bottom sheet.
 */
@Composable
fun SovarixOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSpeakingOrActive: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeakingOrActive) 800 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeakingOrActive) 4000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )

    Box(
        modifier = modifier
            .size(58.dp)
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
            val radius = (size.minDimension / 2f) * 0.85f

            // Outer soft aura glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SovarixCyan.copy(alpha = 0.35f * pulse), Color.Transparent),
                    center = center,
                    radius = radius * 1.35f * pulse
                ),
                center = center,
                radius = radius * 1.35f * pulse
            )

            // Dark graphite core base
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SovarixSurfaceElevated, SovarixDark, SovarixBg),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )

            // Dynamic orbital energy ring
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        SovarixCyan,
                        SovarixCyan.copy(alpha = 0.15f),
                        SovarixCyan.copy(alpha = 0.8f),
                        SovarixCyan
                    ),
                    center = center
                ),
                center = center,
                radius = radius * 0.92f * pulse,
                style = Stroke(width = 2.dp.toPx())
            )

            // Orbital particle indicating physical life
            val rad = Math.toRadians(rotation.toDouble())
            val orbitalX = center.x + (radius * 0.92f * pulse) * cos(rad).toFloat()
            val orbitalY = center.y + (radius * 0.92f * pulse) * sin(rad).toFloat()
            drawCircle(
                color = SovarixCyan,
                center = Offset(orbitalX, orbitalY),
                radius = 2.5.dp.toPx()
            )
        }

        // Inner intelligence sparkle icon
        Icon(
            imageVector = SovarixIcons.Sparkle,
            contentDescription = "Ask SOVARIX",
            tint = SovarixCyan,
            modifier = Modifier.size(20.dp)
        )
    }
}
