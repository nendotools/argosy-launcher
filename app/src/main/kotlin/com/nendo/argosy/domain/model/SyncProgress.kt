package com.nendo.argosy.domain.model

sealed class SyncProgress {
    data object Idle : SyncProgress()

    sealed class PreLaunch : SyncProgress() {
        abstract val channelName: String?

        data class CheckingSave(
            override val channelName: String?,
            val found: Boolean? = null
        ) : PreLaunch()

        data class Connecting(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PreLaunch()

        data class Downloading(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PreLaunch()

        data class Writing(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PreLaunch()

        data class Launching(
            override val channelName: String?
        ) : PreLaunch()
    }

    sealed class PostSession : SyncProgress() {
        abstract val channelName: String?

        data class CheckingSave(
            override val channelName: String?,
            val found: Boolean? = null
        ) : PostSession()

        data class Connecting(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PostSession()

        data class Uploading(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PostSession()

        data object Complete : PostSession() {
            override val channelName: String? = null
        }
    }

    data class Error(val message: String) : SyncProgress()
    data object Skipped : SyncProgress()

    val displayChannelName: String?
        get() = when (this) {
            is PreLaunch -> channelName
            is PostSession -> channelName
            else -> null
        }

    val statusMessage: String
        get() = when (this) {
            is Idle -> ""
            is PreLaunch.CheckingSave -> when (found) {
                null -> "Checking save file..."
                true -> "Save found"
                false -> "No save file"
            }
            is PreLaunch.Connecting -> when (success) {
                null -> "Connecting..."
                true -> "Connected"
                false -> "Connection failed"
            }
            is PreLaunch.Downloading -> when (success) {
                null -> "Downloading..."
                true -> "Download complete"
                false -> "Download failed"
            }
            is PreLaunch.Writing -> when (success) {
                null -> "Writing save..."
                true -> "Save written"
                false -> "Write failed"
            }
            is PreLaunch.Launching -> "Launching game..."
            is PostSession.CheckingSave -> when (found) {
                null -> "Checking save file..."
                true -> "Save found"
                false -> "No save file"
            }
            is PostSession.Connecting -> when (success) {
                null -> "Connecting..."
                true -> "Connected"
                false -> "Connection failed"
            }
            is PostSession.Uploading -> when (success) {
                null -> "Uploading..."
                true -> "Upload complete"
                false -> "Upload queued"
            }
            is PostSession.Complete -> "Sync complete"
            is Error -> message
            is Skipped -> "Sync skipped"
        }
}
