package com.gmp.offline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GmpLightColors = lightColorScheme(
    primary = SolarGreen,
    onPrimary = Color.White,
    primaryContainer = SolarGreenLight,
    onPrimaryContainer = SolarGreenDark,
    secondary = SolarAmber,
    onSecondary = SolarGreenDark,
    secondaryContainer = SolarAmber,
    onSecondaryContainer = SolarGreenDark,
    tertiary = SolarSky,
    onTertiary = Color.White,
    background = SolarBackground,
    onBackground = SolarGreenDark,
    surface = SolarSurface,
    onSurface = SolarGreenDark,
    surfaceVariant = SolarGreenLight,
    onSurfaceVariant = SolarOnSurfaceVariant,
    error = SolarError,
    onError = Color.White,
)

/**
 * Tema visual de GMP — Gestión Montajes Pro (app de gestión de montajes de
 * paneles solares). Paleta pensada para transmitir energía renovable/limpia:
 * verde principal + acento dorado solar + azul cielo en fondos degradados.
 */
@Composable
fun GmpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GmpLightColors,
        typography = GmpTypography,
        content = content,
    )
}
