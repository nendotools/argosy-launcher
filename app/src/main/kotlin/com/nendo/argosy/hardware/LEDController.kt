package com.nendo.argosy.hardware

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

interface LEDController {
    val isAvailable: Boolean

    fun setColor(left: Color, right: Color): Boolean
    fun setColor(color: Color): Boolean = setColor(color, color)
    fun setBrightness(percent: Float): Boolean
    fun setEnabled(left: Boolean, right: Boolean): Boolean
    fun setEnabled(enabled: Boolean): Boolean = setEnabled(enabled, enabled)
}

data class LEDState(
    val leftColor: Color = Color.White,
    val rightColor: Color = Color.White,
    val brightness: Float = 1.0f,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true
)

fun Color.toHexArgb(): String {
    val argb = this.toArgb()
    return String.format("#%08x", argb)
}
