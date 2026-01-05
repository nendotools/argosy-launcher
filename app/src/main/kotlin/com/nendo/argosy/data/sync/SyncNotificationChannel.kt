package com.nendo.argosy.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object SyncNotificationChannel {
    const val CHANNEL_ID = "sync_service_channel"
    private const val CHANNEL_NAME = "Save Sync"
    private const val CHANNEL_DESCRIPTION = "Shows save sync progress in background"

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
