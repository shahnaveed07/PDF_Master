package com.dsc.pdfmastertool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DscPrimaryCyan,
    onPrimary = DscPureBlack,
    primaryContainer = DscCyanContainer,
    onPrimaryContainer = DscOnCyanContainer,
    secondary = DscSecondaryPurple,
    onSecondary = DscWhite,
    secondaryContainer = DscPurpleContainer,
    onSecondaryContainer = DscOnPurpleContainer,
    background = DscPureBlack,
    onBackground = DscTextPrimary,
    surface = DscSurfacePrimary,
    onSurface = DscTextPrimary,
    surfaceVariant = DscSurfaceSecondary,
    onSurfaceVariant = DscTextSecondary,
    outline = DscDivider,
    outlineVariant = DscDivider
)

private val LightColorScheme = lightColorScheme(
    primary = DscPrimaryCyanLight,
    onPrimary = DscOnPrimaryLight,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = DscSecondaryPurpleLight,
    onSecondary = DscWhite,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    background = DscBackgroundLight,
    onBackground = DscTextPrimaryLight,
    surface = DscSurfaceLight,
    onSurface = DscTextPrimaryLight,
    surfaceVariant = DscSurfaceVariantLight,
    onSurfaceVariant = DscTextSecondaryLight,
    outline = DscDividerLight,
    outlineVariant = DscDividerLight
)

@Composable
fun PdfMasterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Dynamic color is explicitly not used to strictly protect DarkSkullCorporation visual identity
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


