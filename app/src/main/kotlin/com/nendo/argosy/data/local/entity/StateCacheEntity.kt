package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "state_cache",
    indices = [
        Index("gameId"),
        Index("cachedAt"),
        Index(
            value = ["gameId", "emulatorId", "slotNumber", "channelName", "coreId"],
            unique = true,
            name = "index_state_cache_game_emu_slot_channel_core"
        ),
        Index("rommSaveId"),
        Index("syncStatus"),
        Index(value = ["gameId", "emulatorId"], name = "index_state_cache_gameId_emulatorId")
    ]
)
data class StateCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val platformSlug: String,
    val emulatorId: String,
    val slotNumber: Int,
    val channelName: String? = null,
    val cachedAt: Instant,
    val stateSize: Long,
    val cachePath: String,
    val screenshotPath: String? = null,
    val coreId: String? = null,
    val coreVersion: String? = null,
    val isLocked: Boolean = false,
    val note: String? = null,
    val rommSaveId: Long? = null,
    val syncStatus: String? = null,
    val serverUpdatedAt: Instant? = null,
    val lastUploadedHash: String? = null
) {
    companion object {
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_LOCAL_NEWER = "LOCAL_NEWER"
        const val STATUS_SERVER_NEWER = "SERVER_NEWER"
        const val STATUS_PENDING_UPLOAD = "PENDING_UPLOAD"
    }
}
