package dev.quietly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Quiet, dark, minimal palette
private val DarkColors = darkColorScheme(
    primary         = Color(0xFF8BC4F5),  // soft blue
    onPrimary       = Color(0xFF00325A),
    primaryContainer= Color(0xFF004880),
    secondary       = Color(0xFF90CAF9),
    background      = Color(0xFF0D0D0D),
    surface         = Color(0xFF1A1A1A),
    onBackground    = Color(0xFFE2E2E2),
    onSurface       = Color(0xFFE2E2E2),
    error           = Color(0xFFCF6679),
)

@Composable
fun QuietlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = QuietlyTypography,
        content     = content
    )
}
