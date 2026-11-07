package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Runway design tokens.
// - DARK theme: the original near-black design (#0B0D10 family) with the
//   Soft Indigo (#6C7CFF) accent — kept complete and unchanged.
// - LIGHT theme: Concept A "Home-first workspace" — warm cream background,
//   white cards, sage accent, deep forest ink text.
// Screens must read colours from MaterialTheme.colorScheme (which switches
// between these two palettes); the accent constants below are the DARK
// palette values and exist for the theme definition itself plus a few
// fixed data/syntax palettes.
// ============================================================================

// Dark accent (Soft Indigo)
val SoftIndigo = Color(0xFF6C7CFF)
val SoftIndigoDim = Color(0x1F6C7CFF)

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

// Dark Palette (Default)
val DarkBackground = Color(0xFF0B0D10)
val DarkSurface = Color(0xFF14171C)
val DarkElevatedSurface = Color(0xFF1B1F26)
val DarkDivider = Color(0x14FFFFFF) // 1px subtle border: white at 8% opacity

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
