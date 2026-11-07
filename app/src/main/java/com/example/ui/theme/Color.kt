package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Runway design tokens — Concept A in BOTH modes.
// - LIGHT theme: "Home-first workspace" — warm cream background, white
//   cards, sage accent, deep forest ink text.
// - DARK theme: "Concept A Dark" — the same design language on the
//   near-black base family: #151920 cards, subtle light outlines, tinted
//   chips, and a lifted sage accent (the old Soft Indigo primary is gone
//   from the scheme; the constant survives only for the fixed code-syntax
//   palette in the markdown renderer).
// Screens must read colours from MaterialTheme.colorScheme, which switches
// between these two palettes.
// ============================================================================

// Legacy accent (code-syntax palette only — NOT a UI colour any more)
val SoftIndigo = Color(0xFF6C7CFF)
val SoftIndigoDim = Color(0x1F6C7CFF)

// Concept A dark accent: lifted sage, legible on near-black surfaces
val SageDark = Color(0xFF8FAE8B)
val SageDarkDim = Color(0xFF2A3628) // opaque deep-sage container on dark cards
val SageDarkOnContainer = Color(0xFFD3E2CE)
val SageDarkOnPrimary = Color(0xFF0F1D0C) // deep green-black ink on the lifted sage
val DarkCardSurface = Color(0xFF151920) // Concept A Dark card fill (per refs)
val DarkCardBorder = Color(0x1FFFFFFF) // subtle light outline: white at 12%

// Concept A (light) tokens
val Sage = Color(0xFF5F7D5A)
val SageTint = Color(0xFFEDF2E8)
val SageSelectedBorder = Color(0xFFB9C8B2)
val ForestInk = Color(0xFF23281F)
val DeepForest = Color(0xFF39412F)
val MutedOliveText = Color(0xFF7A7E6E)
val FaintOliveText = Color(0xFF9A9D8B)
val CreamBackground = Color(0xFFF6F4ED)
val CreamOnDark = Color(0xFFF4F3EA)
val CardBorderLight = Color(0xFFE7E3D6)
val CardBorderLightSoft = Color(0xFFEAE6D9)
val LightElevatedWarm = Color(0xFFEFEBDD)

// Dark Palette (Concept A Dark)
val DarkBackground = Color(0xFF0B0D10)
val DarkSurface = DarkCardSurface
val DarkElevatedSurface = Color(0xFF1D232C)
val DarkDivider = DarkCardBorder // 1px subtle light outline: white at 12% opacity

val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF8E95A2)
val TextMuted = Color(0xFF5E6572)

// Light Palette (Concept A)
val LightBackground = CreamBackground
val LightSurface = Color(0xFFFFFFFF)
val LightElevatedSurface = LightElevatedWarm
val LightDivider = CardBorderLight // 1px warm hairline border

val TextPrimaryLight = ForestInk
val TextSecondaryLight = DeepForest

// Status Dot Colors (Minimal)
val StatusGreenDot = Color(0xFF34D399)
val StatusGrayDot = Color(0xFF6B7280)

// Backward-compatible semantic aliases mapped to the single Soft Indigo accent & neutral grays
val MutedAccent = SoftIndigo
val MutedAccentContainer = SoftIndigoDim
val VioletAccent = SoftIndigo
val VioletAccentContainer = SoftIndigoDim
val SkyCyanAccent = SoftIndigo
val SkyCyanContainer = SoftIndigoDim

val EmeraldConnected = StatusGreenDot
val EmeraldSuccess = StatusGreenDot
val CoralError = Color(0xFFEF4444)
val CoralErrorContainer = Color(0x1FEF4444)
val OnCoralErrorContainer = Color(0xFFFCA5A5)
val AmberWarning = Color(0xFF9CA3AF)

val ObsidianCanvas = DarkBackground
val SlateSurface = DarkSurface
val SlateElevated = DarkElevatedSurface
val SlateBorder = DarkDivider

val CyberCyan = SoftIndigo
val CyberCyanContainer = SoftIndigoDim
val ElectricIndigo = SoftIndigo
val ElectricIndigoContainer = SoftIndigoDim
val ElectricViolet = SoftIndigo

val TextPrimaryDark = TextPrimary
val TextSecondaryDark = TextSecondary

val AlabasterCanvas = LightBackground
val WhiteSurface = LightSurface
val SoftSlateElevated = LightElevatedSurface
val LightBorder = LightDivider
val DeepIndigo = SoftIndigo
val DeepIndigoContainer = SoftIndigoDim
val DeepCyan = SoftIndigo
val DeepCyanContainer = SoftIndigoDim

// ============================================================================
// Theme accent palettes (Round 3): the user picks ONE accent family in
// Settings -> Appearance; surfaces/backgrounds stay Concept A in both modes
// and only the accent family (primary, containers, user bubble) changes.
// Sage is the original Concept A accent and reproduces the pre-round-3
// schemes exactly.
// ============================================================================

class RunwayAccentPalette(
    val id: String,
    val displayName: String,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val userBubble: Color,
    val userBubbleDot: Color
)

val RunwayAccentPalettes: List<RunwayAccentPalette> = listOf(
    RunwayAccentPalette(
        id = "SAGE",
        displayName = "Sage",
        lightPrimary = Sage,
        lightOnPrimary = Color.White,
        lightContainer = SageTint,
        lightOnContainer = ForestInk,
        darkPrimary = SageDark,
        darkOnPrimary = SageDarkOnPrimary,
        darkContainer = SageDarkDim,
        darkOnContainer = SageDarkOnContainer,
        userBubble = DeepForest,
        userBubbleDot = Color(0xFFA8C69A)
    ),
    RunwayAccentPalette(
        id = "OCEAN",
        displayName = "Ocean",
        lightPrimary = Color(0xFF0F6BAE),
        lightOnPrimary = Color.White,
        lightContainer = Color(0xFFE1EFFA),
        lightOnContainer = Color(0xFF0A3353),
        darkPrimary = Color(0xFF7FB8E8),
        darkOnPrimary = Color(0xFF072A42),
        darkContainer = Color(0xFF16334A),
        darkOnContainer = Color(0xFFCDE7FA),
        userBubble = Color(0xFF0C4A75),
        userBubbleDot = Color(0xFFA9D3F2)
    ),
    RunwayAccentPalette(
        id = "INDIGO",
        displayName = "Indigo",
        lightPrimary = Color(0xFF4F46E5),
        lightOnPrimary = Color.White,
        lightContainer = Color(0xFFE6E4FC),
        lightOnContainer = Color(0xFF28206E),
        darkPrimary = Color(0xFFA5B4FC),
        darkOnPrimary = Color(0xFF1C1850),
        darkContainer = Color(0xFF2C2960),
        darkOnContainer = Color(0xFFDFE3FF),
        userBubble = Color(0xFF3730A3),
        userBubbleDot = Color(0xFFC7D0FE)
    ),
    RunwayAccentPalette(
        id = "VIOLET",
        displayName = "Violet",
        lightPrimary = Color(0xFF7C3AED),
        lightOnPrimary = Color.White,
        lightContainer = Color(0xFFF0E7FD),
        lightOnContainer = Color(0xFF3E1470),
        darkPrimary = Color(0xFFC4B5FD),
        darkOnPrimary = Color(0xFF2C1052),
        darkContainer = Color(0xFF3B2563),
        darkOnContainer = Color(0xFFEBE3FF),
        userBubble = Color(0xFF5B21B6),
        userBubbleDot = Color(0xFFDDD0FE)
    ),
    RunwayAccentPalette(
        id = "AMBER",
        displayName = "Amber",
        lightPrimary = Color(0xFFB45309),
        lightOnPrimary = Color.White,
        lightContainer = Color(0xFFFAEFDB),
        lightOnContainer = Color(0xFF5E2E02),
        darkPrimary = Color(0xFFF2C063),
        darkOnPrimary = Color(0xFF3A2702),
        darkContainer = Color(0xFF4A340E),
        darkOnContainer = Color(0xFFFBEAC4),
        userBubble = Color(0xFF92400E),
        userBubbleDot = Color(0xFFF6D79B)
    ),
    RunwayAccentPalette(
        id = "ROSE",
        displayName = "Rose",
        lightPrimary = Color(0xFFBE3455),
        lightOnPrimary = Color.White,
        lightContainer = Color(0xFFFBE6EB),
        lightOnContainer = Color(0xFF5E1124),
        darkPrimary = Color(0xFFF49AB4),
        darkOnPrimary = Color(0xFF430D1E),
        darkContainer = Color(0xFF51202E),
        darkOnContainer = Color(0xFFFBDCE4),
        userBubble = Color(0xFF9F2543),
        userBubbleDot = Color(0xFFF8C3D2)
    )
)

fun runwayAccentPalette(id: String): RunwayAccentPalette =
    RunwayAccentPalettes.firstOrNull { it.id.equals(id, ignoreCase = true) }
        ?: RunwayAccentPalettes.first()
