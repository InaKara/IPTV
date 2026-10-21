package com.example.iptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IPTVTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF2979FF), // Electric Blue
        secondary = Color(0xFF00E676), // Emerald Green
        background = Color(0xFF121212), // Cinema Dark
        surface = Color(0xFF1E1E1E),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
