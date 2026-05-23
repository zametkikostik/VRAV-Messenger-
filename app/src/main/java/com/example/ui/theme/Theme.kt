package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SleekAccent,
    secondary = SleekBorder,
    tertiary = SleekGreen,
    background = SleekBackground,
    surface = SleekSurface,
    onPrimary = SleekTextAccent,
    onBackground = SleekTextPrimary,
    onSurface = SleekTextPrimary
)

private val LightColorScheme = darkColorScheme( // VRAV is a pure dark sovereign app
    primary = SleekAccent,
    secondary = SleekBorder,
    tertiary = SleekGreen,
    background = SleekBackground,
    surface = SleekSurface,
    onPrimary = SleekTextAccent,
    onBackground = SleekTextPrimary,
    onSurface = SleekTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark sovereign UI
    dynamicColor: Boolean = false, // Force consistent branding
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
