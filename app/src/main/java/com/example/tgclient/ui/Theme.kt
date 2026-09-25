package com.example.tgclient.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TelegramLightColors = lightColorScheme(
    primary = Color(0xFF3390EC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8ECFF),
    onPrimaryContainer = Color(0xFF0B3A63),
    secondary = Color(0xFF6B7C8F),
    background = Color(0xFFF1F3F5),
    onBackground = Color(0xFF17212B),
    surface = Color.White,
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFE7EBEF),
    onSurfaceVariant = Color(0xFF6B7785),
    outline = Color(0xFFD2D8DE),
)

private val TelegramDarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0D2C44),
    primaryContainer = Color(0xFF214A6C),
    onPrimaryContainer = Color(0xFFD8ECFF),
    secondary = Color(0xFFAEBECD),
    background = Color(0xFF17212B),
    onBackground = Color(0xFFE9EDF0),
    surface = Color(0xFF202B36),
    onSurface = Color(0xFFE9EDF0),
    surfaceVariant = Color(0xFF2B3948),
    onSurfaceVariant = Color(0xFFB2BFCA),
    outline = Color(0xFF465665),
)

@Composable
fun ChatwaveTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Keep Telegram's palette stable across devices instead of inheriting a
    // device theme that can make the messenger look completely white.
    val colors: ColorScheme = if (darkTheme) TelegramDarkColors else TelegramLightColors
    MaterialTheme(colorScheme = colors, content = content)
}
