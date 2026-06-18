package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = TechCyan,
    secondary = TechBlue,
    tertiary = TechMint,
    background = Slate950,
    surface = Slate900,
    surfaceVariant = Slate800,
    outline = Slate700,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color(0xFFF1F5F9), // Slate 100
    onSurfaceVariant = Color(0xFF94A3B8), // Slate 400
    error = AccentPink
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0891B2), // Cyan 600
    secondary = Color(0xFF0284C7), // Sky 600
    tertiary = Color(0xFF059669), // Emerald 600
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF2F7),
    outline = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A), // Slate 900
    onSurface = Color(0xFF1E293B), // Slate 800
    onSurfaceVariant = Color(0xFF64748B), // Slate 500
    error = Color(0xFFE11D48) // Rose 600
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false to ensure our custom tailored branding shines, yet can be true on newer OS if preferred.
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
