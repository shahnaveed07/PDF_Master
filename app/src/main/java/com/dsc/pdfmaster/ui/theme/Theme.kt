package com.dsc.pdfmaster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MonoDarkPrimary,
    onPrimary = MonoDarkOnPrimary,
    primaryContainer = MonoDarkPrimaryContainer,
    onPrimaryContainer = MonoDarkOnPrimaryContainer,
    secondary = Color(0xFF808080),
    onSecondary = MonoDarkOnPrimary,
    secondaryContainer = MonoDarkSurfaceElevated,
    onSecondaryContainer = MonoDarkTextPrimary,
    background = MonoDarkBackground,
    onBackground = MonoDarkTextPrimary,
    surface = MonoDarkSurface,
    onSurface = MonoDarkTextPrimary,
    surfaceVariant = MonoDarkSurfaceVariant,
    onSurfaceVariant = MonoDarkTextSecondary,
    outline = MonoDarkOutline,
    outlineVariant = MonoDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = MonoLightPrimary,
    onPrimary = MonoLightOnPrimary,
    primaryContainer = MonoLightPrimaryContainer,
    onPrimaryContainer = MonoLightOnPrimaryContainer,
    secondary = Color(0xFF666666),
    onSecondary = MonoLightOnPrimary,
    secondaryContainer = MonoLightSurfaceElevated,
    onSecondaryContainer = MonoLightTextPrimary,
    background = MonoLightBackground,
    onBackground = MonoLightTextPrimary,
    surface = MonoLightSurface,
    onSurface = MonoLightTextPrimary,
    surfaceVariant = MonoLightSurfaceVariant,
    onSurfaceVariant = MonoLightTextSecondary,
    outline = MonoLightOutline,
    outlineVariant = MonoLightOutline
)

@Composable
fun PdfMasterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
