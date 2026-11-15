package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Aerodock Soft Dark — the redesign palette (spec Part 2). Pure black
// background, dark gray cards, one orange accent. Flat surfaces only:
// no shadows, gradients or glow anywhere in this scheme.
// These constants are the ONLY home of the redesign hex values; screens
// read colours from MaterialTheme.colorScheme and LocalAerodockExtraColors.
// ============================================================================

val SoftDarkBackground = Color(0xFF000000)
val SoftDarkSurface = Color(0xFF1A1A1A)
val SoftDarkSurfaceVariant = Color(0xFF2E2E2E)
val SoftDarkOnBackground = Color(0xFFF5F5F5)
val SoftDarkMuted = Color(0xFF9A9A9A)
val SoftDarkOutline = Color(0xFF2A2A2A)
val SoftDarkTabDivider = Color(0xFF1F1F1F)
val SoftDarkAccent = Color(0xFFFF7A45)
val SoftDarkToolTint = Color(0xFF14201A)
val SoftDarkStatusOn = Color(0xFF7FD39A)
val SoftDarkError = Color(0xFFFF6B6B)

val AerodockSoftDarkColorScheme = darkColorScheme(
    primary = SoftDarkAccent,
    onPrimary = Color.White,
    primaryContainer = SoftDarkSurfaceVariant,
    onPrimaryContainer = SoftDarkOnBackground,
    secondary = SoftDarkSurfaceVariant,
    onSecondary = SoftDarkOnBackground,
    secondaryContainer = SoftDarkSurfaceVariant,
    onSecondaryContainer = SoftDarkOnBackground,
    tertiary = SoftDarkStatusOn,
    onTertiary = Color.Black,
    background = SoftDarkBackground,
    onBackground = SoftDarkOnBackground,
    surface = SoftDarkSurface,
    onSurface = SoftDarkOnBackground,
    surfaceVariant = SoftDarkSurfaceVariant,
    onSurfaceVariant = SoftDarkMuted,
    surfaceDim = SoftDarkBackground,
    surfaceBright = SoftDarkSurfaceVariant,
    surfaceContainerLowest = SoftDarkBackground,
    surfaceContainerLow = SoftDarkSurface,
    surfaceContainer = SoftDarkSurface,
    surfaceContainerHigh = SoftDarkSurfaceVariant,
    surfaceContainerHighest = SoftDarkSurfaceVariant,
    inverseSurface = SoftDarkOnBackground,
    inverseOnSurface = SoftDarkBackground,
    inversePrimary = SoftDarkAccent,
    outline = SoftDarkOutline,
    outlineVariant = SoftDarkOutline,
    error = SoftDarkError,
    onError = Color.Black
)

/**
 * Redesign colours that have no Material 3 role: the tool-request tint,
 * the green "On / Active" status pairing, and the tab-bar divider.
 * Provided by OmniAIStudioTheme in both modes; the light values reuse the
 * existing Concept A light constants so Light mode keeps its own identity.
 */
data class AerodockExtraColors(
    val toolTint: Color,
    val statusOnText: Color,
    val tabDivider: Color
)

val AerodockSoftDarkExtraColors = AerodockExtraColors(
    toolTint = SoftDarkToolTint,
    statusOnText = SoftDarkStatusOn,
    tabDivider = SoftDarkTabDivider
)

val AerodockLightExtraColors = AerodockExtraColors(
    toolTint = SageTint,
    statusOnText = Sage,
    tabDivider = CardBorderLight
)

val LocalAerodockExtraColors = staticCompositionLocalOf { AerodockSoftDarkExtraColors }
