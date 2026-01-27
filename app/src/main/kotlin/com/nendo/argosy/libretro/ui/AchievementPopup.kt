package com.nendo.argosy.libretro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

data class AchievementUnlock(
    val id: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val badgeUrl: String?,
    val isHardcore: Boolean
)

private val bronzeColor = Color(0xFFCD7F32)
private val goldColor = Color(0xFFFFD700)
private val goldDark = Color(0xFFB8860B)
private val bronzeDark = Color(0xFF8B4513)

@Composable
fun AchievementPopup(
    achievement: AchievementUnlock?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember(achievement) { mutableStateOf(achievement != null) }

    LaunchedEffect(achievement) {
        if (achievement != null) {
            visible = true
            delay(4000L)
            visible = false
            delay(300L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible && achievement != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        achievement?.let { unlock ->
            AchievementPopupContent(unlock)
        }
    }
}

@Composable
private fun AchievementPopupContent(achievement: AchievementUnlock) {
    val primaryColor = if (achievement.isHardcore) goldColor else bronzeColor
    val darkColor = if (achievement.isHardcore) goldDark else bronzeDark

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            darkColor.copy(alpha = 0.95f),
                            primaryColor.copy(alpha = 0.85f),
                            darkColor.copy(alpha = 0.95f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (achievement.badgeUrl != null) {
                AsyncImage(
                    model = achievement.badgeUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }

            Column {
                Text(
                    text = if (achievement.isHardcore) "HARDCORE ACHIEVEMENT" else "ACHIEVEMENT UNLOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${achievement.points} points",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
