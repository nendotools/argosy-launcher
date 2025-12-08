package com.nendo.argosy.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Motion {
    val focusSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 400f)
    val focusSpringDp: AnimationSpec<Dp> = spring(dampingRatio = 0.6f, stiffness = 400f)

    const val scaleFocused = 1.1f
    const val scaleDefault = 1.0f

    const val alphaFocused = 1f
    const val alphaUnfocused = 0.85f

    const val saturationFocused = 1f
    const val saturationUnfocused = 0.3f

    const val glowAlphaFocused = 0.4f
    const val glowAlphaUnfocused = 0f

    const val transitionDebounceMs = 200L

    const val focusScrollDebounceMs = 60L
    const val scrollPaddingPercent = 0.2f

    val blurRadiusModal = 8.dp
    val blurRadiusDrawer = 24.dp
}
