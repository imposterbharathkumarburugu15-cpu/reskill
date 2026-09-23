package dev.sovarix.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.components.*
import dev.sovarix.app.ui.screens.*
import dev.sovarix.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * SOVARIX FLAGSHIP ANDROID SYSTEM APPLICATION
 * "YOUR PHONE KNOWS. NOW IT UNDERSTANDS."
 *
 * Premium dark-first experience:
 * - Fluid typography and whitespace
 * - Central Thermal Hero
 * - Polished SOVARIX Orb intelligence core
 * - 5 native vector navigation tabs: HOME · LAB · GAME · MEMORY · DEVICE
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SovarixScreen(
    repo: TwinRepository,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onRequestScreenCapture: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onExport: () -> Unit = {},
    onDim: () -> Unit = {},
    onSettings: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val gm = repo.gamingManager
    val isGaming by gm.isGamingActive.collectAsStateWithLifecycle()
    val voice = repo.voiceManager
    val isVoiceActive by voice.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voice.isSpeaking.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableStateOf(SovarixTab.HOME) }
    var showThermalDetailSheet by remember { mutableStateOf(false) }
    var showAskSovarixSheet by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    var showHowItWorksModal by remember { mutableStateOf(false) }

    SovarixTheme {
        Scaffold(
            containerColor = SovarixBg,
            bottomBar = {
                SovarixBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    isGamingActive = isGaming
                )
            },
            floatingActionButton = {
                // Polished SOVARIX Orb - The intelligence core
                SovarixOrb(
                    onClick = { showAskSovarixSheet = true },
                    modifier = Modifier.padding(bottom = 12.dp),
                    isSpeakingOrActive = isVoiceActive || isSpeaking
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(SovarixBg)
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(220),
                    label = "TabCrossfade"
                ) { tab ->
                    when (tab) {
                        SovarixTab.HOME -> HomeScreen(
                            repo = repo,
                            onStart = onStart,
                            onStop = onStop,
                            onNavigateToGame = { selectedTab = SovarixTab.GAME },
                            onNavigateToLab = { selectedTab = SovarixTab.LAB },
                            onOpenThermalDetail = { showThermalDetailSheet = true },
                            onOpenHowItWorks = { showHowItWorksModal = true }
                        )
                        SovarixTab.LAB -> LabScreen(
                            repo = repo,
                            onMessage = onMessage
                        )
                        SovarixTab.GAME -> GameScreen(
                            repo = repo,
                            onRequestScreenCapture = onRequestScreenCapture,
                            onRequestUsageAccess = onRequestUsageAccess,
                            onMessage = onMessage
                        )
                        SovarixTab.MEMORY -> MemoryScreen(
                            repo = repo,
                            onExport = onExport
                        )
                        SovarixTab.DEVICE -> DeviceScreen(
                            repo = repo,
                            onExport = onExport,
                            onPurge = { showPurgeConfirmDialog = true }
                        )
                    }
                }
            }
        }

        // =========================================================================
        // THERMAL DETAIL BOTTOM SHEET
        // =========================================================================
        if (showThermalDetailSheet) {
            ThermalDetailSheet(
                repo = repo,
                onDismiss = { showThermalDetailSheet = false },
                onMessage = onMessage
            )
        }

        // =========================================================================
        // ASK SOVARIX INTELLIGENCE BOTTOM SHEET
        // =========================================================================
        if (showAskSovarixSheet) {
            AskSovarixSheet(
                repo = repo,
                onDismiss = { showAskSovarixSheet = false }
            )
        }

        // =========================================================================
        // HOW IT WORKS MODAL
        // =========================================================================
        if (showHowItWorksModal) {
            HowItWorksModal(
                onDismiss = { showHowItWorksModal = false }
            )
        }

        // =========================================================================
        // PURGE HISTORY CONFIRMATION DIALOG
        // =========================================================================
        if (showPurgeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPurgeConfirmDialog = false },
                containerColor = SovarixSurface,
                title = { Text("Purge History", color = SovarixRed, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This will reset all recorded timeline events, baseline profiles, and learned Device DNA. Real device hardware will not be affected.",
                        color = SovarixTextPrimary,
                        lineHeight = 18.sp
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showPurgeConfirmDialog = false }) {
                        Text("Cancel", color = SovarixTextSecondary)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPurgeConfirmDialog = false
                            scope.launch {
                                val ok = repo.clear()
                                onMessage(if (ok) "History and Device DNA purged." else "Stop active session first.")
                            }
                        }
                    ) {
                        Text("Purge", color = SovarixRed, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
