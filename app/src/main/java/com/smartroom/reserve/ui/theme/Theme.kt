package com.smartroom.reserve.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SuccessGreen = Color(0xFF2E7D32)
val WarnOrange = Color(0xFFEF6C00)
val InfoBlue = Color(0xFF1565C0)
val MutedGrey = Color(0xFF757575)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8EFEA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4E6F0),
    error = Color(0xFFC62828)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0E2283),
    primaryContainer = Color(0xFF2A3A9A),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFC8EFEA),
    tertiary = Color(0xFFFFB74D),
    error = Color(0xFFEF9A9A)
)

@Composable
fun SmartRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
