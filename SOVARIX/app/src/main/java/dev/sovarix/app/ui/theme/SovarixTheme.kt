package dev.sovarix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ==============================================================================
// SOVARIX MINIMALIST HIGH-TECH HUD DESIGN SYSTEM
// OLED pitch-black, razor-thin borders, restrained industrial telemetry accents
// ==============================================================================

val SovarixBg = Color(0xFF000000)              // Pure OLED Pitch Black
val SovarixDark = Color(0xFF040609)            // Deepest HUD substrate
val SovarixSurface = Color(0xFF080D14)         // Minimalist HUD panel surface
val SovarixSurfaceElevated = Color(0xFF0F1722) // Interactive HUD surface
val SovarixBorder = Color(0xFF15202E)          // Razor-thin industrial border (1px)
val SovarixBorderActive = Color(0xFF223247)    // Active HUD border
val SovarixBorderAccent = Color(0x3300E5FF)    // Restrained cyan hairline border

val SovarixCyan = Color(0xFF00E5FF)            // Precision electric cyan (Primary HUD)
val SovarixCyanLight = Color(0xFF67F0FF)       // Bright telemetry readout cyan
val SovarixCyanSurface = Color(0xFF031A24)     // Cyan background tint
val SovarixGreen = Color(0xFF00F5A0)           // Stealth quantum emerald (Nominal / Verified)
val SovarixGreenSurface = Color(0xFF032115)    // Green tint
val SovarixAmber = Color(0xFFFFB800)           // Radar caution amber (Warming)
val SovarixAmberSurface = Color(0xFF241802)    // Amber tint
val SovarixOrange = Color(0xFFFF6200)          // Elevated thermal orange (High load)
val SovarixRed = Color(0xFFFF2A42)             // Critical alert crimson
val SovarixRedSurface = Color(0xFF26050A)      // Red tint

val SovarixDarkElevated = Color(0xFF0F1722)    // Interactive elevated surface
val SovarixBlack = Color(0xFF000000)           // Pure black
val SovarixFontMono = FontFamily.Monospace     // High-tech monospace data typography

val SovarixTextPrimary = Color(0xFFF8FAFC)     // Crisp white readout
val SovarixTextSecondary = Color(0xFF8492A6)   // Technical slate secondary
val SovarixTextMuted = Color(0xFF475569)       // Muted radar telemetry / captions

private val DarkColorPalette = darkColorScheme(
    primary = SovarixCyan,
    onPrimary = SovarixBlack,
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
