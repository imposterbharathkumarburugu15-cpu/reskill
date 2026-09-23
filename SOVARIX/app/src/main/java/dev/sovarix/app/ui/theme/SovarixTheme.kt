package dev.sovarix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ==============================================================================
// SOVARIX FLAGSHIP DESIGN SYSTEM
// Premium dark-first palette: Deep graphite, elevated surfaces, electric cyan
// ==============================================================================

val SovarixBg = Color(0xFF080B10)              // Deep graphite/near black
val SovarixDark = Color(0xFF0D1219)            // Subtle secondary dark
val SovarixSurface = Color(0xFF121924)         // Primary elevated surface
val SovarixSurfaceElevated = Color(0xFF172030) // Interactive surface
val SovarixBorder = Color(0xFF1B2536)          // Refined border
val SovarixBorderActive = Color(0xFF283850)    // Focused border

val SovarixCyan = Color(0xFF00E5FF)            // Restrained flagship electric cyan
val SovarixCyanSurface = Color(0xFF06232F)     // Cyan background tint
val SovarixGreen = Color(0xFF00FF66)           // Stable/nominal state
val SovarixGreenSurface = Color(0xFF062418)    // Green tint
val SovarixAmber = Color(0xFFF59E0B)           // Rising thermal state
val SovarixAmberSurface = Color(0xFF261907)    // Amber tint
val SovarixOrange = Color(0xFFFF6B2B)          // Hot state
val SovarixRed = Color(0xFFEF4444)             // Critical state
val SovarixRedSurface = Color(0xFF280B10)      // Red tint

val SovarixTextPrimary = Color(0xFFF8FAFC)     // Crisp primary text
val SovarixTextSecondary = Color(0xFF94A3B8)   // Slate secondary
val SovarixTextMuted = Color(0xFF64748B)       // Muted details / caption

private val DarkColorPalette = darkColorScheme(
    primary = SovarixCyan,
    onPrimary = SovarixBg,
    background = SovarixBg,
    surface = SovarixSurface,
    onSurface = SovarixTextPrimary,
    onBackground = SovarixTextPrimary,
    outline = SovarixBorder,
    secondary = SovarixTextSecondary
)

@Composable
fun SovarixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorPalette,
        content = content
    )
}

/**
 * Returns the semantic color for a thermal temperature / status.
 * Never turns the entire screen red—only semantic indicators.
 */
fun thermalSemanticColor(temperatureC: Double?, thermalState: Int?): Color {
    val temp = temperatureC ?: 35.0
    val status = thermalState ?: 0
    return when {
        status >= 4 || temp >= 45.0 -> SovarixRed
        status >= 2 || temp >= 40.0 -> SovarixOrange
        status >= 1 || temp >= 38.0 -> SovarixAmber
        else -> SovarixCyan
    }
}
