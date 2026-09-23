package dev.sovarix.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sovarix.app.R
import dev.sovarix.app.TwinRepository
import dev.sovarix.app.ui.theme.*
import dev.sovarix.core.BlackBoxEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    repo: TwinRepository,
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    HistoryScreen(repo, onExport, modifier)
}

/**
 * Flagship Memory Screen: "Your phone remembers."
 *
 * An organic chronological timeline of your phone's behavioral history.
 * Replaces technical database cards with a story timeline:
 * - TODAY section
 * - Timestamped physical milestones (Gaming, Thermal, Prediction, Intervention, Recovery)
 * - Black Box export
 */
@Composable
fun HistoryScreen(
    repo: TwinRepository,
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val events by repo.blackBoxEvents.collectAsStateWithLifecycle()
    var expandedEventId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // =========================================================================
        // 1. HEADER
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "MEMORY",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = SovarixTextPrimary
                )
                Text(
                    text = "Your phone remembers.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SovarixCyan
                )
            }

            // Export Black Box action
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SovarixDark)
                    .border(1.dp, SovarixBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onExport)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "EXPORT",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = SovarixCyan
                )
            }
        }

        // =========================================================================
        // 2. TIMELINE OF EXPERIENCES
        // =========================================================================
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your phone's physical journey will be remembered here.\nGaming sessions, thermal adaptations, and recoveries will appear as a timeline.",
                    fontSize = 12.5.sp,
                    color = SovarixTextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 19.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                item {
                    Text(
                        text = "TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = SovarixTextMuted
                    )
                }

                items(events.reversed()) { event ->
                    TimelineMilestoneItem(
                        event = event,
                        isExpanded = expandedEventId == event.currentHash,
                        onToggleExpand = {
                            expandedEventId = if (expandedEventId == event.currentHash) null else event.currentHash
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineMilestoneItem(
    event: BlackBoxEvent,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))

    val (title, dotColor) = when (event.eventType) {
        "GAMING_STARTED", "SESSION_START" -> "Gaming started" to SovarixCyan
        "THERMAL_ALERT", "THERMAL_TREND_RISING" -> "Thermal trend rising" to SovarixAmber
        "AUTO_COOL_STARTED", "PROTECT_ACTIVATED" -> "Auto-Cool activated" to SovarixOrange
        "AUTO_COOL_VERIFIED", "AUTO_COOL_RECOVERY", "RECOVERY" -> "Thermal recovery" to SovarixGreen
        "SESSION_STOP", "NORMAL_RESTORED" -> "Normal state restored" to SovarixGreen
        "MOMENT_CAPTURED", "SPECIAL_MOMENT" -> "Special Moment captured" to SovarixCyan
        "PREDICTION_MADE" -> "Temperature predicted to rise" to SovarixTextSecondary
        "EXPERIMENT_STARTED" -> "Diagnostic test started" to SovarixCyan
        "EXPERIMENT_CANCELLED", "EXPERIMENT_COMPLETED" -> "Diagnostic test concluded" to SovarixGreen
        else -> event.eventType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } to SovarixTextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleExpand
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp column
        Text(
            text = timeStr,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SovarixTextMuted,
            modifier = Modifier.width(42.dp)
        )

        // Timeline node + connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(SovarixBorder)
            )
        }

        // Event text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = SovarixTextPrimary
            )

            if (event.notes.isNotBlank()) {
                Text(
                    text = event.notes,
                    fontSize = 11.5.sp,
                    color = SovarixTextSecondary,
                    lineHeight = 16.sp
                )
            }

            event.intervention?.let { action ->
                Text(
                    text = "Action: $action",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = SovarixCyan
                )
            }

            // Expandable technical hash verification
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SovarixDark)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "SHA-256: ${event.currentHash.take(18)}...",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SovarixTextMuted
                    )
                    Text(
                        text = "PREV: ${event.previousHash.take(18)}...",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SovarixTextMuted
                    )
                }
            }
        }
    }
}
