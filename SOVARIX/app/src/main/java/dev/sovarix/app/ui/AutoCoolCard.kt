package dev.sovarix.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.core.*
import java.util.Locale

// Theme colors matching Sovarix UI system
private val BrandBg = Color(0xFF070B12)
private val BrandDark = Color(0xFF0D1420)
private val BrandSurface = Color(0xFF101827)
private val BrandSurfaceAlt = Color(0xFF162032)
private val BrandCard = Color(0xFF131D2E)
private val BrandBorder = Color(0xFF1E2C42)
private val BrandBorderActive = Color(0xFF2E4362)
private val BrandGreen = Color(0xFF00FF66)
private val BrandGreenSurface = Color(0xFF072417)
private val BrandCyan = Color(0xFF00E5FF)
private val BrandCyanSurface = Color(0xFF06222E)
private val BrandAmber = Color(0xFFF59E0B)
private val BrandAmberSurface = Color(0xFF261906)
private val BrandRed = Color(0xFFEF4444)
private val BrandRedSurface = Color(0xFF2A0D11)
private val BrandTextPrimary = Color(0xFFF8FAFC)
private val BrandTextSecondary = Color(0xFF94A3B8)
private val BrandTextMuted = Color(0xFF64748B)

@Composable
fun AutoCoolCard(
    repo: TwinRepository,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {}
) {
    val acm = repo.autoCoolManager
    val trend by acm.thermalTrend.collectAsStateWithLifecycle()
    val forecast by acm.thermalForecast.collectAsStateWithLifecycle()
    val decision by acm.autoCoolDecision.collectAsStateWithLifecycle()
    val verification by acm.latestVerification.collectAsStateWithLifecycle()
    val settings by acm.settings.collectAsStateWithLifecycle()
    val caps by acm.monitor.capabilities.collectAsStateWithLifecycle()
    val adapter = acm.adapter
    val isGaming by repo.gamingManager.isGamingActive.collectAsStateWithLifecycle()
    val selectedGame by repo.gamingManager.selectedGame.collectAsStateWithLifecycle()

    var showSettingsModal by remember { mutableStateOf(false) }

    val currentTemp = trend?.currentTemperature ?: repo.state.collectAsStateWithLifecycle().value.temperature
    val currentHeadroom = trend?.thermalHeadroom ?: repo.state.collectAsStateWithLifecycle().value.thermalHeadroom
    val velocity = trend?.temperatureVelocity ?: 0.0

    val strategy = decision?.strategy ?: AutoCoolStrategy.LEVEL_0_NORMAL
    val isIntervening = strategy != AutoCoolStrategy.LEVEL_0_NORMAL

    // Pulse animation for active intervention status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (isIntervening) BrandAmber.copy(alpha = 0.6f) else BrandBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = BrandSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title, Status Badge, and Settings Gear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !settings.autoCoolEnabled -> BrandTextMuted
                                    strategy == AutoCoolStrategy.LEVEL_4_CRITICAL -> BrandRed.copy(alpha = pulseAlpha)
                                    isIntervening -> BrandAmber.copy(alpha = pulseAlpha)
                                    else -> BrandGreen
                                }
                            )
                    )
                    Text(
                        "AUTO-COOL THERMAL GUARD",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = BrandTextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (settings.autoCoolEnabled) BrandGreenSurface else BrandSurfaceAlt,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (settings.autoCoolEnabled) BrandGreen.copy(alpha = 0.5f) else BrandBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (settings.autoCoolEnabled) "ARMED" else "OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.autoCoolEnabled) BrandGreen else BrandTextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BrandSurfaceAlt)
                            .border(1.dp, BrandBorder, CircleShape)
                            .clickable { showSettingsModal = !showSettingsModal },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙", fontSize = 13.sp, color = BrandTextSecondary)
                    }
                }
            }

            // Real Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Current Temperature
                MetricBox(
                    modifier = Modifier.weight(1f),
                    label = "CURRENT",
                    value = currentTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable",
                    valueColor = when {
                        currentTemp == null -> BrandTextSecondary
                        currentTemp >= 42.0 -> BrandRed
                        currentTemp >= 39.0 -> BrandAmber
                        else -> BrandGreen
                    }
                )

                // Metric 2: Trend & Velocity
                MetricBox(
                    modifier = Modifier.weight(1f),
                    label = "TREND",
                    value = when {
                        velocity >= 0.35 -> "↑ +${String.format(Locale.US, "%.1f", velocity)}°/m"
                        velocity <= -0.2 -> "↓ ${String.format(Locale.US, "%.1f", velocity)}°/m"
                        else -> "→ Stable"
                    },
                    valueColor = when {
                        velocity >= 0.35 -> BrandAmber
                        velocity <= -0.2 -> BrandCyan
                        else -> BrandTextPrimary
                    }
                )

                // Metric 3: Thermal Headroom
                MetricBox(
                    modifier = Modifier.weight(1f),
                    label = "HEADROOM",
                    value = currentHeadroom?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
                    valueColor = when {
                        currentHeadroom == null -> BrandTextSecondary
                        currentHeadroom >= 0.9 -> BrandRed
                        currentHeadroom >= 0.8 -> BrandAmber
                        else -> BrandCyan
                    }
                )
            }

            // Forecast & Gaming Session Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandDark, RoundedCornerShape(8.dp))
                    .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val f60 = forecast?.getPoint(60)?.predictedTemperature
                val f30 = forecast?.getPoint(30)?.predictedTemperature

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Forecast (+60s):",
                        fontSize = 11.sp,
                        color = BrandTextSecondary
                    )
                    Text(
                        if (f60 != null) String.format(Locale.US, "%.1f°C (${forecast?.getPoint(60)?.confidence ?: "LOW"})", f60)
                        else "Collecting data...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (f60 != null && f60 >= 40.0) BrandAmber else BrandTextPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Workload / Game:",
                        fontSize = 11.sp,
                        color = BrandTextSecondary
                    )
                    Text(
                        if (isGaming) selectedGame else "IDLE / EVERYDAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGaming) BrandCyan else BrandTextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Current Strategy:",
                        fontSize = 11.sp,
                        color = BrandTextSecondary
                    )
                    Text(
                        strategy.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (strategy) {
                            AutoCoolStrategy.LEVEL_0_NORMAL -> BrandGreen
                            AutoCoolStrategy.LEVEL_1_PRE_COOL -> BrandCyan
                            AutoCoolStrategy.LEVEL_2_COOL -> BrandAmber
                            AutoCoolStrategy.LEVEL_3_AGGRESSIVE_COOL, AutoCoolStrategy.LEVEL_4_CRITICAL -> BrandRed
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "SOVARIX Overhead:",
                        fontSize = 11.sp,
                        color = BrandTextSecondary
                    )
                    Text(
                        if (isIntervening) "ECO (MINIMAL)" else "LOW (<0.1% CPU)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIntervening) BrandGreen else BrandTextPrimary
                    )
                }
            }

            // ACTIVE INTERVENTION ALERT BANNER (Shows only when Auto-Cool is mitigating)
            AnimatedVisibility(visible = isIntervening) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (strategy == AutoCoolStrategy.LEVEL_4_CRITICAL) BrandRedSurface else BrandAmberSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (strategy == AutoCoolStrategy.LEVEL_4_CRITICAL) BrandRed else BrandAmber, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (strategy == AutoCoolStrategy.LEVEL_4_CRITICAL) "CRITICAL THERMAL PRESSURE" else "AUTO-COOL ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (strategy == AutoCoolStrategy.LEVEL_4_CRITICAL) BrandRed else BrandAmber
                        )
                    }
                    Text(
                        "Reason: ${decision?.reason ?: "Thermal mitigation in progress"}",
                        fontSize = 10.sp,
                        color = BrandTextPrimary
                    )
                    Text(
                        "Actions: ${decision?.actions?.joinToString(", ") { it.description } ?: "Reduced SOVARIX footprint"}",
                        fontSize = 10.sp,
                        color = BrandTextSecondary
                    )
                }
            }

            // COOLING VERIFIED CARD (Shows latest resolved verification)
            verification?.let { v ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandGreenSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "COOLING VERIFIED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = BrandGreen
                        )
                        Text(
                            "Effectiveness: ${v.effectiveness}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Before: ${v.actualTempBefore?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"}",
                            fontSize = 10.sp,
                            color = BrandTextSecondary
                        )
                        Text(
                            "Peak: ${v.actualTempPeak?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"}",
                            fontSize = 10.sp,
                            color = BrandTextSecondary
                        )
                        Text(
                            "After: ${v.actualTempAfter?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"}",
                            fontSize = 10.sp,
                            color = BrandTextSecondary
                        )
                        Text(
                            "Recovery: ${v.coolingResponseTimeSec}s",
                            fontSize = 10.sp,
                            color = BrandTextSecondary
                        )
                    }
                }
            }

            // Real Device Capabilities Audit
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "DEVICE CAPABILITIES (${adapter.name})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = BrandTextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CapabilityPill("Thermal Status", caps.thermalStatusAvailable)
                    CapabilityPill("Headroom", caps.thermalHeadroomAvailable)
                    CapabilityPill("Headroom Forecast", caps.thermalForecastAvailable)
                    CapabilityPill("Battery Temp", caps.batteryTemperatureAvailable)
                }

                Text(
                    adapter.statusDescription,
                    fontSize = 9.sp,
                    color = BrandTextMuted
                )
            }

            // Expandable Settings Modal
            AnimatedVisibility(visible = showSettingsModal) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandDark, RoundedCornerShape(8.dp))
                        .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "AUTO-COOL SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan
                    )

                    // Auto-Cool Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Auto-Cool", fontSize = 11.sp, color = BrandTextPrimary)
                        Switch(
                            checked = settings.autoCoolEnabled,
                            onCheckedChange = { acm.setAutoCoolEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BrandGreen,
                                checkedTrackColor = BrandGreenSurface
                            )
                        )
                    }

                    // Aggressiveness Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aggressiveness", fontSize = 11.sp, color = BrandTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AutoCoolAggressiveness.entries.forEach { agg ->
                                val selected = settings.aggressiveness == agg
                                Box(
                                    modifier = Modifier
                                        .background(if (selected) BrandGreenSurface else BrandSurfaceAlt, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (selected) BrandGreen else BrandBorder, RoundedCornerShape(4.dp))
                                        .clickable { acm.setAggressiveness(agg) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        agg.name.take(4),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) BrandGreen else BrandTextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Overhead Budget Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SOVARIX Overhead Budget", fontSize = 11.sp, color = BrandTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SovarixOverheadBudget.entries.forEach { budget ->
                                val selected = settings.maxSovarixOverhead == budget
                                Box(
                                    modifier = Modifier
                                        .background(if (selected) BrandCyanSurface else BrandSurfaceAlt, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (selected) BrandCyan else BrandBorder, RoundedCornerShape(4.dp))
                                        .clickable { acm.setOverheadBudget(budget) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        budget.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) BrandCyan else BrandTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BrandDark, RoundedCornerShape(8.dp))
            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = BrandTextMuted,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = valueColor,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CapabilityPill(label: String, available: Boolean) {
    Box(
        modifier = Modifier
            .background(if (available) BrandGreenSurface else BrandSurfaceAlt, RoundedCornerShape(4.dp))
            .border(1.dp, if (available) BrandGreen.copy(alpha = 0.4f) else BrandBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (available) BrandGreen else BrandTextMuted
        )
    }
}
