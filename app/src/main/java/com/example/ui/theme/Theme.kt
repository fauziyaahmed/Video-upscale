package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = AmberGold,
    onSecondary = ObsidianBg,
    secondaryContainer = DeepAmberContainer,
    onSecondaryContainer = AmberGold,
    tertiary = MintSuccess,
    onTertiary = ObsidianBg,
    tertiaryContainer = DeepMintContainer,
    onTertiaryContainer = MintSuccess,
    error = CoralError,
    onError = ObsidianBg,
    errorContainer = DeepErrorContainer,
    onErrorContainer = CoralError,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioCardHighlight
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun UpscaleAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = AppTypography,
        shapes = StudioShapes,
        content = content
    )
}
