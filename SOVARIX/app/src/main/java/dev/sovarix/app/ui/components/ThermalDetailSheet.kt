package dev.sovarix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import dev.sovarix.app.ui.AutoCoolCard
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.AutoCoolStrategy
import dev.sovarix.core.ThermalTrend
import java.util.Locale

/**
 * Thermal Detail Bottom Sheet.
 * Displays high-level physical thermal state when tapping the Thermal Hero.
 * Keeps deeper technical diagnostics behind the "VIEW DIAGNOSTICS" expandable action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalDetailSheet(
    repo: TwinRepository,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val liveSample by repo.liveTelemetry.collectAsStateWithLifecycle()
    val currentSample = s.latest ?: liveSample

    val acm = repo.autoCoolManager
    val trend by acm.thermalTrend.collectAsStateWithLifecycle()
    val decision by acm.autoCoolDecision.collectAsStateWithLifecycle()
    val forecast by acm.thermalForecast.collectAsStateWithLifecycle()
    val gm = repo.gamingManager
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val selectedGame by gm.selectedGame.collectAsStateWithLifecycle()

    val currentTemp = trend?.currentTemperature ?: currentSample?.batteryC ?: s.temperature ?: 37.2
    val currentHeadroom = trend?.thermalHeadroom ?: currentSample?.headroom?.toDouble() ?: 0.75
    val strategy = decision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL
    val isAutoCoolActive = strategy != AutoCoolStrategy.LEVEL_0_NORMAL
    val forecast60s = forecast?.getPoint(60)?.predictedTemperature ?: (currentTemp + 0.6)

    var showDiagnostics by remember { mutableStateOf(false) }

    val semanticColor = thermalSemanticColor(currentTemp, s.thermalState)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Text(
                    text = "THERMAL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = SovarixCyan
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SovarixSurface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAutoCoolActive) "AUTO-COOL ACTIVE" else "AUTO-COOL ARMED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAutoCoolActive) SovarixAmber else SovarixGreen
                    )
                }
            }

            // Big Temperature Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = String.format(Locale.US, "%.1f°", currentTemp),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                        color = SovarixTextPrimary
                    )

                    val velocity = trend?.temperatureVelocity ?: 0.0
                    val trendText = when {
                        trend?.isCooling == true || velocity <= -0.1 -> "↓ COOLING"
                        velocity >= 0.15 -> "↑ RISING"
                        else -> "• STABLE"
                    }

                    Text(
                        text = trendText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = semanticColor
                    )
                }

                // Headroom Meter
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "THERMAL HEADROOM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = SovarixTextMuted
                    )

                    // Visual Progress Bar
                    val blocks = 10
                    val filled = ((currentHeadroom.coerceIn(0.0, 1.0)) * blocks).toInt().coerceIn(1, blocks)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 0 until blocks) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (i < filled) SovarixCyan else SovarixBorder
                                    )
                            )
                        }
                    }
                    Text(
                        text = "${(currentHeadroom * 100).toInt()}% remaining",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SovarixTextSecondary
                    )
                }
            }

            // Primary Detail Key Facts (Clean typography & separators)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SovarixSurface)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FactRow(
                    label = "Forecast",
                    value = String.format(Locale.US, "%.1f° in 60 sec", forecast60s),
                    valueColor = if (forecast60s > currentTemp) SovarixAmber else SovarixTextPrimary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SovarixBorder)
                )

                FactRow(
                    label = "Gaming",
                    value = if (isGaming) selectedGame else "Device Standby",
                    valueColor = if (isGaming) SovarixCyan else SovarixTextSecondary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SovarixBorder)
                )

                FactRow(
                    label = "Auto-Cool",
                    value = if (isAutoCoolActive) "ACTIVE (${strategy.label})" else "ARMED & READY",
                    valueColor = if (isAutoCoolActive) SovarixAmber else SovarixGreen
                )
            }

            // VIEW DIAGNOSTICS Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SovarixSurfaceElevated)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(12.dp))
                    .clickable { showDiagnostics = !showDiagnostics }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showDiagnostics) "HIDE DIAGNOSTICS" else "VIEW DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = SovarixCyan
                )
            }

            // Advanced Diagnostics Expansion
            AnimatedVisibility(visible = showDiagnostics) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AutoCoolCard(
                        repo = repo,
                        onMessage = onMessage
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
    valueColor: Color = SovarixTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = SovarixTextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
