package com.dsc.pdfmaster.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

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

private val FallbackDynamicDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0)
)

private val FallbackDynamicLightColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E)
)

@Composable
fun PdfMasterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dynamicColor -> {
            if (darkTheme) FallbackDynamicDarkColorScheme else FallbackDynamicLightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = Typography,
        content = content
    )
}
