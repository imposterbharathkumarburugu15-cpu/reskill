package dev.sovarix.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
 * Floating Dynamic Navigation Surface.
 * Avoids heavy rectangular bars; sits as a sleek floating capsule.
 * Selected tab: bright cyan/white with animated glowing pill indicator.
 * Unselected: muted gray.
 */
@Composable
fun SovarixBottomNav(
    selectedTab: SovarixTab,
    onTabSelected: (SovarixTab) -> Unit,
    isGamingActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SovarixDark)
                .border(1.dp, SovarixBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabPrefixes = listOf("01", "02", "03", "04", "05")
            SovarixTab.entries.forEachIndexed { index, tab ->
                val isSelected = tab == selectedTab

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) SovarixCyan else SovarixTextMuted,
                    animationSpec = tween(180),
                    label = "iconColor"
                )

                val indicatorWidth by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "indicatorWidth"
                )

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SovarixSurfaceElevated else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) SovarixBorderActive else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = iconColor,
                            modifier = Modifier.size(19.dp)
                        )

                        // Subtle active gaming pulse dot on GAME tab
                        if (tab == SovarixTab.GAME && isGamingActive) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(SovarixGreen)
                            )
                        }
                    }

                    // Monospace Technical HUD Tag: e.g. "01·HOME"
                    Text(
                        text = "${tabPrefixes.getOrElse(index) { "0" }}·${stringResource(tab.labelRes)}",
                        fontSize = 8.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = SovarixFontMono,
                        letterSpacing = 0.5.sp,
                        color = iconColor
                    )

                    // Sharp HUD Active Underline
                    Box(
                        modifier = Modifier
                            .width(indicatorWidth)
                            .height(1.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isSelected) SovarixCyan else Color.Transparent)
                    )
                }
            }
        }
    }
}
