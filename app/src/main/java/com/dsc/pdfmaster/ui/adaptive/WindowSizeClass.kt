package com.dsc.pdfmaster.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromWidthDp(widthDp: Dp): WindowWidthSizeClass = when {
            widthDp < 600.dp -> Compact
            widthDp < 840.dp -> Medium
            else -> Expanded
        }
    }
}

enum class WindowHeightSizeClass {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromHeightDp(heightDp: Dp): WindowHeightSizeClass = when {
            heightDp < 480.dp -> Compact
            heightDp < 900.dp -> Medium
            else -> Expanded
        }
    }
}

@Immutable
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass
) {
    val isTwoPaneSupported: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Medium ||
            widthSizeClass == WindowWidthSizeClass.Expanded

    val quickActionGridColumns: Int
        get() = when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> 2
            WindowWidthSizeClass.Medium -> 3
            WindowWidthSizeClass.Expanded -> 4
        }

    companion object {
        fun calculateFromSize(widthDp: Dp, heightDp: Dp): WindowSizeClass {
            return WindowSizeClass(
                widthSizeClass = WindowWidthSizeClass.fromWidthDp(widthDp),
                heightSizeClass = WindowHeightSizeClass.fromHeightDp(heightDp)
            )
        }
    }
}

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    return WindowSizeClass.calculateFromSize(
        widthDp = configuration.screenWidthDp.dp,
        heightDp = configuration.screenHeightDp.dp
    )
}
