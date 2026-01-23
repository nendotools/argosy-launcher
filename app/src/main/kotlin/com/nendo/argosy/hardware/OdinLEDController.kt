package com.nendo.argosy.hardware

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.util.PServerExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OdinLEDController @Inject constructor() : LEDController {

    override val isAvailable: Boolean
        get() = PServerExecutor.isAvailable

    override fun setColor(left: Color, right: Color): Boolean {
        val leftHex = left.toHexArgb()
        val rightHex = right.toHexArgb()
        return PServerExecutor.setSystemSetting(KEY_COLOR, "$leftHex,$rightHex")
    }

    override fun setBrightness(percent: Float): Boolean {
        val clamped = percent.coerceIn(0f, 1f)
        return PServerExecutor.setSystemSettingFloat(KEY_BRIGHTNESS, clamped)
    }

    override fun setEnabled(left: Boolean, right: Boolean): Boolean {
        val leftVal = if (left) "1" else "0"
        val rightVal = if (right) "1" else "0"
        return PServerExecutor.setSystemSetting(KEY_ENABLED, "$leftVal,$rightVal")
    }

    fun getState(): LEDState? {
        if (!isAvailable) return null

        val colorStr = PServerExecutor.execute("settings get system $KEY_COLOR")
            .getOrNull() ?: return null
        val brightness = PServerExecutor.getSystemSettingFloat(KEY_BRIGHTNESS, 1.0f)
        val enabledStr = PServerExecutor.execute("settings get system $KEY_ENABLED")
            .getOrNull() ?: "1,1"

        val colors = parseColors(colorStr)
        val enabled = parseEnabled(enabledStr)

        return LEDState(
            leftColor = colors.first,
            rightColor = colors.second,
            brightness = brightness,
            leftEnabled = enabled.first,
            rightEnabled = enabled.second
        )
    }

    private fun parseColors(colorStr: String): Pair<Color, Color> {
        val parts = colorStr.split(",")
        if (parts.size != 2) return Pair(Color.White, Color.White)

        val left = parseHexColor(parts[0].trim())
        val right = parseHexColor(parts[1].trim())
        return Pair(left, right)
    }

    private fun parseHexColor(hex: String): Color {
        return try {
            val cleaned = hex.removePrefix("#")
            val argb = cleaned.toLong(16).toInt()
            Color(argb)
        } catch (e: Exception) {
            Color.White
        }
    }

    private fun parseEnabled(enabledStr: String): Pair<Boolean, Boolean> {
        val parts = enabledStr.split(",")
        if (parts.size != 2) return Pair(true, true)
        return Pair(parts[0].trim() == "1", parts[1].trim() == "1")
    }

    companion object {
        private const val KEY_COLOR = "joystick_led_light_picker_color"
        private const val KEY_BRIGHTNESS = "led_light_brightness_percent"
        private const val KEY_ENABLED = "joystick_light_enabled"
    }
}
