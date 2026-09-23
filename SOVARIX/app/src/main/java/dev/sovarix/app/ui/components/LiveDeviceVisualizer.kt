package dev.sovarix.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.AutoCoolStrategy
import dev.sovarix.core.ThermalTrend
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flagship Thermal Hero: Dynamic Circular Thermal Field.
 *
 * Makes real device temperature the undisputed visual hero.
 * Reacts to REAL thermal state with restrained, semantic physical visualization.
 * No generic cards or boxes.
 */
@Composable
fun LiveDeviceVisualizer(
    temperatureC: Double?,
    thermalStatus: Int?,
    trend: ThermalTrend?,
    strategy: AutoCoolStrategy = AutoCoolStrategy.LEVEL_0_NORMAL,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val temp = temperatureC ?: 37.2
    val velocityPerMin = trend?.temperatureVelocity ?: 0.0
    val delta5Min = velocityPerMin * 5.0

    // Thermal States according to exact prompt specifications:
    // NORMAL, RISING, HOT, AUTO-COOL, RECOVERY, CRITICAL
    val isAutoCoolActive = strategy != AutoCoolStrategy.LEVEL_0_NORMAL
    val isCritical = (thermalStatus ?: 0) >= 4 || temp >= 45.0 || strategy >= AutoCoolStrategy.LEVEL_4_CRITICAL
    val isHot = !isCritical && ((thermalStatus ?: 0) >= 2 || temp >= 40.0)
    val isRising = !isCritical && !isHot && (velocityPerMin >= 0.15)
    val isRecovery = !isCritical && !isHot && !isRising && (trend?.isCooling == true || velocityPerMin <= -0.1)
    val isNormal = !isCritical && !isHot && !isRising && !isRecovery

    // Semantic state label
    val stateLabel = when {
        isCritical -> "CRITICAL THERMAL"
        isAutoCoolActive -> "AUTO-COOL ACTIVE"
        isHot -> "THERMAL HIGH"
        isRising -> "THERMAL RISING"
        isRecovery -> "THERMAL RECOVERING"
        else -> "COOL / STABLE"
    }

    // Semantic colors
    val primaryColor = when {
        isCritical -> SovarixRed
        isHot -> SovarixOrange
        isRising -> SovarixAmber
        isAutoCoolActive -> SovarixAmber
        isRecovery -> SovarixGreen
        else -> SovarixCyan
    }

    val secondaryColor = when {
        isCritical -> SovarixRed.copy(alpha = 0.35f)
        isHot -> SovarixOrange.copy(alpha = 0.3f)
        isRising -> SovarixAmber.copy(alpha = 0.25f)
        isRecovery -> SovarixGreen.copy(alpha = 0.3f)
        else -> SovarixCyan.copy(alpha = 0.2f)
    }

    // Restrained, non-excessive animation cycles
    val animationDuration = when {
        isCritical -> 900
        isHot -> 1400
        isRising -> 2000
        else -> 3400
    }

    val infiniteTransition = rememberInfiniteTransition(label = "thermalFieldTransition")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thermalPulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration * 7, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitalAngle"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic Circular Thermal Energy Field
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.76f

            // Outer soft ambient thermal glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.12f * pulse), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.32f * pulse
                ),
                center = center,
                radius = baseRadius * 1.32f * pulse
            )

            // Inner subtle background disc
            drawCircle(
                color = SovarixDark.copy(alpha = 0.6f),
                center = center,
                radius = baseRadius * 0.88f
            )

            // Dynamic pulsing outer thermal ring
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.75f),
                        secondaryColor,
                        primaryColor.copy(alpha = 0.15f),
                        primaryColor.copy(alpha = 0.75f)
                    ),
                    center = center
                ),
                center = center,
                radius = baseRadius * pulse,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Single orbital indicator particle
            val rad = Math.toRadians(rotationAngle.toDouble())
            val orbitalX = center.x + (baseRadius * pulse) * cos(rad).toFloat()
            val orbitalY = center.y + (baseRadius * pulse) * sin(rad).toFloat()
            drawCircle(
                color = primaryColor,
                center = Offset(orbitalX, orbitalY),
                radius = 3.5.dp.toPx()
            )
        }

        // Center Content: Hero Temperature & Semantic States
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visual Hero Temperature (Very Large Typography)
            Text(
                text = String.format(Locale.US, "%.1f°", temp),
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                color = SovarixTextPrimary,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(Modifier.height(2.dp))

            // Semantic Status Label
            Text(
                text = stateLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = primaryColor
            )

            Spacer(Modifier.height(4.dp))

            // Rate of change: ↑ +0.7°C / 5 min
            val arrow = if (delta5Min > 0.05) "↑" else if (delta5Min < -0.05) "↓" else "•"
            val sign = if (delta5Min > 0) "+" else if (delta5Min < 0) "-" else ""
            val rateText = "$arrow $sign${String.format(Locale.US, "%.1f", abs(delta5Min))}°C / 5 min"

            Text(
                text = rateText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = SovarixTextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.height(6.dp))

            // Human-centric outcome subtext
            val verdict = when {
                isAutoCoolActive -> "Auto-Cool is reducing available workload."
                isCritical -> "Device temperature is critical."
                else -> "AUTO-COOL ARMED"
            }

            Text(
                text = verdict,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = if (isAutoCoolActive) SovarixAmber else SovarixTextMuted
            )
        }
    }
}
