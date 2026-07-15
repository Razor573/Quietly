package dev.quietly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF8BC4F5),
    onPrimary        = Color(0xFF003353),
    primaryContainer = Color(0xFF004A76),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary        = Color(0xFFB3CAE3),
    background       = Color(0xFF0D0D0D),
    surface          = Color(0xFF1A1A1A),
    onBackground     = Color(0xFFE2E2E6),
    onSurface        = Color(0xFFE2E2E6),
    error            = Color(0xFFFFB4AB),
    errorContainer   = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    tertiary         = Color(0xFFFFB951)
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF00658F),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9E6FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary        = Color(0xFF50606F),
    background       = Color(0xFFF8F9FF),
    surface          = Color(0xFFF8F9FF),
    onBackground     = Color(0xFF191C20),
    onSurface        = Color(0xFF191C20),
    error            = Color(0xFFBA1A1A),
    tertiary         = Color(0xFFB56300)
)

@Composable
fun QuietlyTheme(
    darkTheme: Boolean = true,
    content:   @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = QuietlyTypography,
        content     = content
    )
}
