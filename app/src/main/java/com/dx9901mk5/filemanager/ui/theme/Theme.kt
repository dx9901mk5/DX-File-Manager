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
import com.dx9901mk5.filemanager.core.settings.DarkBackgroundStyle

// 暗色主題（全黑 OLED 螢幕適用）：純黑 #000000 底色 + 金黃色強調 + 高對比純白文字
private val DxOledDarkColors = darkColorScheme(
    primary = Color(0xFFFFC83D),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3B2E00),
    onPrimaryContainer = Color(0xFFFFF3C4),
    secondary = Color(0xFFFFD566),
    onSecondary = Color(0xFF1E1700),
    secondaryContainer = Color(0xFF2C2612),
    onSecondaryContainer = Color.White,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF000000),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFE5E5E5),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF262626)
)

// 暗色主題（灰黑底）：柔和灰黑底色 + 金黃色強調 + 高對比純白文字
private val DxGrayDarkColors = darkColorScheme(
    primary = Color(0xFFFFC83D),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3B2E00),
    onPrimaryContainer = Color(0xFFFFF3C4),
    secondary = Color(0xFFFFD566),
    onSecondary = Color(0xFF1E1700),
    secondaryContainer = Color(0xFF2C2612),
    onSecondaryContainer = Color.White,
    background = Color(0xFF18191C),
    onBackground = Color.White,
    surface = Color(0xFF202226),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2B2E33),
    onSurfaceVariant = Color(0xFFE5E5E5),
    outline = Color(0xFF787C85),
    outlineVariant = Color(0xFF3F434A)
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
    darkBackgroundStyle: DarkBackgroundStyle = DarkBackgroundStyle.OLED_BLACK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> when (darkBackgroundStyle) {
            DarkBackgroundStyle.OLED_BLACK -> DxOledDarkColors
            DarkBackgroundStyle.DARK_GRAY -> DxGrayDarkColors
        }
        else -> DxLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
