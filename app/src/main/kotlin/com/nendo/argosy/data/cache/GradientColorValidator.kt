package com.nendo.argosy.data.cache

import android.graphics.BitmapFactory
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GradientColorValidator"

data class ValidationProgress(
    val isProcessing: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0
)

@Singleton
class GradientColorValidator @Inject constructor(
    private val gameDao: GameDao,
    private val gradientColorExtractor: GradientColorExtractor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val priorityQueue = Channel<Long>(Channel.UNLIMITED)
    private val prioritizedIds = mutableSetOf<Long>()

    private val _progress = MutableStateFlow(ValidationProgress())
    val progress: StateFlow<ValidationProgress> = _progress.asStateFlow()

    private var isRunning = false
    private var pendingGames = listOf<Long>()
    private var pendingIndex = 0

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            delay(2000)
            loadPendingGames()
            processQueue()
        }
    }

    fun prioritize(gameIds: List<Long>) {
        scope.launch {
            gameIds.forEach { id ->
                if (id !in prioritizedIds) {
                    prioritizedIds.add(id)
                    priorityQueue.send(id)
                }
            }
        }
    }

    private suspend fun loadPendingGames() {
        val candidates = gameDao.getGamesWithMissingGradientColors()
        pendingGames = candidates.map { it.id }
        pendingIndex = 0
        _progress.value = ValidationProgress(
            isProcessing = pendingGames.isNotEmpty(),
            processedCount = 0,
            totalCount = pendingGames.size
        )
        if (pendingGames.isNotEmpty()) {
            Log.d(TAG, "Found ${pendingGames.size} games needing gradient colors")
        }
    }

    private suspend fun processQueue() {
        while (isRunning) {
            val priorityId = priorityQueue.tryReceive().getOrNull()
            if (priorityId != null) {
                processGame(priorityId)
                continue
            }

            if (pendingIndex < pendingGames.size) {
                val gameId = pendingGames[pendingIndex]
                pendingIndex++
                if (gameId !in prioritizedIds) {
                    processGame(gameId)
                }
                _progress.value = _progress.value.copy(
                    processedCount = pendingIndex
                )
            } else {
                _progress.value = _progress.value.copy(isProcessing = false)
                delay(5000)
                loadPendingGames()
                if (pendingGames.isEmpty()) {
                    delay(60000)
                }
            }
        }
    }

    private suspend fun processGame(gameId: Long) {
        try {
            val candidates = gameDao.getGamesWithMissingGradientColors()
            val candidate = candidates.find { it.id == gameId } ?: return

            val file = File(candidate.coverPath)
            if (!file.exists()) return

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            try {
                val (primary, secondary) = gradientColorExtractor.extractGradientColors(bitmap)
                val colors = gradientColorExtractor.serializeColors(primary, secondary)
                gameDao.updateGradientColors(gameId, colors)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process gradient colors for game $gameId", e)
        }
    }
}
