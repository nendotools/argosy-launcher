package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant

enum class SaveSyncStatus {
    SYNCED,
    LOCAL_NEWER,
    PENDING_UPLOAD,
    NO_SAVE,
    NOT_CONFIGURED
}

data class SaveStatusInfo(
    val status: SaveSyncStatus,
    val channelName: String?,
    val lastSyncTime: Instant?
)

@Composable
fun SaveStatusRow(
    status: SaveStatusInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = status.status.icon,
            contentDescription = null,
            tint = status.status.color(),
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                    append(status.channelName ?: "Latest")
                }
            },
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = status.status.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        status.lastSyncTime?.let { time ->
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatRelativeTime(time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private val SaveSyncStatus.icon: ImageVector
    get() = when (this) {
        SaveSyncStatus.SYNCED -> Icons.Default.Check
        SaveSyncStatus.LOCAL_NEWER -> Icons.Default.CloudUpload
        SaveSyncStatus.PENDING_UPLOAD -> Icons.Default.Sync
        SaveSyncStatus.NO_SAVE -> Icons.Default.CloudOff
        SaveSyncStatus.NOT_CONFIGURED -> Icons.Default.Error
    }

@Composable
private fun SaveSyncStatus.color() = when (this) {
    SaveSyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
    SaveSyncStatus.LOCAL_NEWER -> MaterialTheme.colorScheme.tertiary
    SaveSyncStatus.PENDING_UPLOAD -> MaterialTheme.colorScheme.secondary
    SaveSyncStatus.NO_SAVE -> MaterialTheme.colorScheme.onSurfaceVariant
    SaveSyncStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.error
}

private val SaveSyncStatus.displayName: String
    get() = when (this) {
        SaveSyncStatus.SYNCED -> "Synced"
        SaveSyncStatus.LOCAL_NEWER -> "Local newer"
        SaveSyncStatus.PENDING_UPLOAD -> "Pending upload"
        SaveSyncStatus.NO_SAVE -> "No save"
        SaveSyncStatus.NOT_CONFIGURED -> "Not configured"
    }

private fun formatRelativeTime(instant: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(instant, now)

    return when {
        duration.toMinutes() < 1 -> "just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()} min ago"
        duration.toHours() < 24 -> "${duration.toHours()} hr ago"
        duration.toDays() < 7 -> "${duration.toDays()} days ago"
        else -> "${duration.toDays() / 7} weeks ago"
    }
}
