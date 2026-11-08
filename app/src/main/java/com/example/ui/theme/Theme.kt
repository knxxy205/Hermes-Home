package com.example.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppPreferencesStore

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF2FE), onPrimaryContainer = Color(0xFF1746A2),
    secondary = Color(0xFF2563EB), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE), onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF216AAB), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF0FF), onTertiaryContainer = Color(0xFF163D59),
    background = Color(0xFFF7F9FD), onBackground = Color(0xFF1E293B),
    surface = Color.White, onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF0F4FA), onSurfaceVariant = Color(0xFF58677B),
    surfaceDim = Color(0xFFE4EAF3), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainer = Color(0xFFF0F4FA), surfaceContainerHigh = Color(0xFFE9EEF7),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFF7E8B9E), outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB91C1C), onError = Color.White,
    errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Color(0xFF263343), inverseOnSurface = Color(0xFFF1F5FB),
    inversePrimary = Color(0xFF9FC5FF), scrim = Color.Black,
    surfaceTint = Color.Transparent
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FC5FF), onPrimary = Color(0xFF10294E),
    primaryContainer = Color(0xFF203B61), onPrimaryContainer = Color(0xFFD7E7FF),
    secondary = Color(0xFF9FC5FF), onSecondary = Color(0xFF10294E),
    secondaryContainer = Color(0xFF263D5B), onSecondaryContainer = Color(0xFFD7E7FF),
    tertiary = Color(0xFF89CAFA), onTertiary = Color(0xFF102D43),
    tertiaryContainer = Color(0xFF1E3D53), onTertiaryContainer = Color(0xFFD1EAFE),
    background = Color(0xFF101722), onBackground = Color(0xFFE7EDF6),
    surface = Color(0xFF192331), onSurface = Color(0xFFE7EDF6),
    surfaceVariant = Color(0xFF263343), onSurfaceVariant = Color(0xFFB3C0D2),
    surfaceDim = Color(0xFF0D131D), surfaceBright = Color(0xFF2C394A),
    surfaceContainerLowest = Color(0xFF0D131D), surfaceContainerLow = Color(0xFF151E2B),
    surfaceContainer = Color(0xFF202C3B), surfaceContainerHigh = Color(0xFF263343),
    surfaceContainerHighest = Color(0xFF2C3B4D),
    outline = Color(0xFF7D8DA4), outlineVariant = Color(0xFF3C4C60),
    error = Color(0xFFFFB4AB), onError = Color(0xFF5D1716),
    errorContainer = Color(0xFF602323), onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE7EDF6), inverseOnSurface = Color(0xFF192331),
    inversePrimary = Color(0xFF2563EB), scrim = Color.Black,
    surfaceTint = Color.Transparent
)

internal fun resolveDarkAppearance(mode: String, systemDark: Boolean): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { AppPreferencesStore(context) }
    DisposableEffect(store) { onDispose { store.close() } }
    val preferences by store.state.collectAsState()
    val dark = darkTheme ?: resolveDarkAppearance(preferences.themeMode, isSystemInDarkTheme())
    val nightMode = when (preferences.themeMode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    SideEffect {
        val activity = context.appCompatActivity()
        if (darkTheme == null && activity != null && activity.delegate.localNightMode != nightMode) {
            activity.delegate.localNightMode = nightMode
        }
    }
    CompositionLocalProvider(LocalDarkAppearance provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
            ),
            content = content
        )
    }
}

private tailrec fun Context.appCompatActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.appCompatActivity()
    else -> null
}
