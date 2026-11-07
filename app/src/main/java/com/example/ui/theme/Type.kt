package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium)
)

val MonospaceFontFamily = FontFamily.Monospace
val JetBrainsMonoFontFamily = InterFontFamily
val SpaceGroteskFontFamily = InterFontFamily
val PlusJakartaFontFamily = InterFontFamily

// Concept A type hierarchy (font setup unchanged — Inter + monospace):
// - Greeting / screen titles: 22–26sp SemiBold
// - Card titles: ~17sp SemiBold
// - Body: 13–15sp Regular
// - Labels: 11–12sp Medium with slight tracking (small-caps feel);
//   token/cost numbers use the monospace family at call sites.
private val GreetingStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.4).sp
)

private val ScreenTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.3).sp
)

private val Title20Style = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.2).sp
)

private val CardTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 23.sp,
    letterSpacing = (-0.1).sp
)

private val TitleSmallStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp
)

private val Body15Style = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp
)

private val Body14Style = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

private val Body13Style = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

private val LabelMediumStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.3.sp
)

private val LabelSmallStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.6.sp
)

val Typography = Typography(
    displayLarge = GreetingStyle,
    displayMedium = GreetingStyle,
    displaySmall = ScreenTitleStyle,
    headlineLarge = ScreenTitleStyle,
    headlineMedium = Title20Style,
    headlineSmall = Title20Style,
    titleLarge = Title20Style,
    titleMedium = CardTitleStyle,
    titleSmall = TitleSmallStyle,
    bodyLarge = Body15Style,
    bodyMedium = Body14Style,
    bodySmall = Body13Style,
    labelLarge = Body14Style.copy(fontWeight = FontWeight.Medium),
    labelMedium = LabelMediumStyle,
    labelSmall = LabelSmallStyle
)
