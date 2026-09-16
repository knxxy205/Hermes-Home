package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TailHarnessColorScheme = darkColorScheme(
  primary = BurntOrange,
  onPrimary = NavyDarkBackground,
  primaryContainer = BurntOrangePillBg,
  onPrimaryContainer = BurntOrangeLight,
  secondary = BurntOrangeLight,
  onSecondary = NavyDarkBackground,
  secondaryContainer = BurntOrangeDim,
  onSecondaryContainer = BurntOrangeLight,
  tertiary = TerminalCyan,
  background = NavyDarkBackground,
  onBackground = TextPrimary,
  surface = NavyDarkSurface,
  onSurface = TextPrimary,
  surfaceVariant = NavyDarkCard,
  onSurfaceVariant = TextSecondary,
  outline = NavyDarkBorder,
  outlineVariant = NavyCardBorder,
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = TailHarnessColorScheme,
    typography = Typography,
    content = content
  )
}

