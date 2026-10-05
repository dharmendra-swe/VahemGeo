package com.vahemgeo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val VahemGeoColorScheme = darkColorScheme(
    primary = ElectricLime,
    onPrimary = DarkCanvas,
    primaryContainer = CyberTeal,
    onPrimaryContainer = ElectricLime,
    secondary = NeonChartreuse,
    onSecondary = DarkCanvas,
    background = DarkCanvas,
    onBackground = PolarWhite,
    surface = SurfaceCard,
    onSurface = PolarWhite,
    surfaceVariant = CyberTeal.copy(alpha = 0.5f),
    onSurfaceVariant = MutedSage,
    outline = ElectricLime.copy(alpha = 0.35f),
    error = WarningRed,
    onError = PolarWhite
)

@Composable
fun VahemGeoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VahemGeoColorScheme,
        typography = Typography,
        content = content
    )
}