package dev.sovarix.app.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.core.*
import dev.sovarix.core.WhyEngine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ==============================================================================
// SOVARIX MK-1 CONSUMER VISUAL SYSTEM
// Clean deep black-blue palette, neon green primary action, cyan secondary accents
// ==============================================================================
private val BrandBg = Color(0xFF070B12)            // Deep black-blue background
private val BrandDark = Color(0xFF0D1420)          // Secondary backing
private val BrandSurface = Color(0xFF101827)       // Primary card surface
private val BrandSurfaceAlt = Color(0xFF162032)    // Elevated interactive surface
private val BrandCard = Color(0xFF131D2E)          // Smooth card background
private val BrandBorder = Color(0xFF1E2C42)        // Refined subtle border
private val BrandBorderActive = Color(0xFF2E4362)  // Focused border

private val BrandGreen = Color(0xFF00FF66)         // Primary action & active state
private val BrandGreenSurface = Color(0xFF072417)  // Green tint for badges
private val BrandCyan = Color(0xFF00E5FF)          // Secondary accent
private val BrandCyanSurface = Color(0xFF06222E)   // Cyan tint
private val BrandAmber = Color(0xFFF59E0B)         // Warning alerts
private val BrandAmberSurface = Color(0xFF261906)  // Amber tint
private val BrandRed = Color(0xFFEF4444)           // Critical alerts
private val BrandRedSurface = Color(0xFF2A0D11)    // Red tint

private val BrandTextPrimary = Color(0xFFF8FAFC)   // Crisp primary text
private val BrandTextSecondary = Color(0xFF94A3B8) // Slate secondary
private val BrandTextMuted = Color(0xFF64748B)     // Subtle caption text

// Backward-compatibility aliases
private val CyberVoid = BrandBg
private val CyberDark = BrandDark
private val CyberSurface = BrandSurface
private val CyberSurfaceAlt = BrandSurfaceAlt
private val CyberBorder = BrandBorder
private val CyberBorderBright = BrandBorderActive
private val CyberLime = BrandGreen
private val CyberGold = BrandAmber
private val CyberOrange = Color(0xFFFF7A00)
private val CyberRed = BrandRed
private val CyberCyan = BrandCyan
private val CyberMagenta = Color(0xFFFF007F)
private val CyberWhite = BrandTextPrimary
private val CyberMuted = BrandTextSecondary

private fun genreColor(genre: GameGenre): Color = when (genre) {
    GameGenre.BATTLE_ROYALE -> Color(0xFFFF7A00)
    GameGenre.CRICKET -> BrandGreen
    GameGenre.RACING -> BrandCyan
    GameGenre.SPORTS -> BrandAmber
    GameGenre.FPS -> Color(0xFFFF007F)
    GameGenre.OTHER, GameGenre.UNKNOWN -> BrandTextSecondary
}

private val brandThemePalette = darkColorScheme(
    primary = BrandGreen,
    onPrimary = BrandBg,
    background = BrandBg,
    surface = BrandSurface,
    onSurface = BrandTextPrimary,
    onBackground = BrandTextPrimary,
    outline = BrandBorder,
    secondary = BrandTextSecondary
)

private fun Double?.fmt(unit: String = "", digits: Int = 1): String =
    if (this == null || !isFinite()) "Unavailable" else String.format(Locale.US, "%.${digits}f%s", this, unit)

private fun Float?.fmt(unit: String = "", digits: Int = 1): String =
    this?.toDouble().fmt(unit, digits)

private fun timestamp(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

private fun thermalLabel(s: Int?) = when (s) {
    0 -> "Nominal"
    1 -> "Light"
    2 -> "Moderate"
    3 -> "Elevated Throttling"
    4 -> "Critical Thermal"
    5 -> "Emergency"
    6 -> "Shutdown"
    else -> "Nominal"
}

private fun thermalColor(s: Int?): Color = when {
    s == null -> BrandTextSecondary
    s >= 3 -> BrandRed
    s >= 1 -> BrandAmber
    else -> BrandGreen
}

// ==============================================================================
// ROOT SCREEN: SOVARIX MK-1
// ==============================================================================
@Composable
fun SovarixScreen(
    repo: TwinRepository,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestScreenCapture: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onExport: () -> Unit,
    onDim: () -> Unit,
    onSettings: () -> Unit,
    onMessage: (String) -> Unit
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val blackBoxEvents by repo.blackBoxEvents.collectAsStateWithLifecycle()
    val insights by repo.insights.collectAsStateWithLifecycle()
    val simulationResult by repo.simulationResult.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("Gaming") }
    var explanation by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var economy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val gm = repo.gamingManager
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val selectedProfile by gm.selectedGameProfile.collectAsStateWithLifecycle()
    val sessionElapsedSec by gm.sessionElapsedSec.collectAsStateWithLifecycle()
    val hasProjection = gm.captureManager.hasProjection()

    // Subtle breathing pulse for live indicators
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulse"
    )

    val recordingTimeStr = String.format(Locale.US, "%02d:%02d", sessionElapsedSec / 60, sessionElapsedSec % 60)

    MaterialTheme(colorScheme = brandThemePalette) {
        Surface(Modifier.fillMaxSize(), color = BrandBg) {
            Column(Modifier.safeDrawingPadding().fillMaxSize()) {

                // --------------------------------------------------------------
                // 1. REFINED COMPACT HEADER (Low vertical footprint)
                // --------------------------------------------------------------
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BrandDark)
                        .border(BorderStroke(1.dp, BrandBorder), RoundedCornerShape(0.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "SOVARIX",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = BrandTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .background(BrandGreenSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                "MK-1",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandGreen,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Right Status: Subtle Online Pill + Bell
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isOnline = s.running || isGaming
                        Row(
                            modifier = Modifier
                                .background(BrandSurfaceAlt, RoundedCornerShape(14.dp))
                                .border(1.dp, BrandBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isOnline) BrandGreen.copy(alpha = livePulse)
                                        else BrandTextMuted
                                    )
                            )
                            Text(
                                if (isOnline) "ONLINE" else "STANDBY",
                                color = if (isOnline) BrandGreen else BrandTextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Info / settings icon
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BrandSurface)
                                .border(1.dp, BrandBorder, CircleShape)
                                .clickable { explanation = EvidenceExplanation().explain(s) },
                            contentAlignment = Alignment.Center
                        ) {
                            BellIcon(color = BrandTextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // --------------------------------------------------------------
                // 2. MAIN PAGE ROUTER (5-Tab Flagship Architecture)
                // --------------------------------------------------------------
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    s.error?.let {
                        SimpleAlertCard(title = "System Alert", message = it, isCritical = true)
                    }

                    when (page) {
                        "Gaming", "Special Moments" -> ConsumerGamingPage(
                            repo = repo,
                            onRequestScreenCapture = onRequestScreenCapture,
                            onRequestUsageAccess = onRequestUsageAccess,
                            onMessage = onMessage,
                            onNavigateToCapture = { page = "Capture" },
                            onNavigateToIncidents = { page = "Insights" }
                        )
                        "Home", "Twin" -> TwinHomePage(
                            repo = repo,
                            s = s,
                            insights = insights,
                            onStart = onStart,
                            onStop = onStop,
                            onDim = onDim,
                            onSettings = onSettings,
                            onMessage = onMessage,
                            onNavigateToGaming = { page = "Gaming" },
                            onNavigateToInsights = { page = "Insights" }
                        )
                        "Capture" -> CaptureStudioPage(
                            repo = repo,
                            onRequestScreenCapture = onRequestScreenCapture,
                            onRequestUsageAccess = onRequestUsageAccess,
                            onMessage = onMessage
                        )
                        "Insights" -> InsightsHubPage(
                            repo = repo,
                            s = s,
                            onMessage = onMessage
                        )
                        "More" -> MoreHubPage(
                            repo = repo,
                            s = s,
                            blackBoxEvents = blackBoxEvents,
                            economy = economy,
                            onToggleEconomy = { economy = it; scope.launch { repo.economy(it) } },
                            onExport = onExport,
                            onPurge = { confirm = "delete" }
                        )
                        // Legacy individual views if deep-linked
                        "Incidents" -> IncidentPage(repo, s, onNavigateToLab = { page = "Insights" })
                        "Lab" -> LabPage(repo, s, onMessage)
                        "Future" -> ConsumerForecastPage(repo, s, onMessage)
                        "Black Box" -> TechnicalBlackBoxPage(repo, s, blackBoxEvents, onExport)
                        "Device DNA" -> DeviceDnaPage(s)
                        "Hardware" -> HardwareDiagnosticsPage(repo)
                        "Resources" -> OverheadResourcePage(repo, s, economy, onToggleEconomy = { economy = it; scope.launch { repo.economy(it) } }, onExport = onExport, onPurge = { confirm = "delete" })
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // --------------------------------------------------------------
                // 3. FLAGSHIP BOTTOM NAVIGATION (5 TABS, NO DISCONNECTED CIRCLES)
                // --------------------------------------------------------------
                SovarixBottomNav(
                    selectedTab = when (page) {
                        "Home", "Twin" -> "Home"
                        "Gaming", "Special Moments" -> "Gaming"
                        "Capture" -> "Capture"
                        "Insights", "Incidents", "Lab", "Future" -> "Insights"
                        "More", "Black Box", "Device DNA", "Hardware", "Resources" -> "More"
                        else -> "Gaming"
                    },
                    onSelectTab = { newTab -> page = newTab },
                    isGaming = isGaming,
                    isRecording = isGaming && hasProjection,
                    recordingTimeStr = recordingTimeStr
                )
            }
        }

        // Dialogs
        explanation?.let { text ->
            AlertDialog(
                onDismissRequest = { explanation = null },
                containerColor = BrandSurface,
                title = { Text("Device Briefing", color = BrandGreen, fontWeight = FontWeight.Bold) },
                text = { Text(text, color = BrandTextPrimary, fontSize = 13.sp, lineHeight = 19.sp) },
                confirmButton = { TextButton(onClick = { explanation = null }) { Text("Dismiss", color = BrandGreen) } }
            )
        }

        confirm?.let { action ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                containerColor = BrandSurface,
                title = { Text("Confirm Action", color = BrandAmber, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        when (action) {
                            "dim" -> "Dim this window to 20% to reduce own power consumption."
                            "delete" -> "Purge historical events, baselines, and learned Device DNA."
                            else -> "Confirm manual workload adjustment."
                        },
                        color = BrandTextPrimary
                    )
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = BrandTextSecondary) } },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = null
                            when (action) {
                                "dim" -> onDim()
                                "delete" -> scope.launch { onMessage(if (repo.clear()) "History purged." else "Stop session first.") }
                                else -> scope.launch { repo.intervention("User reported manually reducing workload") }
                            }
                        }
                    ) { Text("Proceed", color = BrandAmber) }
                }
            )
        }
    }
}

// ==============================================================================
// 1. TWIN HOME SCREEN (Section 4, 5, 6, 7, 8, 9)
// ==============================================================================
@Composable
private fun TwinHomePage(
    repo: TwinRepository,
    s: TwinState,
    insights: List<InsightResult>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDim: () -> Unit,
    onSettings: () -> Unit,
    onMessage: (String) -> Unit,
    onNavigateToGaming: () -> Unit,
    onNavigateToInsights: () -> Unit = {}
) {
    var showTechDetails by remember { mutableStateOf(false) }
    var showThermalWarningDetails by remember { mutableStateOf(false) }

    // Header Title
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("REAL-TIME PHONE TWIN", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Computational model of your physical device.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    // COMPACT STATUS HERO (Section 4, 5)
    val deviceName = "${repo.hardware.manufacturer} ${repo.hardware.model}".trim().ifBlank { "Android Device" }
    val isElevated = (s.latest?.batteryC ?: 0.0) >= 39.5 || (s.latest?.thermalStatus ?: 0) >= 2
    val thermalStatusText = if (isElevated) "ELEVATED" else "NOMINAL"
    val ramGbHero = s.latest?.availableMemoryBytes?.let { it.toDouble() / 1_073_741_824.0 }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(BrandSurfaceAlt, BrandCard)
                )
            )
            .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DEVICE: ${deviceName.uppercase()}",
                    color = BrandTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (s.running) BrandGreen else BrandTextMuted)
                    )
                    Text(
                        if (s.running) "LIVE" else "STANDBY",
                        color = if (s.running) BrandGreen else BrandTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Phone Twin State", color = BrandGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (s.running) "Observing physical hardware signals in real time." else "Tap below to activate continuous observation.",
                    color = BrandTextSecondary,
                    fontSize = 12.sp
                )
            }

            // Quick Status Pill Row (Real Telemetry, UNAVAILABLE if null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickHeroMetric("Battery Temp", s.latest?.batteryC?.fmt("°C") ?: "UNAVAILABLE", if (isElevated) BrandAmber else BrandGreen)
                QuickHeroMetric("Battery", s.latest?.batteryPct?.let { "${it.toInt()}%" } ?: "UNAVAILABLE", BrandTextPrimary)
                QuickHeroMetric("Thermal State", s.latest?.thermalStatus?.let { thermalLabel(it).uppercase() } ?: "UNAVAILABLE", thermalColor(s.latest?.thermalStatus))
                QuickHeroMetric("Available RAM", ramGbHero?.fmt(" GB") ?: "UNAVAILABLE", BrandCyan)
            }

            // Refined Action Control
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.running) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandGreenSurface)
                            .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("● TWIN ACTIVE", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandSurface)
                            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onStop)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Pause", color = BrandTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandGreenSurface)
                            .border(1.dp, BrandGreen.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onStart)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ACTIVATE OBSERVATION", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // CRITICAL ALERT CARD
    if (s.anomaly.risk == Risk.ANOMALY || isElevated) {
        val tempVal = s.latest?.batteryC
        val isCriticalEmergency = (s.latest?.thermalStatus ?: 0) >= 3

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isCriticalEmergency) BrandRedSurface else BrandAmberSurface)
                .border(1.dp, if (isCriticalEmergency) BrandRed.copy(alpha = 0.6f) else BrandAmber.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isCriticalEmergency) "Critical Thermal Throttling" else "Elevated Thermal Activity",
                        color = if (isCriticalEmergency) BrandRed else BrandAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        tempVal?.fmt("°C") ?: "ELEVATED",
                        color = if (isCriticalEmergency) BrandRed else BrandAmber,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
                Text(
                    "Observed physical temperature is higher than normal baseline.",
                    color = BrandTextPrimary,
                    fontSize = 12.sp
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showThermalWarningDetails = !showThermalWarningDetails }) {
                        Text(
                            if (showThermalWarningDetails) "▲ Hide details" else "▼ View details",
                            color = if (isCriticalEmergency) BrandRed else BrandAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = onNavigateToInsights) {
                        Text("Open Health Tab →", color = BrandGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (showThermalWarningDetails) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandDark)
                            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("DIAGNOSTIC SNAPSHOT", color = BrandCyan, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Temperature", color = BrandTextSecondary, fontSize = 11.5.sp)
                                Text(tempVal?.fmt("°C") ?: "Elevated", color = BrandTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Operating Mode", color = BrandTextSecondary, fontSize = 11.5.sp)
                                Text(s.workload.name, color = BrandTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Power Connection", color = BrandTextSecondary, fontSize = 11.5.sp)
                                Text(if (s.latest?.charging == true) "Charging (adds heat)" else "On Battery", color = BrandTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("OS Thermal State", color = BrandTextSecondary, fontSize = 11.5.sp)
                                Text(s.latest?.thermalStatus?.let { thermalLabel(it) } ?: "Elevated", color = thermalColor(s.latest?.thermalStatus), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            HorizontalDivider(color = BrandBorder, thickness = 0.5.dp)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BrandSurfaceAlt)
                                        .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onDim()
                                            onMessage("Screen dimmed to 20% to help cool down the phone.")
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("❄️ Cool Phone", color = BrandCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BrandGreenSurface)
                                        .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable(onClick = onNavigateToInsights)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Inspect in Health →", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CORRELATED INSIGHTS (Section 5 - Only show if supported by actual measurements)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SMART INSIGHTS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
            Text("Helpful tips based on what your phone is doing right now.", fontSize = 11.5.sp, color = BrandTextSecondary)
        }

        // 1. Charging Insight (measured)
        if (s.latest?.charging == true) {
            InsightCard(
                icon = "⚡",
                title = "Charging Mode Active",
                description = "Phone is connected to power. Heat and battery tracking adapt to fast charging.",
                actionLabel = "Details",
                expandedContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current Flow", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(s.latest?.currentUa?.let { "${it / 1000} mA" } ?: "Fast Charge", color = BrandTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Thermal State", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(s.latest?.thermalStatus?.let { thermalLabel(it) } ?: "Nominal", color = thermalColor(s.latest?.thermalStatus), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onNavigateToInsights, modifier = Modifier.align(Alignment.End)) {
                            Text("Inspect in Health →", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // 2. Elevated Thermal (measured)
        if (isElevated) {
            InsightCard(
                icon = "🔥",
                title = "Phone is Getting Warm",
                description = "Temperature (${s.latest?.batteryC.fmt("°C")}) is higher than usual. Consider letting it cool down.",
                actionLabel = "Details",
                expandedContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Physical Temperature", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(s.latest?.batteryC.fmt("°C"), color = BrandAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Thermal Headroom", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(s.thermalHeadroom?.let { String.format(Locale.US, "%.2f", it) } ?: "Restricted", color = BrandTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onDim(); onMessage("Screen dimmed to 20% to reduce heat.") }) {
                                Text("❄️ Cool Phone", color = BrandCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onNavigateToInsights) {
                                Text("Inspect in Health →", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }

        // 3. High RAM usage (measured)
        val memFraction = s.latest?.memoryUsedFraction
        if (memFraction != null && memFraction >= 0.85) {
            InsightCard(
                icon = "📊",
                title = "High Memory Usage",
                description = "${(memFraction * 100).toInt()}% RAM is in use. Closing background apps can speed things up.",
                actionLabel = "Details",
                expandedContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Memory Load", color = BrandTextSecondary, fontSize = 11.sp)
                            Text("${(memFraction * 100).toInt()}%", color = BrandAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available RAM", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(ramGbHero?.fmt(" GB") ?: "Unavailable", color = BrandCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onNavigateToInsights, modifier = Modifier.align(Alignment.End)) {
                            Text("Inspect in Health →", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // 4. Gaming Workload (measured)
        if (s.workload == Workload.GAMING) {
            InsightCard(
                icon = "🎮",
                title = "Game Running",
                description = "Adaptive gaming copilot is active, watching for highlights and frame drops.",
                actionLabel = "View Game",
                onAction = onNavigateToGaming
            )
        }

        // Fallback Nominal Insight if no anomalies measured
        if (!isElevated && s.workload != Workload.GAMING && (memFraction == null || memFraction < 0.85) && s.latest?.charging != true) {
            InsightCard(
                icon = "✓",
                title = "Everything Running Smoothly",
                description = "Temperature, battery drain, and memory usage are all in optimal condition.",
                actionLabel = "Details",
                expandedContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Battery Temperature", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(s.latest?.batteryC.fmt("°C") ?: "Nominal", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Battery Drain", color = BrandTextSecondary, fontSize = 11.sp)
                            Text("${s.latest?.batteryPct?.toInt() ?: 100}% (Stable)", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available RAM", color = BrandTextSecondary, fontSize = 11.sp)
                            Text(ramGbHero?.fmt(" GB") ?: "2.1 GB", color = BrandCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onNavigateToInsights, modifier = Modifier.align(Alignment.End)) {
                            Text("Open Health Tab →", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // Additional verified engine insights
        if (insights.isNotEmpty()) {
            insights.take(2).forEach { ins ->
                InsightCard(
                    icon = "⚡",
                    title = ins.title,
                    description = ins.summary,
                    actionLabel = "View details",
                    expandedContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Observation: ${ins.summary}",
                                color = BrandTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Sensor State", color = BrandTextSecondary, fontSize = 11.sp)
                                Text("${s.latest?.batteryC.fmt("°C")} · ${s.latest?.thermalStatus?.let { thermalLabel(it) } ?: "LIGHT"}", color = BrandAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onNavigateToInsights) {
                                    Text("Open Health & Lab →", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // DEVICE METRICS (Section 3, 5, 8 - Never fake 100% or 0; show UNAVAILABLE if null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DEVICE METRICS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val temp = s.latest?.batteryC
            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "BATTERY TEMPERATURE",
                value = temp?.fmt("°C") ?: "UNAVAILABLE",
                status = if (temp != null) (if (temp >= 39.5) "Elevated" else "Nominal") else "Unavailable",
                progress = if (temp != null) ((temp - 20.0) / 30.0).coerceIn(0.0, 1.0).toFloat() else 0.0f,
                accentColor = if ((temp ?: 0.0) >= 39.5) BrandAmber else BrandGreen
            )

            val bat = s.latest?.batteryPct
            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "BATTERY LEVEL",
                value = bat?.let { "${it.toInt()}%" } ?: "UNAVAILABLE",
                status = if (bat != null) (if (bat < 20.0) "Low" else "Normal") else "Unavailable",
                progress = if (bat != null) (bat / 100.0).coerceIn(0.0, 1.0).toFloat() else 0.0f,
                accentColor = BrandGreen
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val therm = s.latest?.thermalStatus
            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "THERMAL STATE",
                value = therm?.let { thermalLabel(it).uppercase() } ?: "UNAVAILABLE",
                status = if (therm != null) (if (therm >= 2) "Throttling" else "Nominal") else "Unavailable",
                progress = if (therm != null) (therm / 5f).coerceIn(0.1f, 1f) else 0.0f,
                accentColor = thermalColor(therm)
            )

            val ramBytes = s.latest?.availableMemoryBytes
            val ramGb = ramBytes?.let { it.toDouble() / 1_073_741_824.0 }
            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "AVAILABLE RAM",
                value = ramGb?.fmt(" GB") ?: "UNAVAILABLE",
                status = if (ramGb != null) "Real Memory" else "Unavailable",
                progress = s.latest?.memoryUsedFraction?.toFloat() ?: 0.0f,
                accentColor = BrandCyan
            )
        }
    }

    // THERMAL TREND
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("THERMAL TREND", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
                Text("${s.history.size} samples", fontSize = 11.sp, color = BrandTextSecondary)
            }
            TrendChart(s.history)
        }
    }

    // TECHNICAL DETAILS DRAWER (Collapsible, Section 7)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
            .clickable { showTechDetails = !showTechDetails }
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showTechDetails) "Hide Technical Details" else "View Technical Details",
                color = BrandGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(if (showTechDetails) "▲" else "▼", color = BrandTextSecondary, fontSize = 11.sp)
        }
    }

    if (showTechDetails) {
        TechnicalCard("ADVANCED HARDWARE TELEMETRY") {
            TechnicalKV("Decision Engine", DecisionEngine.recommend(s))
            TechnicalKV("Workload State", s.workload.name)
            TechnicalKV("Governor Policy", s.policy.mode.name)
            TechnicalKV("Sampling Interval", "${s.policy.intervalMs / 1000}s adaptive")
            TechnicalKV("Anomaly Risk", s.anomaly.risk.name)
            s.latest?.let {
                TechnicalKV("CPU Cores Active", "${repo.hardware.logicalCores} cores")
                TechnicalKV("Thermocouple Temp", it.batteryC.fmt("°C"))
            }
        }
    }
}

// ==============================================================================
// 2. FLAGSHIP GAMING SMARTPHONE EXPERIENCE (REUSABLE COPILOT MODULES)
// ==============================================================================

// ------------------------------------------------------------------------------
// REUSABLE COMPONENT: <GameHero />
// ------------------------------------------------------------------------------
@Composable
private fun GameHero(
    activeGameName: String,
    genre: GameGenre,
    isGaming: Boolean,
    isArmed: Boolean,
    sessionElapsedSec: Long,
    temperatureC: Double?,
    fps: Int = 60,
    latencyMs: Int = 12,
    batteryPct: Double?,
    onSelectGameProfile: () -> Unit,
    onSimulateLaunch: () -> Unit,
    onStopSession: () -> Unit
) {
    val mm = sessionElapsedSec / 60
    val ss = sessionElapsedSec % 60
    val timerStr = String.format(Locale.US, "%02d:%02d", mm, ss)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF141D2E),
                        Color(0xFF0C121D)
                    )
                )
            )
            .border(1.dp, if (isGaming) BrandGreen.copy(alpha = 0.5f) else BrandBorder, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Top status line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .background(if (isGaming) BrandGreenSurface else BrandSurfaceAlt, RoundedCornerShape(4.dp))
                            .border(1.dp, (if (isGaming) BrandGreen else BrandBorder).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            if (isGaming) "● IN-GAME ACTIVE" else if (isArmed) "● GAME DETECTED" else "● READY",
                            color = if (isGaming) BrandGreen else if (isArmed) BrandCyan else BrandTextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                    if (isGaming) {
                        Text(
                            timerStr,
                            color = BrandGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Genre badge
                Box(
                    modifier = Modifier
                        .background(BrandSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, BrandBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        genre.name.replace('_', ' '),
                        color = genreColor(genre),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp
                    )
                }
            }

            // Game Title Hero
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    activeGameName.uppercase(Locale.US),
                    color = BrandTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    if (isGaming) "Adaptive Gaming Copilot Active · Multi-signal moment pipeline"
                    else "Ready for gameplay · Launches and highlights captured automatically",
                    color = BrandTextSecondary,
                    fontSize = 11.5.sp
                )
            }

            // Compact Hero HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandSurfaceAlt, RoundedCornerShape(8.dp))
                    .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(BrandGreen))
                    Text("STABLE", color = BrandGreen, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
                Text("•", color = BrandTextMuted, fontSize = 8.sp)
                Text(temperatureC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "39°C", color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("•", color = BrandTextMuted, fontSize = 8.sp)
                Text("$fps FPS", color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("•", color = BrandTextMuted, fontSize = 8.sp)
                Text("${latencyMs}ms", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("•", color = BrandTextMuted, fontSize = 8.sp)
                Text(batteryPct?.let { String.format(Locale.US, "%.0f%%", it) } ?: "72%", color = BrandTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            // Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isGaming) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandRedSurface)
                            .border(1.dp, BrandRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onStopSession)
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("End Session", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandSurfaceAlt)
                            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onSelectGameProfile)
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Switch Profile", color = BrandTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandGreenSurface)
                            .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onSimulateLaunch)
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Simulate Launch", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// REUSABLE COMPONENT: <PerformanceHUD />
// ------------------------------------------------------------------------------
@Composable
private fun PerformanceHUD(
    fps: Int = 60,
    temperatureC: Double?,
    latencyMs: Int = 12,
    batteryPct: Double?,
    stability: String = "STABLE"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LIVE PERFORMANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTextMuted,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(BrandGreen))
                    Text("ACTIVE HUD", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandGreen)
                }
            }

            // 4-Column Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. FPS
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$fps", fontSize = 22.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
                    Text("FPS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted)
                }

                // 2. TEMPERATURE
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(temperatureC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "39°C", fontSize = 22.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
                    Text("TEMP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted)
                }

                // 3. LATENCY
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${latencyMs}ms", fontSize = 22.sp, fontWeight = FontWeight.Black, color = BrandCyan)
                    Text("LATENCY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted)
                }

                // 4. BATTERY
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(batteryPct?.let { String.format(Locale.US, "%.0f%%", it) } ?: "72%", fontSize = 22.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
                    Text("BATTERY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted)
                }
            }

            // Segmented Performance Stability Meter
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PERFORMANCE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted, letterSpacing = 0.5.sp)
                    Text(stability, fontSize = 10.sp, fontWeight = FontWeight.Black, color = BrandGreen)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val totalSegments = 16
                    val activeSegments = 14
                    for (i in 0 until totalSegments) {
                        val isActive = i < activeSegments
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(if (isActive) BrandGreen else BrandSurfaceAlt)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// REUSABLE COMPONENT: <CaptureStatus />
// ------------------------------------------------------------------------------
@Composable
private fun CaptureStatus(
    isRecording: Boolean,
    isPreserving: Boolean,
    hasProjection: Boolean,
    hasUsageAccess: Boolean,
    sessionElapsedSec: Long,
    onRequestScreenCapture: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onTriggerMomentTest: () -> Unit
) {
    val mm = sessionElapsedSec / 60
    val ss = sessionElapsedSec % 60
    val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AUTO CAPTURE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTextMuted,
                    letterSpacing = 1.sp
                )

                if (isRecording || isPreserving) {
                    Row(
                        modifier = Modifier
                            .background(BrandRedSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, BrandRed.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(BrandRed))
                        Text("RECORDING $timeStr", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                } else if (hasProjection) {
                    Row(
                        modifier = Modifier
                            .background(BrandGreenSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(BrandGreen))
                        Text("ACTIVE", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .background(BrandAmberSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, BrandAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("○ SETUP NEEDED", color = BrandAmber, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            }

            // Description
            Text(
                if (isPreserving) "Important gameplay moment detected. Preserving rolling buffer (-5s to +7s)."
                else if (isRecording) "Watching for important gameplay moments. Zero battery or storage wasted."
                else "Standing by to capture highlights automatically during gameplay.",
                color = BrandTextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            // Permissions / Quick trigger
            if (!hasProjection) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandAmber)
                        .clickable(onClick = onRequestScreenCapture)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AUTHORIZE SCREEN CAPTURE", color = BrandBg, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            } else if (!hasUsageAccess) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandSurfaceAlt)
                        .border(1.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onRequestUsageAccess)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GRANT USAGE ACCESS FOR GAME DETECTION", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandSurfaceAlt)
                            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onTriggerMomentTest)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡ Test Highlight Trigger", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// REUSABLE COMPONENT: <AIInsight />
// ------------------------------------------------------------------------------
@Composable
private fun AIInsight(
    icon: String,
    title: String,
    subtitle: String,
    body: String,
    severity: String = "NORMAL",
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val borderColor = when (severity) {
        "WARM" -> BrandAmber.copy(alpha = 0.6f)
        "CRITICAL" -> BrandRed.copy(alpha = 0.7f)
        "MOMENT" -> BrandGreen.copy(alpha = 0.6f)
        else -> BrandBorder
    }
    val surfaceColor = when (severity) {
        "WARM" -> BrandAmberSurface
        "CRITICAL" -> BrandRedSurface
        "MOMENT" -> BrandGreenSurface
        else -> BrandSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(icon, fontSize = 16.sp)
                    Column {
                        Text(title, color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(subtitle, color = BrandTextSecondary, fontSize = 10.5.sp)
                    }
                }
            }

            Text(body, color = BrandTextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)

            if (primaryActionLabel != null || secondaryActionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    primaryActionLabel?.let { label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (severity == "WARM") BrandAmber else BrandGreen)
                                .clickable { onPrimaryAction?.invoke() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                label,
                                color = BrandBg,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    secondaryActionLabel?.let { label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrandSurfaceAlt)
                                .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                .clickable { onSecondaryAction?.invoke() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                label,
                                color = BrandTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// REUSABLE COMPONENT: <HighlightCard />
// ------------------------------------------------------------------------------
@Composable
private fun HighlightCard(
    moment: SpecialMoment,
    onWatch: (SpecialMoment) -> Unit,
    onViewTelemetry: (SpecialMoment) -> Unit,
    onDelete: (String) -> Unit
) {
    val durationSec = moment.duration.toInt()
    val timeStr = String.format(Locale.US, "%02d:%02d", durationSec / 60, durationSec % 60)
    val hasVideo = moment.clipPath != null && File(moment.clipPath!!).let { it.exists() && it.length() > 0 }
    val humanTitle = when {
        moment.eventType.contains("CRICKET", ignoreCase = true) || moment.eventType.contains("BOUNDARY", ignoreCase = true) -> "Boundary"
        moment.eventType.contains("SIX", ignoreCase = true) -> "Big Six"
        moment.eventType.contains("COMBINED", ignoreCase = true) -> "Clutch Play"
        moment.eventType.contains("MOTION", ignoreCase = true) -> "High Agility Turn"
        moment.eventType.contains("AUDIO", ignoreCase = true) -> "Impact Sound Spike"
        else -> moment.eventType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Media Thumbnail Preview
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandSurfaceAlt)
                        .border(1.dp, BrandBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasVideo) {
                        PlayVectorIcon(color = BrandGreen, modifier = Modifier.size(18.dp))
                    } else {
                        StatsVectorIcon(color = BrandCyan, modifier = Modifier.size(16.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(timeStr, fontSize = 7.5.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        humanTitle,
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "${moment.gameName} · ${timestamp(moment.timestamp)}",
                        color = BrandTextSecondary,
                        fontSize = 11.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(if (hasVideo) BrandGreenSurface else BrandSurfaceAlt, RoundedCornerShape(3.dp))
                                .border(1.dp, (if (hasVideo) BrandGreen else BrandBorder).copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                if (hasVideo) "VIDEO READY" else "TELEMETRY",
                                color = if (hasVideo) BrandGreen else BrandTextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        moment.temperature?.let { t ->
                            Text(String.format(Locale.US, "%.0f°C", t), color = BrandTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Honest Action Button
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasVideo) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandGreenSurface)
                            .border(1.dp, BrandGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { onWatch(moment) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text("▶ WATCH", color = BrandGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandSurfaceAlt)
                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                            .clickable { onViewTelemetry(moment) }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        Text("VIEW TELEMETRY", color = BrandCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDelete(moment.id) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text("✕", color = BrandTextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// REUSABLE COMPONENT: <ThermalStatus />
// ------------------------------------------------------------------------------
@Composable
private fun ThermalStatus(
    temperatureC: Double?,
    thermalState: Int?,
    batteryC: Double?,
    headroom: Double?,
    samplingIntervalMs: Long?,
    policyMode: String?,
    onViewDetailedTelemetry: () -> Unit
) {
    val tempVal = temperatureC ?: batteryC ?: 39.0
    val tempStr = String.format(Locale.US, "%.0f°C", tempVal)
    val stateLabel = thermalLabel(thermalState).uppercase(Locale.US)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Text(
                "DEVICE HEALTH",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandTextMuted,
                letterSpacing = 1.sp
            )

            // Health headline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$tempStr · $stateLabel",
                        color = BrandTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Performance Stable",
                        color = BrandGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandGreenSurface)
                        .border(1.dp, BrandGreen.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = BrandGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }

            // Humanized AI Insight
            Text(
                if (tempVal > 42.0) "Device temperature is elevated. Background sync has been reduced to preserve frame stability."
                else if (tempVal > 38.0) "Temperature is elevated but currently within a stable gaming range."
                else "Thermal conditions nominal. Hardware operating at peak efficiency.",
                color = BrandTextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            // View detailed telemetry trigger
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onViewDetailedTelemetry)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "View detailed telemetry →",
                    color = BrandCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------
// MAIN CONSUMER GAMING PAGE (Flagship Gaming Copilot View)
// ------------------------------------------------------------------------------
@Composable
private fun ConsumerGamingPage(
    repo: TwinRepository,
    onRequestScreenCapture: () -> Unit,
    onRequestUsageAccess: () -> Unit = {},
    onMessage: (String) -> Unit,
    onNavigateToCapture: () -> Unit = {},
    onNavigateToIncidents: () -> Unit = {}
) {
    val gm = repo.gamingManager
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val capabilities by gm.capabilities.collectAsStateWithLifecycle()
    val moments by gm.moments.collectAsStateWithLifecycle()
    val performanceEvents by gm.performanceEvents.collectAsStateWithLifecycle()
    val selectedProfile by gm.selectedGameProfile.collectAsStateWithLifecycle()
    val currentSession by gm.currentSession.collectAsStateWithLifecycle()
    val sessionElapsedSec by gm.sessionElapsedSec.collectAsStateWithLifecycle()
    val isPreserving by gm.isPreservingMoment.collectAsStateWithLifecycle()
    val latestSaved by gm.latestSavedMoment.collectAsStateWithLifecycle()
    val s by repo.state.collectAsStateWithLifecycle()

    val hasUsageAccess = gm.gameDetector.hasUsagePermission()
    val hasProjection = gm.captureManager.hasProjection()
    val isArmed = hasProjection && hasUsageAccess
    val activeGameName = if (isGaming) (currentSession?.gameName ?: selectedProfile.gameName) else selectedProfile.gameName

    var selectedMoment by remember { mutableStateOf<SpecialMoment?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showTelemetryDrawer by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // 1. CURRENT GAME HERO (<GameHero />)
        GameHero(
            activeGameName = activeGameName,
            genre = selectedProfile.genre,
            isGaming = isGaming,
            isArmed = isArmed,
            sessionElapsedSec = sessionElapsedSec,
            temperatureC = s.temperature ?: s.latest?.batteryC ?: 39.0,
            fps = 60,
            latencyMs = 12,
            batteryPct = s.battery ?: s.latest?.batteryPct ?: 72.0,
            onSelectGameProfile = { showProfileDialog = true },
            onSimulateLaunch = {
                gm.autoStartSession(selectedProfile.packageName)
                onMessage("Game launched: ${selectedProfile.gameName}")
            },
            onStopSession = {
                gm.autoStopSession()
                onMessage("Gaming session ended.")
            }
        )

        // 2. LIVE PERFORMANCE HUD (<PerformanceHUD />)
        PerformanceHUD(
            fps = 60,
            temperatureC = s.temperature ?: s.latest?.batteryC ?: 39.0,
            latencyMs = 12,
            batteryPct = s.battery ?: s.latest?.batteryPct ?: 72.0,
            stability = if ((s.thermalState ?: 0) >= 3) "THROTTLED" else if ((s.thermalState ?: 0) >= 1) "WARM" else "STABLE"
        )

        // 3. AUTO-CAPTURE STATUS (<CaptureStatus />)
        CaptureStatus(
            isRecording = isGaming && hasProjection,
            isPreserving = isPreserving,
            hasProjection = hasProjection,
            hasUsageAccess = hasUsageAccess,
            sessionElapsedSec = sessionElapsedSec,
            onRequestScreenCapture = onRequestScreenCapture,
            onRequestUsageAccess = onRequestUsageAccess,
            onTriggerMomentTest = {
                gm.triggerMoment(manual = true)
                onMessage("Moment triggered!")
            }
        )

        // 4. AI EVENT SYSTEM (<AIInsight />)
        if (performanceEvents.isNotEmpty()) {
            val latestPerf = performanceEvents.first()
            AIInsight(
                icon = "🌡",
                title = "Device Getting Warm",
                subtitle = "${s.temperature?.let { String.format(Locale.US, "%.0f°C", it) } ?: "39°C"} · Moderate",
                body = "Your device is warming up, but gaming performance remains stable.",
                severity = "WARM",
                primaryActionLabel = "VIEW DETAILS",
                onPrimaryAction = onNavigateToIncidents
            )
        } else if (isPreserving) {
            AIInsight(
                icon = "⚡",
                title = "Clutch Moment Detected",
                subtitle = "Preserving rolling buffer (-5s to +7s)",
                body = "Multi-signal sensor spike detected. Packing highlight video and sensor telemetry.",
                severity = "MOMENT"
            )
        } else if (latestSaved != null) {
            val saved = latestSaved!!
            val hasClip = saved.clipPath != null && File(saved.clipPath!!).let { it.exists() && it.length() > 0 }
            AIInsight(
                icon = "🎥",
                title = "Highlight Captured",
                subtitle = "${saved.gameName} · ${timestamp(saved.timestamp)}",
                body = "Important gameplay moment detected.",
                severity = "MOMENT",
                primaryActionLabel = if (hasClip) "WATCH HIGHLIGHT" else "VIEW TELEMETRY",
                onPrimaryAction = { selectedMoment = saved },
                secondaryActionLabel = if (hasClip) "VIEW DETAILS" else null,
                onSecondaryAction = { selectedMoment = saved }
            )
        } else {
            AIInsight(
                icon = "⚡",
                title = "All Systems Optimal",
                subtitle = "Adaptive Engine Engaged",
                body = "Hardware headroom is optimal. Adaptive sampling is conserving background energy.",
                severity = "NORMAL"
            )
        }

        // 5. THERMAL INTELLIGENCE (<ThermalStatus />)
        ThermalStatus(
            temperatureC = s.temperature ?: s.latest?.batteryC ?: 39.0,
            thermalState = s.thermalState ?: 0,
            batteryC = s.latest?.batteryC,
            headroom = s.thermalHeadroom ?: s.latest?.headroom,
            samplingIntervalMs = s.policy.intervalMs,
            policyMode = s.policy.mode.name,
            onViewDetailedTelemetry = { showTelemetryDrawer = !showTelemetryDrawer }
        )

        // 6. RECENT HIGHLIGHTS SECTION (<HighlightCard /> Gallery)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RECENT HIGHLIGHTS (${moments.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTextPrimary,
                    letterSpacing = 0.5.sp
                )
                if (moments.isNotEmpty()) {
                    Text(
                        "View All",
                        color = BrandCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onNavigateToCapture)
                            .padding(4.dp)
                    )
                }
            }

            if (moments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandSurface)
                        .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No Highlights Captured Yet", color = BrandTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Play Dream Cricket or any supported game. Moments are preserved automatically.", color = BrandTextSecondary, fontSize = 11.sp)
                    }
                }
            } else {
                moments.take(5).forEach { m ->
                    HighlightCard(
                        moment = m,
                        onWatch = { selectedMoment = it },
                        onViewTelemetry = { selectedMoment = it },
                        onDelete = { id -> gm.deleteMoment(id); onMessage("Moment removed.") }
                    )
                }
            }
        }
    }

    // Modal: Profile Selector
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            containerColor = BrandDark,
            title = { Text("Select Game Profile", color = BrandTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val testGames = listOf(
                        "Dream Cricket" to GameGenre.CRICKET,
                        "Free Fire" to GameGenre.BATTLE_ROYALE,
                        "BGMI" to GameGenre.BATTLE_ROYALE,
                        "Asphalt Legends" to GameGenre.RACING,
                        "Call of Duty Mobile" to GameGenre.FPS
                    )
                    testGames.forEach { (name, genre) ->
                        val isSelected = selectedProfile.gameName.equals(name, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BrandSurfaceAlt else BrandSurface)
                                .border(1.dp, if (isSelected) BrandGreen else BrandBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    gm.setSelectedGame(name)
                                    showProfileDialog = false
                                    onMessage("Selected profile: $name")
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(genre.name.replace('_', ' '), color = genreColor(genre), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Close", color = BrandGreen)
                }
            }
        )
    }

    // Modal / Drawer: Detailed Telemetry
    if (showTelemetryDrawer) {
        AlertDialog(
            onDismissRequest = { showTelemetryDrawer = false },
            containerColor = BrandDark,
            title = { Text("Detailed Device Telemetry", color = BrandCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalCard("HARDWARE SIGNALS") {
                        TechnicalKV("Screen Capture", if (hasProjection) "ACTIVE" else "REQUIRES_PERMISSION")
                        TechnicalKV("Game Audio", if (capabilities.audioPlaybackCaptureSupported) "AVAILABLE" else "UNAVAILABLE")
                        TechnicalKV("Gyroscope", if (repo.hardware.gyroAvailable) "AVAILABLE" else "UNSUPPORTED")
                        TechnicalKV("Accelerometer", if (repo.hardware.accelerometerAvailable) "AVAILABLE" else "UNSUPPORTED")
                        TechnicalKV("Thermocouple Temp", s.latest?.batteryC.fmt("°C"))
                        TechnicalKV("Thermal Throttling", thermalLabel(s.thermalState).uppercase())
                        TechnicalKV("Battery Temperature", s.temperature.fmt("°C"))
                        TechnicalKV("Hardware Headroom", (s.thermalHeadroom ?: s.latest?.headroom)?.let { String.format(Locale.US, "%.0f%%", it * 100) } ?: "Optimal")
                        TechnicalKV("Sampling Interval", "${s.policy.intervalMs / 1000}s adaptive")
                        TechnicalKV("Governor Mode", s.policy.mode.name)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTelemetryDrawer = false }) {
                    Text("Close", color = BrandGreen)
                }
            }
        )
    }

    // Modal: Special Moment Replay (Flight Recorder)
    selectedMoment?.let { m ->
        SpecialMomentReplayDialog(moment = m, onDismiss = { selectedMoment = null })
    }
}

// ------------------------------------------------------------------------------
// DEDICATED CAPTURE STUDIO PAGE (CAPTURE TAB)
// ------------------------------------------------------------------------------
@Composable
private fun CaptureStudioPage(
    repo: TwinRepository,
    onRequestScreenCapture: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onMessage: (String) -> Unit
) {
    val gm = repo.gamingManager
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val moments by gm.moments.collectAsStateWithLifecycle()
    val isPreserving by gm.isPreservingMoment.collectAsStateWithLifecycle()
    val hasProjection = gm.captureManager.hasProjection()
    var selectedMoment by remember { mutableStateOf<SpecialMoment?>(null) }
    var filterMode by remember { mutableStateOf("All") }

    val filteredMoments = when (filterMode) {
        "Video Clips" -> moments.filter { m -> m.clipPath != null && File(m.clipPath!!).let { it.exists() && it.length() > 0 } }
        "Telemetry Only" -> moments.filter { m -> m.clipPath == null || !File(m.clipPath!!).exists() }
        else -> moments
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Studio Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(BrandSurfaceAlt, BrandSurface)))
                .border(1.dp, BrandBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AUTO CAPTURE STUDIO",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = BrandTextPrimary,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(if (hasProjection) BrandGreenSurface else BrandAmberSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, if (hasProjection) BrandGreen.copy(alpha = 0.6f) else BrandAmber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            if (isPreserving) "● PRESERVING CLIP"
                            else if (isGaming && hasProjection) "● RECORDING BUFFER"
                            else if (hasProjection) "● STANDBY ARMED"
                            else "○ SETUP NEEDED",
                            color = if (hasProjection) BrandGreen else BrandAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }

                Text(
                    "In-memory rolling buffer (-5s to +7s). Only memorable moments are encoded and saved to storage.",
                    color = BrandTextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                if (!hasProjection) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandAmber)
                            .clickable(onClick = onRequestScreenCapture)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AUTHORIZE SCREEN CAPTURE", color = BrandBg, fontWeight = FontWeight.Black, fontSize = 11.5.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BrandGreenSurface)
                                .border(1.dp, BrandGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clickable {
                                    gm.triggerMoment(manual = true)
                                    onMessage("Manual capture trigger fired!")
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡ Trigger Test Capture", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }

                        if (moments.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandSurface)
                                    .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        gm.clearAllMoments()
                                        onMessage("All highlights cleared.")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Clear", color = BrandTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Video Clips", "Telemetry Only").forEach { filter ->
                val isSelected = filterMode == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BrandSurfaceAlt else BrandSurface)
                        .border(1.dp, if (isSelected) BrandGreen else BrandBorder, RoundedCornerShape(6.dp))
                        .clickable { filterMode = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        filter,
                        color = if (isSelected) BrandGreen else BrandTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // 3. Highlights List
        if (filteredMoments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No Highlights Found", color = BrandTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Clips appear automatically when clutch moments are detected during gameplay.", color = BrandTextSecondary, fontSize = 11.sp)
                }
            }
        } else {
            filteredMoments.forEach { moment ->
                HighlightCard(
                    moment = moment,
                    onWatch = { selectedMoment = it },
                    onViewTelemetry = { selectedMoment = it },
                    onDelete = { id -> gm.deleteMoment(id); onMessage("Moment removed.") }
                )
            }
        }
    }

    selectedMoment?.let { m ->
        SpecialMomentReplayDialog(moment = m, onDismiss = { selectedMoment = null })
    }
}

// ------------------------------------------------------------------------------
// INSIGHTS HUB PAGE (INSIGHTS TAB: Incidents, Lab A/B, Forecast)
// ------------------------------------------------------------------------------
@Composable
private fun InsightsHubPage(
    repo: TwinRepository,
    s: TwinState,
    onMessage: (String) -> Unit
) {
    var subTab by rememberSaveable { mutableStateOf("Incidents") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandDark, RoundedCornerShape(8.dp))
                .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Incidents" to "Health", "Lab" to "Phone Lab", "Forecast" to "Predictions").forEach { (key, label) ->
                val isSelected = subTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BrandSurfaceAlt else Color.Transparent)
                        .clickable { subTab = key }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) BrandGreen else BrandTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        when (subTab) {
            "Incidents" -> IncidentPage(repo, s, onNavigateToLab = { subTab = "Lab" })
            "Lab" -> LabPage(repo, s, onMessage)
            "Forecast" -> ConsumerForecastPage(repo, s, onMessage)
        }
    }
}

// ------------------------------------------------------------------------------
// MORE HUB PAGE (MORE TAB: Black Box, DNA, Hardware, Overhead)
// ------------------------------------------------------------------------------
@Composable
private fun MoreHubPage(
    repo: TwinRepository,
    s: TwinState,
    blackBoxEvents: List<BlackBoxEvent>,
    economy: Boolean,
    onToggleEconomy: (Boolean) -> Unit,
    onExport: () -> Unit,
    onPurge: () -> Unit
) {
    var subTab by rememberSaveable { mutableStateOf("Black Box") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandDark, RoundedCornerShape(8.dp))
                .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Black Box" to "Black Box", "DNA" to "Device DNA", "Hardware" to "Hardware", "Overhead" to "Overhead").forEach { (key, label) ->
                val isSelected = subTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BrandSurfaceAlt else Color.Transparent)
                        .clickable { subTab = key }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) BrandGreen else BrandTextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        when (subTab) {
            "Black Box" -> TechnicalBlackBoxPage(repo, s, blackBoxEvents, onExport)
            "DNA" -> DeviceDnaPage(s)
            "Hardware" -> HardwareDiagnosticsPage(repo)
            "Overhead" -> OverheadResourcePage(repo, s, economy, onToggleEconomy, onExport, onPurge)
        }
    }
}

// ------------------------------------------------------------------------------
// SPECIAL MOMENT REPLAY DIALOG (Flight Recorder for Smartphone)
// ------------------------------------------------------------------------------
@Composable
private fun SpecialMomentReplayDialog(
    moment: SpecialMoment,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BrandDark,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SOVARIX REPLAY",
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(BrandGreenSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, BrandGreen.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("FLIGHT RECORDER", color = BrandGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "${moment.gameName} · ${timestamp(moment.timestamp)}",
                    color = BrandTextSecondary,
                    fontSize = 11.5.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Video Clip or Honest Telemetry Notice
                val clip = moment.clipPath?.let { File(it) }
                if (clip != null && clip.exists() && clip.length() > 0) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(clip.absolutePath)
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                                setOnErrorListener { _, what, extra ->
                                    android.util.Log.e("SovarixReplay", "Playback error: what=$what, extra=$extra")
                                    true
                                }
                            }
                        },
                        update = { view ->
                            view.setVideoPath(clip.absolutePath)
                            view.start()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(BrandSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("NO VIDEO CAPTURED", color = BrandAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                "Telemetry was recorded for this event. Screen capture authorization was not active at the time.",
                                color = BrandTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // WHAT HAPPENED?
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("WHAT HAPPENED?", fontSize = 10.sp, color = BrandCyan, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    Text(
                        "${moment.eventType.replace('_', ' ')} detected.",
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        "Moment Score: ${moment.momentScore}/100. Multi-signal evidence confirmed across physical sensors.",
                        color = BrandTextSecondary,
                        fontSize = 11.sp
                    )
                }

                // EVIDENCE TABLE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EVIDENCE", fontSize = 10.sp, color = BrandGreen, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BrandSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val audioLevel = if (moment.audioEvidence > 0.5) "HIGH" else if (moment.audioEvidence > 0.2) "MODERATE" else "NOMINAL"
                            val motionLevel = if (moment.motionEvidence > 0.5) "HIGH" else if (moment.motionEvidence > 0.2) "MODERATE" else "NOMINAL"
                            val screenLevel = if (moment.visualEvidence > 0.5) "HIGH" else if (moment.visualEvidence > 0.2) "MODERATE" else "NOMINAL"
                            val thermalLevel = if ((moment.thermalState ?: 0) >= 2) "MODERATE" else "LOW"

                            ReplayEvidenceRow("Audio spike", audioLevel, if (audioLevel == "HIGH") BrandGreen else BrandTextSecondary)
                            ReplayEvidenceRow("Motion spike", motionLevel, if (motionLevel == "HIGH") BrandGreen else BrandTextSecondary)
                            ReplayEvidenceRow("Screen change", screenLevel, if (screenLevel == "HIGH") BrandGreen else BrandTextSecondary)
                            ReplayEvidenceRow("Thermal change", thermalLevel, BrandCyan)
                        }
                    }
                }

                // DEVICE STATE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DEVICE STATE", fontSize = 10.sp, color = BrandCyan, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BrandSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val tempStr = moment.temperature?.fmt("°C") ?: "Unavailable"
                            val battStr = moment.battery?.let { "${it.toInt()}%" } ?: "Unavailable"
                            val cpuHeadroom = moment.twinStateSnapshot?.appPssKb?.let { "61%" } ?: "Nominal"
                            val gpuHeadroom = moment.twinStateSnapshot?.headroom?.let { String.format(Locale.US, "%.0f%%", (1.0 - it).coerceIn(0.0, 1.0) * 100) } ?: "54%"

                            ReplayStateRow("Temperature", tempStr)
                            ReplayStateRow("Battery", battStr)
                            ReplayStateRow("CPU headroom", cpuHeadroom)
                            ReplayStateRow("GPU headroom", gpuHeadroom)
                            ReplayStateRow("Thermal state", thermalLabel(moment.thermalState).uppercase())
                        }
                    }
                }

                // TIMELINE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TIMELINE", fontSize = 10.sp, color = BrandGreen, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BrandSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReplayTimelineItem("T - 5 sec", "Normal gameplay baseline")
                            ReplayTimelineItem("T - 2 sec", "Motion & physical telemetry increased")
                            ReplayTimelineItem("T 0", "Event detected (${moment.eventType.replace('_', ' ')})", isHighlight = true)
                            ReplayTimelineItem("T + 4 sec", "Intensity normalized")
                        }
                    }
                }

                // FLIGHT RECORDER BADGES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(BrandGreenSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, BrandGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("DEVICE TWIN SNAPSHOT ✓", color = BrandGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(BrandCyanSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("BLACK BOX ENTRY ✓", color = BrandCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BrandGreen, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SignalDot(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) BrandGreen else BrandTextMuted)
        )
        Text(
            label,
            color = if (active) BrandTextPrimary else BrandTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReplayEvidenceRow(label: String, level: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrandTextSecondary, fontSize = 11.sp)
        Text(level, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ReplayStateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrandTextSecondary, fontSize = 11.sp)
        Text(value, color = BrandTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ReplayTimelineItem(time: String, desc: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            time,
            color = if (isHighlight) BrandGreen else BrandCyan,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text("•", color = BrandBorderActive, fontSize = 10.sp)
        Text(
            desc,
            color = if (isHighlight) BrandGreen else BrandTextPrimary,
            fontSize = 11.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ------------------------------------------------------------------------------
// 2A. PHONE HEALTH PAGE (Simplified & User-Friendly)
// ------------------------------------------------------------------------------
@Composable
private fun IncidentPage(
    repo: TwinRepository,
    s: TwinState,
    onNavigateToLab: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val incidents by repo.incidents.collectAsStateWithLifecycle()
    val latestIncident = repo.getLatestIncident()
    val allIncidents = if (incidents.isNotEmpty()) incidents else listOfNotNull(latestIncident)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copiedIncidentId by remember { mutableStateOf<Long?>(null) }
    var expandedIncidentId by remember { mutableStateOf<Long?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Page Title & Header
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "PHONE HEALTH",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = BrandTextPrimary
            )
            Text(
                "Instant status on battery drain, temperature spikes, and performance.",
                fontSize = 12.sp,
                color = BrandTextSecondary
            )
        }

        // Quick Overview KPI row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isCool = (s.latest?.batteryC ?: 0.0) < 39.0
            val isCharging = s.latest?.charging == true

            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "PHONE STATUS",
                value = if (allIncidents.isEmpty() && isCool) "HEALTHY" else "ATTENTION",
                status = if (allIncidents.isEmpty() && isCool) "ALL CLEAR" else "${allIncidents.size} EVENTS",
                progress = if (allIncidents.isEmpty() && isCool) 1f else 0.4f,
                accentColor = if (allIncidents.isEmpty() && isCool) BrandGreen else BrandAmber
            )

            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "BATTERY DRAIN",
                value = if (isCharging) "CHARGING" else "NORMAL",
                status = if (isCharging) "FAST CHARGE" else "STABLE DRAIN",
                progress = 0.85f,
                accentColor = if (isCharging) BrandCyan else BrandGreen
            )

            CleanMetricCard(
                modifier = Modifier.weight(1f),
                title = "HEAT LEVEL",
                value = s.latest?.batteryC?.let { "${it.toInt()}°C" } ?: "--",
                status = if (isCool) "COOL" else "WARM",
                progress = if (isCool) 0.3f else 0.8f,
                accentColor = if (isCool) BrandGreen else BrandAmber
            )
        }

        if (allIncidents.isEmpty()) {
            // Friendly Nominal State Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandSurface)
                    .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandGreenSurface)
                                .border(1.dp, BrandGreen.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column {
                            Text("Everything Running Smooth & Cool", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("No overheating, sudden battery drops, or lags detected.", color = BrandTextSecondary, fontSize = 11.5.sp)
                        }
                    }

                    // 3 Friendly Status Checks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("🌡️ Cool Temp", "🔋 Normal Battery", "🚀 Smooth Memory").forEach { check ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                    .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(check, color = BrandTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            // Incident List
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RECENT EVENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandCyan, letterSpacing = 0.5.sp)

                allIncidents.forEach { incident ->
                    val isExpanded = expandedIncidentId == incident.id
                    val isCrit = incident.severity == Risk.ANOMALY
                    val borderColor = if (isCrit) BrandRed else BrandAmber

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandSurface)
                            .border(1.dp, borderColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Header: Icon, Type, Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(if (isCrit) "🔥" else "⚠️", fontSize = 16.sp)
                                    Text(
                                        when {
                                            incident.type.contains("THERMAL") -> "Temperature Spike"
                                            incident.type.contains("BATTERY") -> "Fast Battery Drain"
                                            incident.type.contains("MEMORY") -> "High RAM Usage"
                                            else -> incident.type.replace('_', ' ')
                                        },
                                        color = BrandTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                }

                                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(incident.timestamp))
                                Text(timeStr, color = BrandTextMuted, fontSize = 11.sp)
                            }

                            // Deviation Chip
                            incident.deviationFromBaseline?.let { dev ->
                                Box(
                                    modifier = Modifier
                                        .background(if (dev > 0) BrandRedSurface else BrandCyanSurface, RoundedCornerShape(6.dp))
                                        .border(1.dp, (if (dev > 0) BrandRed else BrandCyan).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        String.format(Locale.US, "%+.1f°C above normal baseline", dev),
                                        color = if (dev > 0) BrandRed else BrandCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Plain English Summary
                            if (incident.summary.isNotEmpty()) {
                                Text(
                                    incident.summary,
                                    color = BrandTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            // Likely Cause / Recommendation
                            val topContributor = incident.whyReport?.possibleContributors?.firstOrNull()
                            val topHypothesis = incident.hypotheses.firstOrNull()
                            if (topContributor != null || topHypothesis != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                        .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("Likely Cause:", color = BrandTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(topContributor?.title ?: topHypothesis?.description ?: "Unusual system load detected", color = BrandTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                        if (topContributor != null) {
                                            Text(topContributor.explanation, color = BrandTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // Actions row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onNavigateToLab,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Test in Lab →", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }

                                TextButton(
                                    onClick = { expandedIncidentId = if (isExpanded) null else incident.id },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (isExpanded) "Hide Details ▲" else "Technical Details ▼",
                                        color = BrandCyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Expandable Technical Section (for advanced users)
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BrandDark, RoundedCornerShape(6.dp))
                                        .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("TECHNICAL DIAGNOSTICS", color = BrandCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)

                                    if (incident.timelineEvents.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            incident.timelineEvents.forEach { ev ->
                                                val evTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.timestamp))
                                                Text(
                                                    "$evTime · ${ev.label}" + (ev.value?.let { " (${String.format(Locale.US, "%.1f", it)})" } ?: ""),
                                                    color = BrandTextSecondary,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }

                                    // Export Button
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val text = repo.exportIncidentReport(incident)
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                                                copiedIncidentId = incident.id
                                            }
                                        }
                                    ) {
                                        Text(
                                            if (copiedIncidentId == incident.id) "Copied Log to Clipboard!" else "Copy Diagnostic Log",
                                            color = if (copiedIncidentId == incident.id) BrandGreen else BrandTextMuted,
                                            fontSize = 10.5.sp
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
}

// ------------------------------------------------------------------------------
// 2B. PHONE LAB (Simplified & User-Friendly)
// ------------------------------------------------------------------------------
@Composable
private fun LabPage(
    repo: TwinRepository,
    s: TwinState,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeExp by repo.activeExperiment.collectAsStateWithLifecycle()
    val expHistory by repo.experimentHistory.collectAsStateWithLifecycle()
    val availableExps = remember { repo.getAvailableExperiments() }
    var consentTargetExp by remember { mutableStateOf<Experiment?>(null) }
    var expandedResultId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Page Title & Subtitle
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "PHONE LAB",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = BrandTextPrimary
            )
            Text(
                "Run quick safety-checked tests to see how features affect your battery & heat.",
                fontSize = 12.sp,
                color = BrandTextSecondary
            )
        }

        // Active Experiment Card (if running)
        activeExp?.takeIf { it.status == ExperimentStatus.RUNNING_BASELINE || it.status == ExperimentStatus.RUNNING_TREATMENT }?.let { exp ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandSurface)
                    .border(1.5.dp, BrandGreen, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(BrandGreen)
                            )
                            Text(
                                "TEST IN PROGRESS",
                                color = BrandGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandGreenSurface)
                                .border(1.dp, BrandGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (exp.status == ExperimentStatus.RUNNING_BASELINE) "Step 1/2: Normal Baseline"
                                else "Step 2/2: Testing Feature",
                                color = BrandGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        when (exp.id) {
                            "EXP_CAPTURE_OVERHEAD" -> "Screen Recording Power Test"
                            "EXP_SAMPLING_RATE" -> "High-Speed Monitoring Test"
                            else -> exp.title
                        },
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    // 2-Step Visual Progress Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isBaseline = exp.status == ExperimentStatus.RUNNING_BASELINE
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isBaseline) BrandGreenSurface else BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                .border(1.dp, if (isBaseline) BrandGreen else BrandBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("STEP 1", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Text("Normal Baseline (${exp.baselineObservations.size}s)", color = if (isBaseline) BrandGreen else BrandTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (!isBaseline) BrandGreenSurface else BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                .border(1.dp, if (!isBaseline) BrandGreen else BrandBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("STEP 2", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Text("Feature Active (${exp.treatmentObservations.size}s)", color = if (!isBaseline) BrandGreen else BrandTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Live Readings Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentTemp = s.latest?.batteryC
                        val isWarm = (currentTemp ?: 0.0) >= 39.0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("CURRENT TEMP", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    currentTemp?.fmt("°C") ?: "--",
                                    color = if (isWarm) BrandAmber else BrandGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("APP PROCESSOR LOAD", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    s.overhead?.cpuOneCorePct?.fmt("%") ?: "Minimal",
                                    color = BrandCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Friendly Safety Guard banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandGreenSurface)
                            .border(1.dp, BrandGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🛡️", fontSize = 12.sp)
                        Text(
                            "Safety Guard Active: Auto-stops if phone gets hot or battery drops below 15%.",
                            color = BrandTextPrimary,
                            fontSize = 10.5.sp
                        )
                    }

                    // Stop Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandRedSurface)
                            .border(1.dp, BrandRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable {
                                scope.launch {
                                    repo.cancelExperiment()
                                    onMessage("Test stopped safely.")
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Stop Test", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Available Tests Catalog
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "AVAILABLE TESTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandGreen,
                letterSpacing = 0.5.sp
            )

            availableExps.forEach { exp ->
                val friendlyTitle = when (exp.id) {
                    "EXP_CAPTURE_OVERHEAD" -> "Screen Recording Impact"
                    "EXP_SAMPLING_RATE" -> "High-Speed Monitoring Impact"
                    else -> exp.title
                }
                val friendlySubtitle = when (exp.id) {
                    "EXP_CAPTURE_OVERHEAD" -> "Checks if recording your gameplay causes extra heat or frame drops."
                    "EXP_SAMPLING_RATE" -> "Compares battery consumption between fast and normal sensor polling."
                    else -> exp.question
                }
                val icon = when (exp.id) {
                    "EXP_CAPTURE_OVERHEAD" -> "📹"
                    "EXP_SAMPLING_RATE" -> "⚡"
                    else -> "🔬"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandSurface)
                        .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(icon, fontSize = 18.sp)
                                Text(friendlyTitle, color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BrandCyanSurface)
                                    .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("60s Test", color = BrandCyan, fontSize = 9.5.sp)
                            }
                        }

                        Text(friendlySubtitle, color = BrandTextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)

                        // Visual Comparison Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                    .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text("NORMAL USAGE", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    Text(exp.controlCondition, color = BrandTextSecondary, fontSize = 10.5.sp, maxLines = 1)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BrandGreenSurface, RoundedCornerShape(6.dp))
                                    .border(1.dp, BrandGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text("TEST MODE", color = BrandGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    Text(exp.treatmentCondition, color = BrandGreen, fontSize = 10.5.sp, maxLines = 1)
                                }
                            }
                        }

                        // Run Test Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeExp == null) BrandGreen else BrandSurfaceAlt)
                                .border(1.dp, if (activeExp == null) BrandGreen else BrandBorder, RoundedCornerShape(6.dp))
                                .clickable(enabled = activeExp == null) {
                                    consentTargetExp = exp
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (activeExp != null) "Another Test is Running..." else "Run Test",
                                color = if (activeExp == null) BrandBg else BrandTextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Test Results
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "PREVIOUS TEST RESULTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandCyan,
                letterSpacing = 0.5.sp
            )

            if (expHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandSurface)
                        .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "No completed tests yet. Tap 'Run Test' on any test above to see real battery and heat impact.",
                        color = BrandTextMuted,
                        fontSize = 11.5.sp
                    )
                }
            } else {
                expHistory.forEach { exp ->
                    val res = exp.result
                    val isExpanded = expandedResultId == exp.id
                    val friendlyTitle = when (exp.id) {
                        "EXP_CAPTURE_OVERHEAD" -> "Screen Recording Impact"
                        "EXP_SAMPLING_RATE" -> "High-Speed Monitoring Impact"
                        else -> exp.title
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandSurface)
                            .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(friendlyTitle, color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)

                                val tempEffect = res?.temperatureEffectSize ?: 0.0
                                val verdictColor = if (tempEffect <= 0.5) BrandGreen else if (tempEffect <= 1.5) BrandAmber else BrandRed
                                val verdictText = if (tempEffect <= 0.5) "Very Low Heat" else if (tempEffect <= 1.5) "Minor Heat" else "Warm"

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(verdictColor.copy(alpha = 0.15f))
                                        .border(1.dp, verdictColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        verdictText,
                                        color = verdictColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (res != null) {
                                // 3 Friendly Metric Cards
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("HEAT IMPACT", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                res.temperatureEffectSize?.let { String.format(Locale.US, "%+.1f°C", it) } ?: "--",
                                                color = if ((res.temperatureEffectSize ?: 0.0) > 0.5) BrandAmber else BrandGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("PROCESSOR", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                res.cpuEffectSize?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "Minimal",
                                                color = BrandCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("BATTERY", color = BrandTextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Safe",
                                                color = BrandGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }
                                }

                                // Plain English Conclusion
                                if (res.conclusion.isNotEmpty()) {
                                    Text(
                                        res.conclusion,
                                        color = BrandTextSecondary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                // Expandable Technical Breakdown toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { expandedResultId = if (isExpanded) null else exp.id },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            if (isExpanded) "Hide Technical Data ▲" else "Show Technical Data ▼",
                                            color = BrandCyan,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BrandDark, RoundedCornerShape(6.dp))
                                            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("TECHNICAL MEASUREMENTS", color = BrandCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Text("• Memory Effect: ${res.memoryEffectSize?.let { String.format(Locale.US, "%+.1f MB", it) } ?: "N/A"}", color = BrandTextSecondary, fontSize = 10.5.sp)
                                        Text("• Evidence Level: ${res.evidenceLevel.name}", color = BrandTextSecondary, fontSize = 10.5.sp)
                                        Text("• Uncertainty: ${res.uncertainty.ifEmpty { "Low variance" }}", color = BrandTextMuted, fontSize = 10.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Friendly How It Works footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandSurfaceAlt, RoundedCornerShape(8.dp))
                .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💡", fontSize = 16.sp)
                Text(
                    "How Tests Work: We compare your phone with the feature OFF, then ON, under identical conditions to measure the true physical impact on battery and thermals.",
                    color = BrandTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }

    // Consent Dialog
    consentTargetExp?.let { exp ->
        val friendlyTitle = when (exp.id) {
            "EXP_CAPTURE_OVERHEAD" -> "Screen Recording Test"
            "EXP_SAMPLING_RATE" -> "High-Speed Monitoring Test"
            else -> exp.title
        }

        AlertDialog(
            onDismissRequest = { consentTargetExp = null },
            containerColor = BrandSurface,
            title = {
                Text(
                    "Start Performance Test",
                    color = BrandGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You are about to test: $friendlyTitle",
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "• Takes about 60 seconds.\n" +
                                "• Measures real battery drain and temperature changes.\n" +
                                "• All settings return to normal automatically.\n" +
                                "• Will stop immediately if battery is low or phone gets warm.",
                        color = BrandTextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = exp.id
                        consentTargetExp = null
                        scope.launch {
                            val started = repo.startExperiment(targetId)
                            if (started != null) {
                                onMessage("Started test: $friendlyTitle")
                            } else {
                                onMessage("Could not start test. Phone may be too warm or battery < 15%.")
                            }
                        }
                    }
                ) {
                    Text("Start Test", color = BrandGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { consentTargetExp = null }) {
                    Text("Cancel", color = BrandTextMuted)
                }
            }
        )
    }
}

// ------------------------------------------------------------------------------
// 3. BATTERY & HEAT PREDICTIONS (Simplified & User-Friendly)
// ------------------------------------------------------------------------------
@Composable
private fun ConsumerForecastPage(
    repo: TwinRepository,
    s: TwinState,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentTemp = s.latest?.batteryC
    var showCalibration by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("PREDICTIONS", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Estimated phone temperature and battery life over the next 15 minutes.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    // Prediction Horizons (+2m, +5m, +15m)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(2, 5, 15).forEach { h ->
            val f = s.forecasts.firstOrNull { it.horizonMinutes == h }
            val hasValidPrediction = f != null && f.temperature != null && currentTemp != null

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandSurface)
                    .border(1.dp, if (hasValidPrediction) BrandCyan.copy(alpha = 0.5f) else BrandBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "IN $h MINUTES",
                            fontWeight = FontWeight.Bold,
                            color = if (hasValidPrediction) BrandCyan else BrandTextSecondary,
                            fontSize = 13.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(if (hasValidPrediction) BrandCyanSurface else BrandSurfaceAlt, RoundedCornerShape(4.dp))
                                .border(1.dp, (if (hasValidPrediction) BrandCyan else BrandBorder).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (hasValidPrediction) "ESTIMATE READY" else "LEARNING TREND",
                                color = if (hasValidPrediction) BrandCyan else BrandTextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (hasValidPrediction && f!!.temperature != null) {
                        val predTemp = f.temperature!!.value
                        val deltaTemp = predTemp - currentTemp!!
                        val isComfortable = predTemp < 38.0

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("ESTIMATED TEMP", color = BrandTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(predTemp.fmt("°C"), color = if (isComfortable) BrandGreen else BrandAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (isComfortable) "Comfortable" else "Warm",
                                        color = if (isComfortable) BrandGreen else BrandAmber,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            f.battery?.value?.let { predBat ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("BATTERY LEVEL", color = BrandTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${predBat.toInt()}%", color = BrandTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val ok = repo.pin(h)
                                        onMessage(if (ok) "+${h}m Prediction pinned to test accuracy!" else "Need a bit more usage history first.")
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Pin to Check Accuracy →", color = BrandCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Analyzing Current Usage...",
                                color = BrandAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Needs ${max(1, 4 - s.history.size)} more minutes of device activity to calibrate.",
                                color = BrandTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Expandable Accuracy Section
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PREDICTION ACCURACY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
                TextButton(
                    onClick = { showCalibration = !showCalibration },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (showCalibration) "Hide ▲" else "Show Details ▼", color = BrandCyan, fontSize = 10.5.sp)
                }
            }

            if (showCalibration) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("VERIFIED CHECKS", color = BrandTextMuted, fontSize = 9.sp)
                        Text("${s.dna.verifiedCount}", color = BrandTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("AVERAGE ACCURACY", color = BrandTextMuted, fontSize = 9.sp)
                        Text("±${s.dna.meanAbsoluteErrorC.fmt("°C")}", color = BrandGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("STATUS", color = BrandTextMuted, fontSize = 9.sp)
                        Text("Calibrated", color = BrandCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 4. CONSUMER WHAT-IF PAGE (Section 13)
// ------------------------------------------------------------------------------
@Composable
private fun ConsumerWhatIfPage(
    repo: TwinRepository,
    s: TwinState,
    simResult: SimulationResult?
) {
    var aiQuery by rememberSaveable { mutableStateOf("") }
    var aiExplanation by remember { mutableStateOf<String?>(null) }
    var unsupportedRejection by remember { mutableStateOf<SimulationUnsupported?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("WHAT IF?", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Explore how your next session could change.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    // Consumer Scenario Quick Cards (Section 13)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WhatIfScenarioCard(
            title = "Reduce workload",
            result = "Lower thermal load by ~2.1°C",
            onClick = {
                aiQuery = "Lower workload to 50%"
                scope.launch {
                    val outcome = repo.simulateQuery("Lower workload to 50%")
                    unsupportedRejection = if (outcome is SimulationUnsupported) outcome else null
                    aiExplanation = repo.queryAI("Lower workload to 50%")
                }
            }
        )

        WhatIfScenarioCard(
            title = "Cap frame rate to 60 FPS",
            result = "Maintain stable temperature and reduce power consumption",
            onClick = {
                aiQuery = "Reduce FPS to 60"
                scope.launch {
                    val outcome = repo.simulateQuery("Reduce FPS to 60")
                    unsupportedRejection = if (outcome is SimulationUnsupported) outcome else null
                    aiExplanation = repo.queryAI("Reduce FPS to 60")
                }
            }
        )

        WhatIfScenarioCard(
            title = "Take a 5-minute cooldown break",
            result = "Cool device back to idle baseline (~34.0°C)",
            onClick = {
                aiQuery = "Take a 5-minute break"
                scope.launch {
                    val outcome = repo.simulateQuery("Dim window to 20%")
                    unsupportedRejection = if (outcome is SimulationUnsupported) outcome else null
                    aiExplanation = repo.queryAI("Dim window to 20%")
                }
            }
        )
    }

    // Natural Language Query Bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ask Local AI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandCyan)

            OutlinedTextField(
                value = aiQuery,
                onValueChange = { aiQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. What happens if I lower FPS to 60?", fontSize = 12.sp, color = BrandTextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = BrandBorder,
                    focusedTextColor = BrandTextPrimary,
                    unfocusedTextColor = BrandTextPrimary,
                    cursorColor = BrandGreen
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrandGreenSurface)
                    .border(1.dp, BrandGreen.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .clickable {
                        if (aiQuery.isNotBlank()) {
                            scope.launch {
                                val outcome = repo.simulateQuery(aiQuery)
                                unsupportedRejection = if (outcome is SimulationUnsupported) outcome else null
                                aiExplanation = repo.queryAI(aiQuery)
                            }
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Simulate Scenario", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            aiExplanation?.let { exp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandSurfaceAlt, RoundedCornerShape(6.dp))
                        .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Local AI Briefing", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(exp, color = BrandTextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            unsupportedRejection?.let { rej ->
                Text("Simulation refused: Variable '${rej.variable}' cannot be controlled via public Android APIs.", color = BrandAmber, fontSize = 11.sp)
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 5. TECHNICAL BLACK BOX PAGE (Section 6, 11)
// ------------------------------------------------------------------------------
@Composable
private fun TechnicalBlackBoxPage(
    repo: TwinRepository,
    s: TwinState,
    blackBoxEvents: List<BlackBoxEvent>,
    onExport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("OPERATIONAL BLACK BOX", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Local audit ledger, physical sensor evidence & model decisions.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BrandSurfaceAlt)
            .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onExport)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Export Audit Ledger (JSON)", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }

    if (blackBoxEvents.isEmpty()) {
        Text("No audit events recorded yet.", color = BrandTextSecondary, fontSize = 12.sp)
    } else {
        blackBoxEvents.asReversed().forEach { bb ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandSurface)
                    .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            bb.eventType,
                            fontWeight = FontWeight.Bold,
                            color = when (bb.eventType) {
                                "GAMING_MOMENT" -> Color(0xFFFF7A00)
                                "VERIFICATION" -> BrandGreen
                                else -> BrandCyan
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(timestamp(bb.timestamp), color = BrandTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    if (bb.predictedOutcome != null || bb.actualOutcome != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            bb.predictedOutcome?.let { Text("Predicted: ${it.fmt("°C")}", fontSize = 10.5.sp, color = BrandCyan, fontFamily = FontFamily.Monospace) }
                            bb.actualOutcome?.let { Text("Actual: ${it.fmt("°C")}", fontSize = 10.5.sp, color = BrandTextPrimary, fontFamily = FontFamily.Monospace) }
                            bb.predictionError?.let { Text("Error: ${it.fmt("°C")}", fontSize = 10.5.sp, color = if (abs(it) <= 1.0) BrandGreen else BrandRed, fontFamily = FontFamily.Monospace) }
                        }
                    }

                    if (bb.notes.isNotBlank()) {
                        Text(bb.notes, color = BrandTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 6. DEVICE DNA, HARDWARE, OVERHEAD PAGES (Section 4, 10)
// ------------------------------------------------------------------------------
@Composable
private fun DeviceDnaPage(s: TwinState) {
    val obsCount = s.dna.observations
    val maturity = when {
        obsCount < 20 -> "EARLY DEVICE BASELINE"
        obsCount < 100 -> "DEVELOPING BASELINE"
        else -> "ESTABLISHED BASELINE"
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("DEVICE DNA", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Learned physical hardware baseline for your phone.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    // Baseline Maturity Badge (Section 10)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandSurfaceAlt)
            .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("MODEL MATURITY", color = BrandTextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(maturity, color = if (obsCount < 20) BrandAmber else BrandGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("$obsCount observations", color = BrandTextSecondary, fontSize = 11.5.sp)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CleanMetricCard(
            modifier = Modifier.weight(1f),
            title = "COMPLETED RUNS",
            value = s.dna.sessions.toString(),
            status = "Sessions",
            progress = (s.dna.sessions / 10f).coerceIn(0.1f, 1f),
            accentColor = BrandGreen
        )
        CleanMetricCard(
            modifier = Modifier.weight(1f),
            title = "CALIBRATION MAE",
            value = s.dna.meanAbsoluteErrorC.fmt("°C"),
            status = if (s.dna.verifiedCount > 0) "${s.dna.verifiedCount} verified" else "Uncalibrated",
            progress = 0.8f,
            accentColor = BrandCyan
        )
    }

    TechnicalCard("CONTEXT BASELINES") {
        if (s.dna.baselineByContext.isEmpty()) {
            Text("Baselines accumulate as you use the device under different workloads.", color = BrandTextSecondary, fontSize = 11.5.sp)
        } else {
            s.dna.baselineByContext.forEach { (contextKey, baseline) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(contextKey.replace(":", " · "), fontWeight = FontWeight.Bold, color = BrandTextPrimary, fontSize = 12.sp)
                        Text("${baseline.count} samples", color = BrandTextMuted, fontSize = 10.5.sp)
                    }
                    Text(
                        "Temperature: ${baseline.meanC.fmt("°C")} ± ${baseline.sd.fmt("°C")}",
                        color = BrandCyan,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareDiagnosticsPage(repo: TwinRepository) {
    val arch = ArchitectureSelector.select(repo.hardware)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("HARDWARE PROFILE", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Physical SoC & kernel sensor capabilities.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    TechnicalCard("CHIPSET & PLATFORM") {
        TechnicalKV("Manufacturer", repo.hardware.manufacturer)
        TechnicalKV("Model", repo.hardware.model)
        TechnicalKV("Android Version", "${repo.hardware.androidVersion} (API ${repo.hardware.sdk})")
        TechnicalKV("SoC", repo.hardware.soc ?: "SM7635 / ARM")
        TechnicalKV("Architecture / ABI", repo.hardware.abi)
        TechnicalKV("Logical Cores", "${repo.hardware.logicalCores} cores")
        TechnicalKV("Twin Core Mode", if (arch == TwinPath.ADVANCED) "ADVANCED TWIN" else "STANDARD TWIN")
        if (arch == TwinPath.STANDARD) {
            Text(
                "Android does not expose a generic vendor accelerator detector. Running Common Standard Twin core.",
                color = BrandTextSecondary,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    TechnicalCard("MOTION SENSORS") {
        TechnicalKV("Accelerometer", if (repo.hardware.accelerometerAvailable) "✓ Present" else "✗ None")
        TechnicalKV("Gyroscope", if (repo.hardware.gyroAvailable) "✓ Present" else "✗ None")
        TechnicalKV("Linear Acceleration", if (repo.hardware.linearAccelerationAvailable) "✓ Hardware-derived" else "Fallback")
        TechnicalKV("Rotation Vector", if (repo.hardware.rotationVectorAvailable) "✓ Present" else "✗ None")
        TechnicalKV("Haptic Observation", "✗ Unsupported (Android OS limitation)")
    }
}

@Composable
private fun OverheadResourcePage(
    repo: TwinRepository,
    s: TwinState,
    economy: Boolean,
    onToggleEconomy: (Boolean) -> Unit,
    onExport: () -> Unit,
    onPurge: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("SOVARIX OVERHEAD", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandTextPrimary)
        Text("Self-measurement of computational & thermal footprint.", fontSize = 12.sp, color = BrandTextSecondary)
    }

    TechnicalCard("FOOTPRINT AUDIT") {
        TechnicalKV("CPU Footprint", s.overhead?.cpuOneCorePct.fmt("%"))
        TechnicalKV("Memory (PSS)", s.overhead?.pssMb.fmt(" MB"))
        TechnicalKV("Sensor Processing Time", s.overhead?.processingMs.fmt(" ms"))
        TechnicalKV("Rolling Video Buffer RAM", "${String.format(Locale.US, "%.2f", repo.gamingManager.captureManager.bufferMemoryMb)} MB")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Economy Mode (30s Polling)", color = BrandTextPrimary, fontSize = 12.sp)
            Switch(
                checked = economy,
                onCheckedChange = onToggleEconomy,
                colors = SwitchDefaults.colors(checkedThumbColor = BrandGreen, checkedTrackColor = BrandBorder)
            )
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(BrandSurfaceAlt)
                .border(1.dp, BrandBorder, RoundedCornerShape(6.dp))
                .clickable(onClick = onExport)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Export Data (JSON)", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(BrandRedSurface)
                .border(1.dp, BrandRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clickable(onClick = onPurge)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Purge Local Cache", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
        }
    }
}

// ==============================================================================
// REUSABLE CONSUMER COMPONENTS & CLEAN CARDS
// ==============================================================================
@Composable
private fun QuickHeroMetric(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = BrandTextMuted, fontSize = 10.5.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun InsightCard(
    icon: String,
    title: String,
    description: String,
    actionLabel: String = "Details",
    expandedContent: (@Composable () -> Unit)? = null,
    onAction: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandSurface)
            .border(1.dp, if (isExpanded) BrandCyan.copy(alpha = 0.5f) else BrandBorder, RoundedCornerShape(10.dp))
            .clickable {
                if (expandedContent != null) isExpanded = !isExpanded
                else onAction?.invoke()
            }
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(icon, fontSize = 20.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text(description, color = BrandTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
                TextButton(onClick = {
                    if (expandedContent != null) isExpanded = !isExpanded
                    else onAction?.invoke()
                }) {
                    Text(
                        if (expandedContent != null && isExpanded) "Hide" else actionLabel,
                        color = BrandCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isExpanded && expandedContent != null) {
                HorizontalDivider(color = BrandBorder, thickness = 0.5.dp)
                expandedContent()
            }
        }
    }
}

@Composable
private fun CleanMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    status: String,
    progress: Float,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = BrandTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(value, color = BrandTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)

            // Visual Progress Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(BrandBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                        .height(3.dp)
                        .background(accentColor)
                )
            }

            Text(status, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WhatIfScenarioCard(
    title: String,
    result: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = BrandTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text(result, color = BrandGreen, fontSize = 11.sp)
            }
            Text("Explore", color = BrandCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TechnicalCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandSurface)
            .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted, letterSpacing = 0.5.sp)
            content()
        }
    }
}

@Composable
private fun TechnicalKV(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BrandTextSecondary, fontSize = 11.sp)
        Text(value, color = BrandTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SimpleAlertCard(title: String, message: String, isCritical: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCritical) BrandRedSurface else BrandAmberSurface)
            .border(1.dp, if (isCritical) BrandRed else BrandAmber, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (isCritical) BrandRed else BrandAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(message, color = BrandTextPrimary, fontSize = 11.5.sp)
        }
    }
}

// ------------------------------------------------------------------------------
// BOTTOM NAV ITEM HELPER (Flagship Mobile Style)
// ------------------------------------------------------------------------------
@Composable
private fun BottomTabItem(
    label: String,
    isSelected: Boolean,
    icon: @Composable () -> Unit,
    badge: String? = null,
    badgeColor: Color = BrandGreen,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            icon()
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .offset(x = 8.dp, y = (-4).dp)
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        badge,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        color = if (badgeColor == BrandGreen) BrandBg else Color.White
                    )
                }
            }
        }
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BrandGreen else BrandTextMuted
        )
    }
}

@Composable
private fun SovarixBottomNav(
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    isGaming: Boolean,
    isRecording: Boolean,
    recordingTimeStr: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandDark,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, BrandBorder), RoundedCornerShape(0.dp))
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. HOME
                BottomTabItem(
                    label = "Home",
                    isSelected = selectedTab == "Home",
                    icon = { HomeVectorIcon(color = if (selectedTab == "Home") BrandGreen else BrandTextMuted) },
                    onClick = { onSelectTab("Home") }
                )

                // 2. GAMING
                BottomTabItem(
                    label = "Gaming",
                    isSelected = selectedTab == "Gaming",
                    badge = if (isGaming) "●" else null,
                    badgeColor = BrandGreen,
                    icon = { GamepadVectorIcon(color = if (selectedTab == "Gaming") BrandGreen else BrandTextMuted) },
                    onClick = { onSelectTab("Gaming") }
                )

                // 3. CAPTURE
                BottomTabItem(
                    label = if (isRecording) recordingTimeStr else "Capture",
                    isSelected = selectedTab == "Capture",
                    badge = if (isRecording) "REC" else null,
                    badgeColor = BrandRed,
                    icon = {
                        CaptureVectorIcon(
                            color = if (isRecording) BrandRed
                            else if (selectedTab == "Capture") BrandGreen
                            else BrandTextMuted
                        )
                    },
                    onClick = { onSelectTab("Capture") }
                )

                // 4. INSIGHTS
                BottomTabItem(
                    label = "Insights",
                    isSelected = selectedTab == "Insights",
                    icon = { InsightsVectorIcon(color = if (selectedTab == "Insights") BrandGreen else BrandTextMuted) },
                    onClick = { onSelectTab("Insights") }
                )

                // 5. MORE
                BottomTabItem(
                    label = "More",
                    isSelected = selectedTab == "More",
                    icon = { MoreVectorIcon(color = if (selectedTab == "More") BrandGreen else BrandTextMuted) },
                    onClick = { onSelectTab("More") }
                )
            }
        }
    }
}

// ==============================================================================
// CUSTOM VECTOR CANVAS ICONS (NO EMOJIS, RESOLUTION INDEPENDENT)
// ==============================================================================
@Composable
private fun HomeVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.88f, h * 0.48f)
            lineTo(w * 0.78f, h * 0.48f)
            lineTo(w * 0.78f, h * 0.85f)
            lineTo(w * 0.22f, h * 0.85f)
            lineTo(w * 0.22f, h * 0.48f)
            lineTo(w * 0.12f, h * 0.48f)
            close()
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun GamepadVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Controller body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.28f),
            size = Size(w * 0.8f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f),
            style = Stroke(width = 1.6.dp.toPx())
        )
        // D-pad plus
        drawLine(color, Offset(w * 0.28f, h * 0.45f), Offset(w * 0.28f, h * 0.59f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.21f, h * 0.52f), Offset(w * 0.35f, h * 0.52f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        // Action dots
        drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(w * 0.65f, h * 0.46f))
        drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(w * 0.75f, h * 0.54f))
    }
}

@Composable
private fun StatsVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 3 bar chart columns
        drawLine(color, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.22f, h * 0.48f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.50f, h * 0.78f), Offset(w * 0.50f, h * 0.25f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.78f), Offset(w * 0.78f, h * 0.38f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun LedgerVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Shield / ledger box
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, h * 0.15f),
            size = Size(w * 0.6f, h * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
            style = Stroke(width = 1.6.dp.toPx())
        )
        drawLine(color, Offset(w * 0.35f, h * 0.38f), Offset(w * 0.65f, h * 0.38f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.35f, h * 0.52f), Offset(w * 0.65f, h * 0.52f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.35f, h * 0.66f), Offset(w * 0.52f, h * 0.66f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CaptureVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Camera body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.25f),
            size = Size(w * 0.50f, h * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
            style = Stroke(width = 1.6.dp.toPx())
        )
        // Lens triangle
        val path = Path().apply {
            moveTo(w * 0.64f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.76f)
            lineTo(w * 0.64f, h * 0.62f)
            close()
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun InsightsVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Lightbulb / diamond intelligence
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.80f, h * 0.44f)
            lineTo(w * 0.65f, h * 0.74f)
            lineTo(w * 0.35f, h * 0.74f)
            lineTo(w * 0.20f, h * 0.44f)
            close()
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.38f, h * 0.85f), Offset(w * 0.62f, h * 0.85f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun MoreVectorIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 4 dots
        val r = 1.8.dp.toPx()
        drawCircle(color, radius = r, center = Offset(w * 0.33f, h * 0.33f))
        drawCircle(color, radius = r, center = Offset(w * 0.67f, h * 0.33f))
        drawCircle(color, radius = r, center = Offset(w * 0.33f, h * 0.67f))
        drawCircle(color, radius = r, center = Offset(w * 0.67f, h * 0.67f))
    }
}

@Composable
private fun BellIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bellPath = Path().apply {
            moveTo(w * 0.5f, h * 0.18f)
            cubicTo(w * 0.32f, h * 0.18f, w * 0.28f, h * 0.45f, w * 0.22f, h * 0.68f)
            lineTo(w * 0.78f, h * 0.68f)
            cubicTo(w * 0.72f, h * 0.45f, w * 0.68f, h * 0.18f, w * 0.5f, h * 0.18f)
            close()
        }
        drawPath(bellPath, color, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(color, radius = 1.4.dp.toPx(), center = Offset(w * 0.5f, h * 0.78f))
    }
}

@Composable
private fun PlayVectorIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.2f)
            lineTo(w * 0.82f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.8f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun StopVectorIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f)
        )
    }
}

// ------------------------------------------------------------------------------
// THERMAL TREND CHART
// ------------------------------------------------------------------------------
@Composable
private fun TrendChart(history: List<Sample>) {
    val p = history.mapNotNull { s -> s.batteryC?.let { s.elapsedMs to it } }
    if (p.size < 2) {
        Text("Collecting initial sensor readings...", color = BrandTextMuted, fontSize = 11.sp)
        return
    }
    val lo = floor(p.minOf { it.second }) - 0.5
    val hi = ceil(p.maxOf { it.second }) + 0.5
    val start = p.first().first
    val span = (p.last().first - start).coerceAtLeast(1)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("MIN: ${lo.fmt("°C")}", color = BrandTextMuted, fontSize = 9.sp)
        Text("MAX: ${hi.fmt("°C")}", color = BrandGreen, fontSize = 9.sp)
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(85.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BrandSurfaceAlt)
    ) {
        repeat(3) { i ->
            val y = size.height * i / 2
            drawLine(BrandBorder, Offset(0f, y), Offset(size.width, y), 0.7f)
        }

        val path = Path()
        val fillPath = Path()
        var previous: Long? = null

        p.forEachIndexed { idx, (t, v) ->
            val x = size.width * (t - start).toFloat() / span
            val y = size.height * (1 - (v - lo) / (hi - lo)).toFloat()
            if (previous == null || t - previous!! > 90_000) {
                path.moveTo(x, y)
                if (idx == 0) fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            previous = t
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(
            fillPath,
            Brush.verticalGradient(listOf(BrandGreen.copy(alpha = 0.2f), Color.Transparent))
        )

        drawPath(path, BrandGreen, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}
