package com.nendo.argosy.ui.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticPattern {
    FOCUS_CHANGE,
    SELECTION,
    BOUNDARY_HIT,
    ERROR,
    INTENSITY_PREVIEW
}

@Singleton
class HapticFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }

    private var enabled = true
    private var intensity = Intensity.MEDIUM
    private val hasAmplitudeControl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.hasAmplitudeControl() == true
    } else false

    enum class Intensity { LOW, MEDIUM, HIGH }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setIntensity(amplitude: Int) {
        intensity = when {
            amplitude <= 80 -> Intensity.LOW
            amplitude <= 180 -> Intensity.MEDIUM
            else -> Intensity.HIGH
        }
    }

    fun vibrate(pattern: HapticPattern) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = when (intensity) {
                Intensity.LOW -> 140
                Intensity.MEDIUM -> 220
                Intensity.HIGH -> 255
            }

            val effect = if (hasAmplitudeControl) {
                // Device supports amplitude - use it for real intensity control
                when (pattern) {
                    HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(100L, amplitude)
                    HapticPattern.SELECTION -> VibrationEffect.createOneShot(150L, amplitude)
                    HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(150L, 255)
                    HapticPattern.ERROR -> VibrationEffect.createOneShot(240L, 255)
                    HapticPattern.INTENSITY_PREVIEW -> VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 100, 500, 100, 500),
                        intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                        -1
                    )
                }
            } else {
                // No amplitude control - vary duration instead
                val duration = when (intensity) {
                    Intensity.LOW -> 45L
                    Intensity.MEDIUM -> 90L
                    Intensity.HIGH -> 150L
                }
                when (pattern) {
                    HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.SELECTION -> VibrationEffect.createOneShot(duration + 45, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.ERROR -> VibrationEffect.createOneShot(300L, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.INTENSITY_PREVIEW -> VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 100, 500, 100, 500),
                        -1
                    )
                }
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val duration = when (intensity) {
                Intensity.LOW -> 45L
                Intensity.MEDIUM -> 90L
                Intensity.HIGH -> 150L
            }
            when (pattern) {
                HapticPattern.FOCUS_CHANGE -> vibrator.vibrate(duration)
                HapticPattern.SELECTION -> vibrator.vibrate(duration + 45)
                HapticPattern.BOUNDARY_HIT -> vibrator.vibrate(180L)
                HapticPattern.ERROR -> vibrator.vibrate(300L)
                HapticPattern.INTENSITY_PREVIEW -> vibrator.vibrate(longArrayOf(0, 500, 100, 500, 100, 500), -1)
            }
        }
    }
}
