package com.nuitcode.daytesk.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DayteskColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DayteskColors.PrimaryLight,
    onPrimaryContainer = DayteskColors.Primary,
    secondary = DayteskColors.Warning,
    onSecondary = Color.White,
    secondaryContainer = DayteskColors.WarningLight,
    onSecondaryContainer = DayteskColors.Warning,
    tertiary = DayteskColors.Success,
    onTertiary = Color.White,
    tertiaryContainer = DayteskColors.SuccessLight,
    onTertiaryContainer = DayteskColors.Success,
    error = DayteskColors.Urgent,
    onError = Color.White,
    errorContainer = DayteskColors.UrgentLight,
    onErrorContainer = DayteskColors.Urgent,
    background = DayteskColors.Background,
    onBackground = DayteskColors.TextPrimary,
    surface = DayteskColors.Surface,
    onSurface = DayteskColors.TextPrimary,
    surfaceVariant = DayteskColors.Divider,
    onSurfaceVariant = DayteskColors.TextSecondary,
    outline = DayteskColors.Border,
    outlineVariant = DayteskColors.Divider,
)

private val DarkColorScheme = darkColorScheme(
    primary = DayteskDarkColors.Primary,
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = DayteskDarkColors.PrimaryLight,
    onPrimaryContainer = DayteskDarkColors.Primary,
    secondary = DayteskDarkColors.Warning,
    onSecondary = Color(0xFF1A1A2E),
    secondaryContainer = DayteskDarkColors.WarningLight,
    onSecondaryContainer = DayteskDarkColors.Warning,
    tertiary = DayteskDarkColors.Success,
    onTertiary = Color(0xFF1A1A2E),
    tertiaryContainer = DayteskDarkColors.SuccessLight,
    onTertiaryContainer = DayteskDarkColors.Success,
    error = DayteskDarkColors.Urgent,
    onError = Color.White,
    errorContainer = DayteskDarkColors.UrgentLight,
    onErrorContainer = DayteskDarkColors.Urgent,
    background = DayteskDarkColors.Background,
    onBackground = DayteskDarkColors.TextPrimary,
    surface = DayteskDarkColors.Surface,
    onSurface = DayteskDarkColors.TextPrimary,
    surfaceVariant = DayteskDarkColors.Divider,
    onSurfaceVariant = DayteskDarkColors.TextSecondary,
    outline = DayteskDarkColors.Border,
    outlineVariant = DayteskDarkColors.Divider,
)

private val DayteskMaterialShapes = Shapes(
    small = DayteskShapes.small,
    medium = DayteskShapes.medium,
    large = DayteskShapes.large,
)

@Composable
fun DayteskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTypography,
        shapes = DayteskMaterialShapes,
        content = content,
    )
}
