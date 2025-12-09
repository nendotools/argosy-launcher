package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pending_save_sync",
    indices = [
        Index("gameId"),
        Index("createdAt")
    ]
)
data class PendingSaveSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommId: Long,
    val emulatorId: String,
    val localSavePath: String,
    val action: String,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now()
) {
    companion object {
        const val ACTION_UPLOAD = "UPLOAD"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_DOWNLOAD = "DOWNLOAD"
    }
}
