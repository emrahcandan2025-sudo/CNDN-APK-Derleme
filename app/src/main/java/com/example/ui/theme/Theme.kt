package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CndnDarkColorScheme = darkColorScheme(
    primary = CndnCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFF80F3FF),

    secondary = CndnEmerald,
    onSecondary = Color(0xFF00210B),
    secondaryContainer = Color(0xFF004D20),
    onSecondaryContainer = Color(0xFF7DF8A6),

    tertiary = CndnPurple,
    onTertiary = Color.White,

    background = CndnBackground,
    onBackground = CndnTextPrimary,

    surface = CndnSurface,
    onSurface = CndnTextPrimary,

    surfaceVariant = CndnSurfaceVariant,
    onSurfaceVariant = CndnTextSecondary,

    outline = CndnBorder,
    outlineVariant = Color(0xFF1F2937),

    error = CndnCrimson,
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF5A000A),
    onErrorContainer = Color(0xFFFFB4AB)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // CNDN is designed with a premium dark theme by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CndnDarkColorScheme,
        typography = Typography,
        content = content
    )
}
