package com.avalon.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 紫金竖排星图夜空方案（preview/index.html 批准稿）：
// 深紫夜空 #180E2D 为底，暖金 #DCAB62 作主控件色，淡紫 #C5AFD9 为辅助，
// 文字用暖羊皮纸色 #EEE1D0。两种系统模式统一使用该深色品牌方向。
private val NightColors = darkColorScheme(
    primary = Color(0xFFDCAB62),
    onPrimary = Color(0xFF2E2038),
    primaryContainer = Color(0xFF4E3A1E),
    onPrimaryContainer = Color(0xFFEBD9AA),
    secondary = Color(0xFFC5AFD9),
    onSecondary = Color(0xFF281D36),
    secondaryContainer = Color(0xFF3A2F4E),
    onSecondaryContainer = Color(0xFFE2D3EC),
    tertiary = Color(0xFFD5C6AC),
    onTertiary = Color(0xFF302439),
    tertiaryContainer = Color(0xFF514430),
    onTertiaryContainer = Color(0xFFF1E2C9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF180E2D),
    onBackground = Color(0xFFEEE1D0),
    surface = Color(0xFF180E2D),
    onSurface = Color(0xFFEEE1D0),
    surfaceVariant = Color(0xFF322A48),
    onSurfaceVariant = Color(0xFFC3B0C6),
    outline = Color(0xFF9A8495),
    outlineVariant = Color(0xFF635466),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEEE1D0),
    inverseOnSurface = Color(0xFF332941),
    inversePrimary = Color(0xFF7A5A28),
    surfaceDim = Color(0xFF140A26),
    surfaceBright = Color(0xFF3B3155),
    surfaceContainerLowest = Color(0xFF120A22),
    surfaceContainerLow = Color(0xFF1C1233),
    surfaceContainer = Color(0xFF211A35),
    surfaceContainerHigh = Color(0xFF28213B),
    surfaceContainerHighest = Color(0xFF342B4C),
)

@Composable
internal fun AvalonTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NightColors,
        content = content,
    )
}
