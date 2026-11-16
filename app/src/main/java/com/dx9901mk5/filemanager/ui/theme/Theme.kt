package com.dx9901mk5.filemanager.ui.theme

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

private val DxDarkColors = darkColorScheme(
    primary = Color(0xFF7FCFFF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6E),
    onPrimaryContainer = Color(0xFFC5E7FF),
    secondary = Color(0xFFB5C9D8),
    background = Color(0xFF0F1419),
    surface = Color(0xFF141B22),
    surfaceVariant = Color(0xFF1E2730),
    outlineVariant = Color(0xFF33414F)
)

private val DxLightColors = lightColorScheme(
    primary = Color(0xFF006491),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9E6FF),
    onPrimaryContainer = Color(0xFF001E2F),
    background = Color(0xFFF6F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EFF5)
)

@Composable
fun DxFileManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DxDarkColors
        else -> DxLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
