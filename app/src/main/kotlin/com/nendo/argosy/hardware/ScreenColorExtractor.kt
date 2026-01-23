package com.nendo.argosy.hardware

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.luminance

object ScreenColorExtractor {

    fun extract(bitmap: Bitmap): Pair<SideColors, SideColors> {
        val width = bitmap.width
        val height = bitmap.height
        val midX = width / 2

        val leftColors = sampleRegion(bitmap, 0, 0, midX, height)
        val rightColors = sampleRegion(bitmap, midX, 0, width, height)

        return Pair(
            extractSideColors(leftColors),
            extractSideColors(rightColors)
        )
    }

    private fun sampleRegion(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): List<Color> {
        val colors = mutableListOf<Color>()
        val stepX = (right - left) / 4
        val stepY = (bottom - top) / 4

        for (x in 1..3) {
            for (y in 1..3) {
                val px = (left + x * stepX).coerceIn(0, bitmap.width - 1)
                val py = (top + y * stepY).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(px, py)
                colors.add(Color(pixel))
            }
        }
        return colors
    }

    private fun extractSideColors(samples: List<Color>): SideColors {
        val sorted = samples.sortedBy { it.toArgb().luminance }

        return SideColors(
            low = sorted[sorted.size / 4],
            mid = sorted[sorted.size / 2],
            high = sorted[sorted.size * 3 / 4]
        )
    }

    private fun Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
