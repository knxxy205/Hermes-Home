package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF3B82F6)
internal val LocalDarkAppearance = staticCompositionLocalOf { false }
val IsDarkAppearance: Boolean @Composable @ReadOnlyComposable get() = LocalDarkAppearance.current

val Canvas: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val CardWhite: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val SoftSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val MutedInk: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val SoftBorder: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
val FolderBlue: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF183B57) else Color(0xFFBCE6FD)
val FolderPeach: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF49391F) else Color(0xFFFDE2AF)
val FolderRose: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF4D293A) else Color(0xFFFFBAC6)
val FolderMint: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF163F35) else Color(0xFFA4EADA)

// Compatibility names now resolve the current semantic palette.
val NavyDarkBackground: Color @Composable @ReadOnlyComposable get() = Canvas
val NavyDarkSurface: Color @Composable @ReadOnlyComposable get() = SoftSurface
val NavyDarkCard: Color @Composable @ReadOnlyComposable get() = CardWhite
val NavyDarkBorder: Color @Composable @ReadOnlyComposable get() = SoftBorder
val NavyCardBorder: Color @Composable @ReadOnlyComposable get() = SoftBorder
val BurntOrange: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val BurntOrangeLight: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val BurntOrangeDim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
val BurntOrangeBadgeBg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
val BurntOrangeBorder: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF45658F) else Color(0xFFD5E5FC)
val BurntOrangePillBg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

val TerminalBg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val TerminalGreen: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF83D8B8) else Color(0xFF0F6B55)
val TerminalCyan: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF89CAFA) else Color(0xFF216AAB)
val TerminalYellow: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFFE7C47C) else Color(0xFF87600D)
val TerminalRed: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val TerminalWhite: Color @Composable @ReadOnlyComposable get() = Ink
val TerminalGray: Color @Composable @ReadOnlyComposable get() = MutedInk
val TextPrimary: Color @Composable @ReadOnlyComposable get() = Ink
val TextSecondary: Color @Composable @ReadOnlyComposable get() = MutedInk
val TextTertiary: Color @Composable @ReadOnlyComposable get() = MutedInk
val SuccessContainer: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF17392F) else Color(0xFFE8F7F1)
val CodeSurface: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF172233) else Color(0xFFF3F3F3)
val UserMessageSurface: Color @Composable @ReadOnlyComposable get() = if (IsDarkAppearance) Color(0xFF22354E) else Color(0xFFEEF3FB)
