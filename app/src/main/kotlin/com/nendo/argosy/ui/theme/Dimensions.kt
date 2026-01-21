package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Dimens {
    private val scale: Float
        @Composable get() = LocalUiScale.current.scale

    // Spacing - SCALED
    val spacingXs: Dp @Composable get() = 4.dp * scale
    val spacingSm: Dp @Composable get() = 8.dp * scale
    val spacingMd: Dp @Composable get() = 16.dp * scale
    val spacingLg: Dp @Composable get() = 24.dp * scale
    val spacingXl: Dp @Composable get() = 32.dp * scale
    val spacingXxl: Dp @Composable get() = 48.dp * scale

    // Radius - SCALED
    val radiusSm: Dp @Composable get() = 4.dp * scale
    val radiusMd: Dp @Composable get() = 8.dp * scale
    val radiusLg: Dp @Composable get() = 12.dp * scale
    val radiusXl: Dp @Composable get() = 16.dp * scale

    // Component sizing - SCALED
    val gameCardWidth: Dp @Composable get() = 180.dp * scale
    val gameCardHeight: Dp @Composable get() = 240.dp * scale
    val settingsItemMinHeight: Dp @Composable get() = 56.dp * scale

    // Icons - SCALED
    val iconXs: Dp @Composable get() = 14.dp * scale
    val iconSm: Dp @Composable get() = 18.dp * scale
    val iconMd: Dp @Composable get() = 24.dp * scale
    val iconLg: Dp @Composable get() = 32.dp * scale
    val iconXl: Dp @Composable get() = 48.dp * scale

    // Layout - SCALED
    val headerHeight: Dp @Composable get() = 72.dp * scale
    val headerHeightLg: Dp @Composable get() = 140.dp * scale
    val footerHeight: Dp @Composable get() = 50.dp * scale
    val modalWidth: Dp @Composable get() = 350.dp * scale
    val modalWidthLg: Dp @Composable get() = 450.dp * scale
    val modalWidthXl: Dp @Composable get() = 500.dp * scale

    // Borders - FIXED (too thin to scale)
    val borderThin = 1.dp
    val borderMedium = 2.dp
    val borderThick = 2.dp

    // Elevation - FIXED (shadows shouldn't scale)
    val elevationNone = 0.dp
    val elevationSm = 2.dp
    val elevationMd = 4.dp
    val elevationLg = 8.dp
    val elevationFocused = 16.dp
}
