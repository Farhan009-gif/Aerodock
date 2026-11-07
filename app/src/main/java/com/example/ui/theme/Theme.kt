package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val GeminiDarkBg = DarkBackground
val GeminiSurface = DarkSurface
val GeminiSurfaceElevated = DarkElevatedSurface
val GeminiAccent = SoftIndigo
val GeminiEmerald = EmeraldSuccess
val GeminiCoral = CoralError
val GeminiAmber = AmberWarning
val GeminiTextPrimary = TextPrimary
val GeminiTextSecondary = TextSecondary
val GeminiBorder = DarkDivider

/**
 * True when the currently active color scheme is the dark (near-black) one.
 * Used for the few places that need a structural difference between modes
 * (e.g. the forest-filled model pill / user bubble that only exist in the
 * Concept A light design).
 */
@Composable
fun runwayIsDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// Concept A corner language: cards 16–22dp, sheets/dialogs 24dp top corners.
private val RunwayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val RunwayDarkColorScheme = darkColorScheme(
    primary = SoftIndigo,
    onPrimary = Color.White,
    primaryContainer = SoftIndigoDim,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkElevatedSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkElevatedSurface,
    onSurfaceVariant = TextSecondary,
    surfaceDim = DarkBackground,
    surfaceBright = Color(0xFF2A2F38),
    surfaceContainerLowest = Color(0xFF07080A),
    surfaceContainerLow = Color(0xFF11141A),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkElevatedSurface,
    surfaceContainerHighest = Color(0xFF22272F),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = SoftIndigo,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = CoralError,
    onError = Color.White
)

private val RunwayLightColorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageTint,
    onPrimaryContainer = ForestInk,
    secondary = DeepForest,
    onSecondary = Color.White,
    secondaryContainer = SageTint,
    onSecondaryContainer = ForestInk,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = CreamBackground,
    onBackground = ForestInk,
    surface = Color.White,
    onSurface = ForestInk,
    surfaceVariant = LightElevatedWarm,
    onSurfaceVariant = MutedOliveText,
    surfaceDim = Color(0xFFE9E5D8),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color(0xFFF3F0E6),
    surfaceContainerHigh = Color(0xFFEFEBDF),
    surfaceContainerHighest = Color(0xFFEAE6D9),
    inverseSurface = ForestInk,
    inverseOnSurface = CreamOnDark,
    inversePrimary = SageTint,
    outline = CardBorderLight,
    outlineVariant = CardBorderLightSoft,
    error = CoralError,
    onError = Color.White
)

@Composable
fun OmniAIStudioTheme(
    themeMode: String = "DARK",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "SYSTEM" -> systemDark
        else -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> RunwayDarkColorScheme
        else -> RunwayLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RunwayShapes,
        content = content
    )
}
