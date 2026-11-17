package com.avalon.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 奶油纸色 + 深绿主色，取自应用头像的配色气质；暖色纸面配深绿清单，深色模式为深灰绿。
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5C42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9EBD8),
    onPrimaryContainer = Color(0xFF0B3021),
    secondary = Color(0xFF4E655A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8DB),
    onSecondaryContainer = Color(0xFF0D271C),
    tertiary = Color(0xFF8A5A21),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDAE),
    onTertiaryContainer = Color(0xFF2E1D00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF5E9),
    onBackground = Color(0xFF1B201B),
    surface = Color(0xFFFAF5E9),
    onSurface = Color(0xFF1B201B),
    surfaceVariant = Color(0xFFE5E1CF),
    onSurfaceVariant = Color(0xFF474B40),
    outline = Color(0xFF777D6F),
    outlineVariant = Color(0xFFC9C8B6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F342E),
    inverseOnSurface = Color(0xFFEEEFE6),
    inversePrimary = Color(0xFF97D5AF),
    surfaceDim = Color(0xFFDAD6C4),
    surfaceBright = Color(0xFFFAF5E9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F0E2),
    surfaceContainer = Color(0xFFEEEADC),
    surfaceContainerHigh = Color(0xFFE8E4D5),
    surfaceContainerHighest = Color(0xFFE2DECF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93D7B1),
    onPrimary = Color(0xFF0A3822),
    primaryContainer = Color(0xFF1E4B37),
    onPrimaryContainer = Color(0xFFACEFCC),
    secondary = Color(0xFFB4CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFD0E8D9),
    tertiary = Color(0xFFEAC08D),
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF61421B),
    onTertiaryContainer = Color(0xFFFFDDB4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111512),
    onBackground = Color(0xFFE0E3DA),
    surface = Color(0xFF111512),
    onSurface = Color(0xFFE0E3DA),
    surfaceVariant = Color(0xFF414940),
    onSurfaceVariant = Color(0xFFC1C8BB),
    outline = Color(0xFF8B9386),
    outlineVariant = Color(0xFF414940),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE0E3DA),
    inverseOnSurface = Color(0xFF2F342E),
    inversePrimary = Color(0xFF1F5C42),
    surfaceDim = Color(0xFF111512),
    surfaceBright = Color(0xFF373B35),
    surfaceContainerLowest = Color(0xFF0C0F0C),
    surfaceContainerLow = Color(0xFF191D19),
    surfaceContainer = Color(0xFF1D211D),
    surfaceContainerHigh = Color(0xFF272B27),
    surfaceContainerHighest = Color(0xFF323632),
)

@Composable
internal fun AvalonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
