package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

private val TailHarnessColorScheme = lightColorScheme(
  primary = BurntOrange,
  onPrimary = Color.White,
  primaryContainer = BurntOrangePillBg,
  onPrimaryContainer = BurntOrangeLight,
  secondary = BurntOrangeLight,
  onSecondary = NavyDarkBackground,
  secondaryContainer = BurntOrangeDim,
  onSecondaryContainer = BurntOrangeLight,
  tertiary = TerminalCyan,
  background = NavyDarkBackground,
  onBackground = TextPrimary,
  surface = CardWhite,
  onSurface = TextPrimary,
  surfaceVariant = NavyDarkCard,
  onSurfaceVariant = TextSecondary,
  outline = NavyDarkBorder,
  outlineVariant = NavyCardBorder,
  error = TerminalRed,
  surfaceTint = Color.Transparent,
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = TailHarnessColorScheme,
    typography = Typography,
    shapes = Shapes(
      small = RoundedCornerShape(12.dp),
      medium = RoundedCornerShape(18.dp),
      large = RoundedCornerShape(24.dp),
      extraLarge = RoundedCornerShape(28.dp)
    ),
    content = content
  )
}
