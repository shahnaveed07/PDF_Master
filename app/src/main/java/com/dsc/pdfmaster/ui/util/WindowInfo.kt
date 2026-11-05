package com.dsc.pdfmaster.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsc.pdfmaster.ui.adaptive.WindowHeightSizeClass
import com.dsc.pdfmaster.ui.adaptive.WindowSizeClass
import com.dsc.pdfmaster.ui.adaptive.WindowWidthSizeClass

@Immutable
data class WindowInfo(
    val widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    val heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
    val widthDp: Dp = 360.dp,
    val heightDp: Dp = 640.dp
) {
    val isCompact: Boolean get() = widthSizeClass == WindowWidthSizeClass.Compact
    val isMedium: Boolean get() = widthSizeClass == WindowWidthSizeClass.Medium
    val isExpanded: Boolean get() = widthSizeClass == WindowWidthSizeClass.Expanded

    val isTwoPaneSupported: Boolean get() = isMedium || isExpanded

    companion object {
        fun fromWindowSizeClass(
            sizeClass: WindowSizeClass,
            widthDp: Dp = 360.dp,
            heightDp: Dp = 640.dp
        ): WindowInfo = WindowInfo(
            widthSizeClass = sizeClass.widthSizeClass,
            heightSizeClass = sizeClass.heightSizeClass,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}

@Composable
fun rememberWindowInfo(): WindowInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp
    val widthClass = WindowWidthSizeClass.fromWidthDp(widthDp)
    val heightClass = WindowHeightSizeClass.fromHeightDp(heightDp)
    return WindowInfo(
        widthSizeClass = widthClass,
        heightSizeClass = heightClass,
        widthDp = widthDp,
        heightDp = heightDp
    )
}
