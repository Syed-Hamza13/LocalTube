package dev.hamza.localtube.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.hamza.localtube.domain.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF690005),
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = AccentDark,
    secondary = Ink200,
    tertiary = Warn,
    background = Ink900,
    onBackground = Ink100,
    surface = Ink900,
    onSurface = Ink100,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink200,
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB3000E),
    onPrimary = Color.White,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = OnAccentContainerLight,
    background = Paper99,
    onBackground = Color(0xFF1C1B1F),
    surface = Paper99,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Paper95,
    onSurfaceVariant = Paper30,
    outline = Color(0xFF79747E),
)

@Composable
fun LocalTubeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LocalTubeTypography,
        shapes = LocalTubeShapes,
        content = content,
    )
}
