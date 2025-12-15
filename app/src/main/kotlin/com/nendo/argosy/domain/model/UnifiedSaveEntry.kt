package com.nendo.argosy.domain.model

import java.time.Instant

data class UnifiedSaveEntry(
    val localCacheId: Long? = null,
    val serverSaveId: Long? = null,
    val timestamp: Instant,
    val size: Long,
    val channelName: String? = null,
    val source: Source,
    val serverFileName: String? = null,
    val isLatest: Boolean = false
) {
    enum class Source { LOCAL, SERVER, BOTH }

    val isChannel: Boolean get() = channelName != null
    val canBecomeChannel: Boolean get() = localCacheId != null && !isChannel
    val canDeleteFromServer: Boolean get() = serverSaveId != null

    val displayName: String
        get() = channelName ?: serverFileName ?: formatTimestamp()

    private fun formatTimestamp(): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
        return formatter.format(timestamp)
    }
}
