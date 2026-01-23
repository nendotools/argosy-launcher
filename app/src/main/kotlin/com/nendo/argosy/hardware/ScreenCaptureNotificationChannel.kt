package com.nendo.argosy.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object ScreenCaptureNotificationChannel {
    const val CHANNEL_ID = "screen_capture_channel"
    const val NOTIFICATION_ID = 0x4000
    private const val CHANNEL_NAME = "Ambient LED Screen Capture"
    private const val CHANNEL_DESCRIPTION = "Samples screen colors for ambient lighting"

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
