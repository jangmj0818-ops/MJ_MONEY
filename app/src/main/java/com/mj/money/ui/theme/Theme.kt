package com.mj.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue800,
    secondary = Blue600,
    onSecondary = Color.White,
    secondaryContainer = Blue50,
    onSecondaryContainer = Blue800,
    tertiary = Amber600,
    onTertiary = Color.White,
    tertiaryContainer = Amber50,
    onTertiaryContainer = Amber800,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Blue900,
    primaryContainer = Blue800,
    onPrimaryContainer = Blue50,
    secondary = Blue200,
    onSecondary = Blue900,
    secondaryContainer = Blue800,
    onSecondaryContainer = Blue50,
    tertiary = Amber200,
    onTertiary = Amber800,
    tertiaryContainer = Amber800,
    onTertiaryContainer = Amber50,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun MJMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
