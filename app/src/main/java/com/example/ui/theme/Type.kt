package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val JakartaSans = FontFamily(
    Font(R.font.jakarta_400, FontWeight.Normal),
    Font(R.font.jakarta_500, FontWeight.Medium),
    Font(R.font.jakarta_600, FontWeight.SemiBold),
    Font(R.font.jakarta_700, FontWeight.Bold),
    Font(R.font.jakarta_800, FontWeight.ExtraBold)
)
val TerminalFont = FontFamily(Font(R.font.jetbrains_mono))

private fun uiStyle(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = JakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displayLarge = uiStyle(48, 56, FontWeight.ExtraBold),
    displayMedium = uiStyle(40, 48, FontWeight.ExtraBold),
    displaySmall = uiStyle(32, 40, FontWeight.Bold),
    headlineLarge = uiStyle(30, 38, FontWeight.Bold),
    headlineMedium = uiStyle(28, 36, FontWeight.Bold),
    headlineSmall = uiStyle(24, 32, FontWeight.Bold),
    titleLarge = uiStyle(24, 32, FontWeight.Bold),
    titleMedium = uiStyle(16, 24, FontWeight.Bold),
    titleSmall = uiStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = uiStyle(16, 24),
    bodyMedium = uiStyle(14, 22),
    bodySmall = uiStyle(12, 18),
    labelLarge = uiStyle(14, 20, FontWeight.SemiBold),
    labelMedium = uiStyle(12, 18, FontWeight.SemiBold),
    labelSmall = uiStyle(11, 16, FontWeight.Medium)
)
