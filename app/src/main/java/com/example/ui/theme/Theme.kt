package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ForgeColorScheme = darkColorScheme(
    primary = ForgeEmerald,
    onPrimary = ForgeOnEmerald,
    primaryContainer = ForgeEmeraldDark.copy(alpha = 0.22f),
    onPrimaryContainer = ForgeEmerald,
    secondary = ForgeCyan,
    onSecondary = ForgeOnCyan,
    secondaryContainer = ForgeCyan.copy(alpha = 0.16f),
    onSecondaryContainer = ForgeCyan,
    tertiary = ForgeAmber,
    onTertiary = ForgeBackground,
    background = ForgeBackground,
    onBackground = ForgeTextPrimary,
    surface = ForgeSurface,
    onSurface = ForgeTextPrimary,
    surfaceVariant = ForgeSurfaceVariant,
    onSurfaceVariant = ForgeTextSecondary,
    surfaceContainerLowest = ForgeBackground,
    surfaceContainerLow = ForgeSurface,
    surfaceContainer = ForgeSurfaceVariant,
    surfaceContainerHigh = ForgeSurfaceHigh,
    outline = ForgeBorder,
    outlineVariant = ForgeBorder.copy(alpha = 0.6f),
    error = ForgeRed,
    onError = ForgeBackground
)

private val ForgeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun APKForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ForgeColorScheme,
        typography = AppTypography,
        shapes = ForgeShapes,
        content = content
    )
}
