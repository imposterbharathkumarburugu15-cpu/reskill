package dev.sovarix.app.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * THE DIGITAL TWIN HERO: Intelligent Energy Field.
 *
 * The phone's living core with:
 * - Concentric orbital rings with dynamic velocity
 * - Micro-particles drifting in the thermal field
 * - Inward cooling pulse during AUTO-COOL state
 * - Integrated Live Thermal Trace vector signal
 * - Clean human-centric state labels: COOL, WARMING, HOT, AUTO-COOL, RECOVERING, STABLE.
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

    // Thermal States based on real physical metrics
    val isAutoCoolActive = strategy != AutoCoolStrategy.LEVEL_0_NORMAL
    val isCritical = (thermalStatus ?: 0) >= 4 || temp >= 45.0 || strategy >= AutoCoolStrategy.LEVEL_4_CRITICAL
    val isHot = !isCritical && ((thermalStatus ?: 0) >= 2 || temp >= 40.0)
    val isWarming = !isCritical && !isHot && (velocityPerMin >= 0.15)
    val isRecovery = !isCritical && !isHot && !isWarming && (trend?.isCooling == true || velocityPerMin <= -0.1)
    val isCool = !isCritical && !isHot && !isWarming && !isRecovery && temp < 37.0
    val isStable = !isCritical && !isHot && !isWarming && !isRecovery && !isCool

    // Human-centric semantic state label
    val stateLabel = when {
        isCritical -> "CRITICAL THERMAL"
        isAutoCoolActive -> "AUTO-COOL ACTIVE"
        isHot -> "THERMAL HIGH"
        isWarming -> "THERMAL RISING"
        isRecovery -> "RECOVERING"
        isCool -> "COOL / STABLE"
        else -> "STABLE"
    }

    // Dynamic semantic palette
    val coreColor = when {
        isCritical -> SovarixRed
        isHot -> SovarixOrange
        isWarming -> SovarixAmber
        isAutoCoolActive -> SovarixCyan
        isRecovery -> SovarixGreen
        else -> SovarixCyan
    }

    val glowColor = when {
        isCritical -> SovarixRed
        isHot -> SovarixOrange
        isWarming -> SovarixAmber
        isAutoCoolActive -> SovarixCyan
        isRecovery -> SovarixGreen
        else -> SovarixCyanLight
    }

    // Dynamic animation speeds: faster motion when warm/hot, calm when cool/recovering
    val orbitCycleMs = when {
        isCritical -> 1800
        isHot -> 2400
        isWarming -> 3600
        isAutoCoolActive -> 3000
        else -> 6000
    }

    val pulseDurationMs = when {
        isCritical -> 800
        isHot -> 1200
        isWarming -> 1800
        else -> 2600
    }

    val infiniteTransition = rememberInfiniteTransition(label = "digitalTwinCore")

    // Breathing pulse for core glow
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingPulse"
    )

    // Orbital ring 1 rotation
    val orbit1Angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(orbitCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit1Angle"
    )

    // Orbital ring 2 counter-rotation
    val orbit2Angle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((orbitCycleMs * 1.4f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit2Angle"
    )

    // Inward cooling pulse for Auto-Cool: travels from outside ring (1.0f) inward to core (0.2f)
    val inwardCoolingPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inwardCoolingPulse"
    )

    // Signal waveform phase for live thermal trace
    val tracePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tracePhase"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp),
            contentAlignment = Alignment.Center
        ) {
            // Intelligent Digital Twin Energy Field Canvas
            Canvas(modifier = Modifier.size(260.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val coreRadius = (size.minDimension / 2f) * 0.74f

                // 1. Outer Deep Atmospheric Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.16f * breathingPulse),
                            glowColor.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = coreRadius * 1.4f * breathingPulse
                    ),
                    center = center,
                    radius = coreRadius * 1.4f * breathingPulse
                )

                // 2. Dark Circular Digital Twin Core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SovarixDarkElevated.copy(alpha = 0.85f),
                            SovarixBlack.copy(alpha = 0.95f)
                        ),
                        center = center,
                        radius = coreRadius * 0.88f
                    ),
                    center = center,
                    radius = coreRadius * 0.88f
                )

                // 3. Thin Inner Orbital Ring (Dashed)
                val innerOrbitRadius = coreRadius * 0.82f
                drawCircle(
                    color = coreColor.copy(alpha = 0.22f),
                    center = center,
                    radius = innerOrbitRadius,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), orbit2Angle)
                    )
                )

                // 4. Outer Thermal Gradient Ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            coreColor.copy(alpha = 0.85f),
                            glowColor.copy(alpha = 0.30f),
                            coreColor.copy(alpha = 0.05f),
                            coreColor.copy(alpha = 0.85f)
                        ),
                        center = center
                    ),
                    center = center,
                    radius = coreRadius * breathingPulse,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )

                // 5. Inward Cooling Wave (Active during AUTO-COOL)
                if (isAutoCoolActive) {
                    val coolRadius = coreRadius * inwardCoolingPulse
                    val coolAlpha = (inwardCoolingPulse - 0.25f).coerceIn(0f, 1f) * 0.6f
                    drawCircle(
                        color = SovarixCyan.copy(alpha = coolAlpha),
                        center = center,
                        radius = coolRadius,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 6. Orbiting Data Particles
                val rad1 = Math.toRadians(orbit1Angle.toDouble())
                val particle1X = center.x + (coreRadius * breathingPulse) * cos(rad1).toFloat()
                val particle1Y = center.y + (coreRadius * breathingPulse) * sin(rad1).toFloat()
                drawCircle(
                    color = coreColor,
                    center = Offset(particle1X, particle1Y),
                    radius = 3.6.dp.toPx()
                )
                // Particle 1 trailing flare
                drawCircle(
                    color = glowColor.copy(alpha = 0.45f),
                    center = Offset(particle1X, particle1Y),
                    radius = 7.dp.toPx()
                )

                // Counter-orbiting secondary data point
                val rad2 = Math.toRadians(orbit2Angle.toDouble())
                val particle2X = center.x + innerOrbitRadius * cos(rad2).toFloat()
                val particle2Y = center.y + innerOrbitRadius * sin(rad2).toFloat()
                drawCircle(
                    color = glowColor.copy(alpha = 0.75f),
                    center = Offset(particle2X, particle2Y),
                    radius = 2.2.dp.toPx()
                )

                // Ambient tertiary micro-particles around the core
                val microParticleCount = if (isWarming || isHot || isCritical) 5 else 3
                for (i in 0 until microParticleCount) {
                    val pAngle = Math.toRadians((orbit1Angle * (1.2 + i * 0.3) + i * 72).toDouble())
                    val pDist = coreRadius * (0.65f + 0.18f * (i % 2))
                    val px = center.x + pDist * cos(pAngle).toFloat()
                    val py = center.y + pDist * sin(pAngle).toFloat()
                    drawCircle(
                        color = coreColor.copy(alpha = 0.35f),
                        center = Offset(px, py),
                        radius = 1.4.dp.toPx()
                    )
                }
            }

            // Center Content: Minimalist High-Tech HUD Readout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large High-Precision Digital Readout
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", temp),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.5).sp,
                        color = SovarixTextPrimary,
                        fontFamily = SovarixFontMono
                    )
                    Text(
                        text = "°C",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SovarixFontMono,
                        color = coreColor,
                        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Technical HUD Velocity Badge
                val velocitySign = if (velocityPerMin >= 0) "+" else ""
                val velocityText = String.format(Locale.US, "%s%.2f°/min", velocitySign, velocityPerMin)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SovarixDarkElevated)
                        .border(1.dp, coreColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "[ $stateLabel // $velocityText ]",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = SovarixFontMono,
                        color = coreColor
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Human-centric outcome subtext
                val verdict = when {
                    isAutoCoolActive -> "AUTO-COOL MITIGATION IN EFFECT"
                    isCritical -> "DEVICE THERMAL LIMIT REACHED"
                    isHot -> "THERMAL LOAD ELEVATED"
                    isWarming -> "THERMAL ACCELERATION DETECTED"
                    isRecovery -> "DISSIPATION ACTIVE // NORMALIZING"
                    else -> "THERMAL EQUILIBRIUM NOMINAL"
                }

                Text(
                    text = verdict,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    fontFamily = SovarixFontMono,
                    color = if (isAutoCoolActive) SovarixCyan else SovarixTextSecondary
                )
            }
        }

        // Live Thermal Trace: Miniature live signal trace directly below the hero
        LiveThermalTrace(
            velocityPerMin = velocityPerMin,
            color = coreColor,
            phase = tracePhase,
            modifier = Modifier
                .width(200.dp)
                .height(24.dp)
        )
    }
}

/**
 * Miniature Live Signal Trace.
 * Vector sparkline displaying the real thermal trajectory (rising / stable / falling).
 */
@Composable
private fun LiveThermalTrace(
    velocityPerMin: Double,
    color: Color,
    phase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // Slope is driven by real temperature velocity (°C/min)
        // positive velocity tilts upward to the right, negative velocity tilts downward
        val slopeY = when {
            velocityPerMin >= 0.15 -> -h * 0.32f
            velocityPerMin <= -0.1 -> h * 0.32f
            else -> 0f
        }

        val path = Path().apply {
            moveTo(0f, midY)
            cubicTo(
                w * 0.25f, midY,
                w * 0.45f, midY + slopeY * 0.5f,
                w * 0.70f, midY + slopeY
            )
            lineTo(w, midY + slopeY)
        }

        // Faint baseline
        drawLine(
            color = color.copy(alpha = 0.12f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        )

        // Live signal path
        drawPath(
            path = path,
            color = color.copy(alpha = 0.75f),
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Leading glowing signal head
        val headX = w
        val headY = midY + slopeY
        drawCircle(
            color = color,
            center = Offset(headX, headY),
            radius = 2.4.dp.toPx()
        )
        drawCircle(
            color = color.copy(alpha = 0.35f),
            center = Offset(headX, headY),
            radius = 5.dp.toPx()
        )
    }
}
