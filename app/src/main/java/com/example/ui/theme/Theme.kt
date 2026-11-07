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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val GeminiDarkBg = DarkBackground
val GeminiSurface = DarkSurface
val GeminiSurfaceElevated = DarkElevatedSurface
val GeminiAccent = SageDark
val GeminiEmerald = EmeraldSuccess
val GeminiCoral = CoralError
val GeminiAmber = AmberWarning
val GeminiTextPrimary = TextPrimary
val GeminiTextSecondary = TextSecondary
val GeminiBorder = DarkDivider

/**
 * True when the currently active color scheme is the dark (Concept A Dark)
 * one. The design is structurally identical in both modes; this survives
 * only for genuinely mode-specific artwork (e.g. the deliberately-dark
 * terminal console and logo tile).
 */
@Composable
fun runwayIsDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Filled user-bubble colours for the active theme accent (Round 3). The
 * bubble fill is a deep shade of the selected accent family, used in BOTH
 * modes (Sage = the original DeepForest); [dot] is the pale status-dot tint
 * shown on the deep-filled model pill in light mode.
 */
data class RunwayAccentBubble(
    val fill: Color,
    val dot: Color
)

val LocalRunwayAccentBubble = staticCompositionLocalOf {
    RunwayAccentBubble(fill = DeepForest, dot = Color(0xFFA8C69A))
}

// Concept A corner language: cards 16–22dp, sheets/dialogs 24dp top corners.
private val RunwayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val RunwayDarkColorScheme = darkColorScheme(
    primary = SageDark,
    onPrimary = SageDarkOnPrimary,
    primaryContainer = SageDarkDim,
    onPrimaryContainer = SageDarkOnContainer,
    secondary = SageDark,
    onSecondary = SageDarkOnPrimary,
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
    surfaceBright = Color(0xFF2E3641),
    surfaceContainerLowest = Color(0xFF07080A),
    surfaceContainerLow = Color(0xFF10131A),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkElevatedSurface,
    surfaceContainerHighest = Color(0xFF262D37),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = SageDark,
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
    themeAccent: String = "SAGE",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "SYSTEM" -> systemDark
        else -> true
    }

    // Round 3: recolour the Concept A schemes with the selected accent
    // family. With the default SAGE accent every copied value equals the
    // static schemes above, so the original look is reproduced exactly.
    val accentPalette = runwayAccentPalette(themeAccent)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> RunwayDarkColorScheme.copy(
            primary = accentPalette.darkPrimary,
            onPrimary = accentPalette.darkOnPrimary,
            primaryContainer = accentPalette.darkContainer,
            onPrimaryContainer = accentPalette.darkOnContainer,
            secondary = accentPalette.darkPrimary,
            onSecondary = accentPalette.darkOnPrimary,
            inversePrimary = accentPalette.darkPrimary
        )
        else -> RunwayLightColorScheme.copy(
            primary = accentPalette.lightPrimary,
            onPrimary = accentPalette.lightOnPrimary,
            primaryContainer = accentPalette.lightContainer,
            onPrimaryContainer = accentPalette.lightOnContainer,
            secondary = accentPalette.userBubble,
            onSecondary = Color.White,
            secondaryContainer = accentPalette.lightContainer,
            onSecondaryContainer = accentPalette.lightOnContainer,
            inversePrimary = accentPalette.lightContainer
        )
    }

    CompositionLocalProvider(
        LocalRunwayAccentBubble provides RunwayAccentBubble(
            fill = accentPalette.userBubble,
            dot = accentPalette.userBubbleDot
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = RunwayShapes,
            content = content
        )
    }
}
