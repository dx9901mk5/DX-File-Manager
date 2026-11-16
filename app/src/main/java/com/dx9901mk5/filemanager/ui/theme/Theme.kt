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

// Windows 11 暗色主題：純黑底色 + 經典檔案總管金黃色強調
private val DxDarkColors = darkColorScheme(
    primary = Color(0xFFFFC83D),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3B2E00),
    onPrimaryContainer = Color(0xFFFFE48F),
    secondary = Color(0xFFE6B84C),
    onSecondary = Color(0xFF1E1700),
    secondaryContainer = Color(0xFF2C2612),
    onSecondaryContainer = Color(0xFFF5DDA0),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF2E2E2E)
)

private val DxLightColors = lightColorScheme(
    primary = Color(0xFFC48B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEBA8),
    onPrimaryContainer = Color(0xFF2A1D00),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBEBEB)
)

@Composable
fun DxFileManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
