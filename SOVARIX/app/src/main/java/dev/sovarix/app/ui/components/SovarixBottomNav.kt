package dev.sovarix.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sovarix.app.R
import dev.sovarix.app.ui.theme.*

enum class SovarixTab(
    @StringRes val labelRes: Int,
    val fallbackLabel: String,
    val icon: ImageVector
) {
    HOME(R.string.tab_home, "HOME", SovarixIcons.Home),
    LAB(R.string.tab_lab, "LAB", SovarixIcons.Lab),
    GAME(R.string.tab_game, "GAME", SovarixIcons.Game),
    MEMORY(R.string.tab_memory, "MEMORY", SovarixIcons.Memory),
    DEVICE(R.string.tab_device, "DEVICE", SovarixIcons.Device);

    val label: String get() = fallbackLabel

    companion object {
        val HISTORY get() = MEMORY
    }
}

/**
 * Native Android Flagship Bottom Navigation Bar.
 * Strictly avoids emoji icons in favor of native vector geometry.
 * Employs animated accent transitions and a subtle under-tab indicator.
 */
@Composable
fun SovarixBottomNav(
    selectedTab: SovarixTab,
    onTabSelected: (SovarixTab) -> Unit,
    isGamingActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SovarixDark)
    ) {
        // Subtle top separator line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SovarixBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SovarixTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) SovarixCyan else SovarixTextMuted,
                    animationSpec = tween(220),
                    label = "iconColor"
                )

                val indicatorWidth by animateDpAsState(
                    targetValue = if (isSelected) 20.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "indicatorWidth"
                )

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )

                        // Subtle active gaming pulse dot on GAME tab
                        if (tab == SovarixTab.GAME && isGamingActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(SovarixGreen)
                            )
                        }
                    }

                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                        color = iconColor
                    )

                    // Subtle animated active tab pill
                    Box(
                        modifier = Modifier
                            .width(indicatorWidth)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isSelected) SovarixCyan else Color.Transparent)
                    )
                }
            }
        }
    }
}
