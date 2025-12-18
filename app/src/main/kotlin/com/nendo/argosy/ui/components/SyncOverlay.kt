package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.ui.theme.Dimens

private enum class StepState {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

private data class SyncStep(
    val label: String,
    val state: StepState
)

@Composable
fun SyncOverlay(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
    gameTitle: String? = null
) {
    val isVisible = syncProgress != null &&
        syncProgress != SyncProgress.Idle &&
        syncProgress != SyncProgress.Skipped

    val isActiveSync = syncProgress != null && syncProgress !is SyncProgress.Error

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val displayRotation = if (isActiveSync) rotation else 0f

    val channelName by remember(syncProgress) {
        derivedStateOf { syncProgress?.displayChannelName }
    }

    val statusMessage by remember(syncProgress) {
        derivedStateOf { syncProgress?.statusMessage ?: "" }
    }

    val steps by remember(syncProgress) {
        derivedStateOf { buildSteps(syncProgress) }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(56.dp)
                        .rotate(displayRotation)
                )

                Spacer(modifier = Modifier.height(Dimens.spacingLg))

                Text(
                    text = buildAnnotatedString {
                        append("Channel: ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                            append(channelName ?: "Latest")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                AnimatedContent(
                    targetState = statusMessage,
                    transitionSpec = {
                        (slideInVertically { -it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { it / 2 } + fadeOut(tween(150)))
                    },
                    label = "syncStatus"
                ) { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (gameTitle != null) {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    Text(
                        text = gameTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.spacingXl))

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEach { step ->
                        SyncStepRow(step = step)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStepRow(step: SyncStep) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Dimens.spacingMd)
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            when (step.state) {
                StepState.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                StepState.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StepState.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                StepState.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = when (step.state) {
                StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                StepState.IN_PROGRESS -> MaterialTheme.colorScheme.onSurface
                StepState.SUCCESS -> MaterialTheme.colorScheme.onSurfaceVariant
                StepState.FAILED -> MaterialTheme.colorScheme.error
            }
        )
    }
}

private fun buildSteps(progress: SyncProgress?): List<SyncStep> {
    return when (progress) {
        is SyncProgress.PreLaunch -> buildPreLaunchSteps(progress)
        is SyncProgress.PostSession -> buildPostSessionSteps(progress)
        else -> emptyList()
    }
}

private fun buildPreLaunchSteps(progress: SyncProgress.PreLaunch): List<SyncStep> {
    val checkingSave = when (progress) {
        is SyncProgress.PreLaunch.CheckingSave -> when (progress.found) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        else -> StepState.SUCCESS
    }

    val connecting = when (progress) {
        is SyncProgress.PreLaunch.CheckingSave -> StepState.PENDING
        is SyncProgress.PreLaunch.Connecting -> when (progress.success) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        else -> StepState.SUCCESS
    }

    val downloading = when (progress) {
        is SyncProgress.PreLaunch.CheckingSave,
        is SyncProgress.PreLaunch.Connecting -> StepState.PENDING
        is SyncProgress.PreLaunch.Downloading -> when (progress.success) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        else -> StepState.SUCCESS
    }

    val writing = when (progress) {
        is SyncProgress.PreLaunch.CheckingSave,
        is SyncProgress.PreLaunch.Connecting,
        is SyncProgress.PreLaunch.Downloading -> StepState.PENDING
        is SyncProgress.PreLaunch.Writing -> when (progress.success) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        else -> StepState.SUCCESS
    }

    val launching = when (progress) {
        is SyncProgress.PreLaunch.Launching -> StepState.IN_PROGRESS
        else -> StepState.PENDING
    }

    return listOf(
        SyncStep("Save found", checkingSave),
        SyncStep("Connected", connecting),
        SyncStep("Downloaded", downloading),
        SyncStep("Written to disk", writing),
        SyncStep("Launching game", launching)
    )
}

private fun buildPostSessionSteps(progress: SyncProgress.PostSession): List<SyncStep> {
    val checkingSave = when (progress) {
        is SyncProgress.PostSession.CheckingSave -> when (progress.found) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        else -> StepState.SUCCESS
    }

    val connecting = when (progress) {
        is SyncProgress.PostSession.CheckingSave -> StepState.PENDING
        is SyncProgress.PostSession.Connecting -> when (progress.success) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        else -> StepState.SUCCESS
    }

    val uploading = when (progress) {
        is SyncProgress.PostSession.CheckingSave,
        is SyncProgress.PostSession.Connecting -> StepState.PENDING
        is SyncProgress.PostSession.Uploading -> when (progress.success) {
            null -> StepState.IN_PROGRESS
            true -> StepState.SUCCESS
            false -> StepState.FAILED
        }
        SyncProgress.PostSession.Complete -> StepState.SUCCESS
    }

    val complete = when (progress) {
        SyncProgress.PostSession.Complete -> StepState.SUCCESS
        is SyncProgress.PostSession.Uploading -> if (progress.success == true) StepState.IN_PROGRESS else StepState.PENDING
        else -> StepState.PENDING
    }

    return listOf(
        SyncStep("Save found", checkingSave),
        SyncStep("Connected", connecting),
        SyncStep("Uploaded", uploading),
        SyncStep("Sync complete", complete)
    )
}

@Deprecated(
    "Use SyncOverlay with SyncProgress instead",
    ReplaceWith("SyncOverlay(syncProgress, modifier, gameTitle)")
)
@Composable
fun SyncOverlay(
    syncState: SyncState?,
    modifier: Modifier = Modifier,
    gameTitle: String? = null
) {
    val isVisible = syncState != null && syncState != SyncState.Idle
    val isActiveSync = syncState != null && syncState !is SyncState.Error

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val displayRotation = if (isActiveSync) rotation else 0f

    val message = when (syncState) {
        is SyncState.Error -> syncState.message
        else -> "Syncing..."
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .rotate(displayRotation)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (gameTitle != null) {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    Text(
                        text = gameTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
