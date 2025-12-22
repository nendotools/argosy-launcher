package com.nendo.argosy.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.ui.theme.ALauncherColors

enum class CompletionStatus(
    val apiValue: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
) {
    INCOMPLETE("incomplete", "Incomplete", Icons.Filled.PlayCircle, ALauncherColors.CompletionPlaying),
    FINISHED("finished", "Finished", Icons.Filled.CheckCircle, ALauncherColors.CompletionBeaten),
    COMPLETED_100("completed_100", "100%", Icons.Filled.EmojiEvents, ALauncherColors.CompletionCompleted),
    RETIRED("retired", "Retired", Icons.Filled.RemoveCircle, Color(0xFF9E9E9E)),
    NEVER_PLAYING("never_playing", "Won't Play", Icons.Filled.Block, Color(0xFF757575));

    companion object {
        fun fromApiValue(value: String?): CompletionStatus? =
            if (value == null) null else entries.find { it.apiValue == value }

        fun cycleNext(current: String?): String {
            val currentStatus = fromApiValue(current) ?: INCOMPLETE
            return entries[(currentStatus.ordinal + 1) % entries.size].apiValue
        }

        fun cyclePrev(current: String?): String {
            val currentStatus = fromApiValue(current) ?: INCOMPLETE
            return entries[(currentStatus.ordinal - 1 + entries.size) % entries.size].apiValue
        }
    }
}
