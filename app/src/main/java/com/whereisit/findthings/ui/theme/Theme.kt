package com.whereisit.findthings.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.whereisit.findthings.data.repository.AppTheme

private val SandColors = lightColorScheme(
    primary = SandPrimary,
    onPrimary = AppOnDarkText,
    secondary = SandSecondary,
    onSecondary = AppOnDarkText,
    tertiary = SandAccent,
    background = SandBackground,
    onBackground = AppOnLight,
    surface = SandSurface,
    onSurface = AppOnLight
)

private val MintColors = lightColorScheme(
    primary = MintPrimary,
    onPrimary = AppOnDarkText,
    secondary = MintSecondary,
    onSecondary = AppOnDarkText,
    tertiary = MintAccent,
    background = MintBackground,
    onBackground = AppOnLight,
    surface = MintSurface,
    onSurface = AppOnLight
)

private val SkyColors = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = AppOnDarkText,
    secondary = SkySecondary,
    onSecondary = AppOnDarkText,
    tertiary = SkyAccent,
    background = SkyBackground,
    onBackground = AppOnLight,
    surface = SkySurface,
    onSurface = AppOnLight
)

private val PeachColors = lightColorScheme(
    primary = PeachPrimary,
    onPrimary = AppOnDarkText,
    secondary = PeachSecondary,
    onSecondary = AppOnDarkText,
    tertiary = PeachAccent,
    background = PeachBackground,
    onBackground = AppOnLight,
    surface = PeachSurface,
    onSurface = AppOnLight
)

private val DarkColors = darkColorScheme(
    primary = SandPrimary,
    secondary = SandSecondary,
    tertiary = SandAccent
)

@Composable
fun FindThingsTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit
) {
    val scheme = when (appTheme) {
        AppTheme.SAND -> SandColors
        AppTheme.MINT -> MintColors
        AppTheme.SKY -> SkyColors
        AppTheme.PEACH -> PeachColors
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
