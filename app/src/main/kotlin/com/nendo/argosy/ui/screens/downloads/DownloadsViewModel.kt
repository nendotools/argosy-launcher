package com.nendo.argosy.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.InputHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloadState: DownloadQueueState = DownloadQueueState(),
    val focusedGameId: Long? = null,
    val maxActiveSlots: Int = 1
) {
    val activeItems: List<DownloadProgress>
        get() = buildList {
            addAll(downloadState.activeDownloads)
            val remainingSlots = maxActiveSlots - size
            if (remainingSlots > 0) {
                val pausedItems = downloadState.queue.filter { it.state == DownloadState.PAUSED }
                addAll(pausedItems.take(remainingSlots))
            }
        }

    private val activeGameIds: Set<Long>
        get() = activeItems.map { it.gameId }.toSet()

    val queuedItems: List<DownloadProgress>
        get() = downloadState.queue.filter { it.gameId !in activeGameIds }

    val allItems: List<DownloadProgress>
        get() = activeItems + queuedItems

    val focusedIndex: Int
        get() = allItems.indexOfFirst { it.gameId == focusedGameId }.takeIf { it >= 0 } ?: 0

    val focusedItem: DownloadProgress?
        get() = allItems.find { it.gameId == focusedGameId } ?: allItems.firstOrNull()

    val canToggle: Boolean
        get() = focusedItem != null

    val canCancel: Boolean
        get() = focusedItem != null

    val toggleLabel: String
        get() = when (focusedItem?.state) {
            DownloadState.DOWNLOADING -> "Pause"
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED -> "Resume"
            DownloadState.QUEUED -> "Pause"
            else -> "Toggle"
        }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val state: StateFlow<DownloadQueueState> = downloadManager.state

    init {
        viewModelScope.launch {
            combine(
                downloadManager.state,
                preferencesRepository.preferences.map { it.maxConcurrentDownloads }
            ) { downloadState, maxActive ->
                downloadState to maxActive
            }.collect { (downloadState, maxActive) ->
                val currentFocusedId = _uiState.value.focusedGameId
                val allItems = buildList {
                    addAll(downloadState.activeDownloads)
                    addAll(downloadState.queue)
                }

                val newFocusedId = when {
                    allItems.isEmpty() -> null
                    currentFocusedId != null && allItems.any { it.gameId == currentFocusedId } -> currentFocusedId
                    else -> allItems.firstOrNull()?.gameId
                }

                _uiState.value = _uiState.value.copy(
                    downloadState = downloadState,
                    focusedGameId = newFocusedId,
                    maxActiveSlots = maxActive
                )
            }
        }
    }

    private fun moveFocus(delta: Int): Boolean {
        val currentState = _uiState.value
        val items = currentState.allItems
        if (items.isEmpty()) return false

        val currentIndex = currentState.focusedIndex
        val newIndex = (currentIndex + delta).coerceIn(0, items.size - 1)

        if (newIndex != currentIndex) {
            _uiState.value = currentState.copy(focusedGameId = items[newIndex].gameId)
            return true
        }
        return false
    }

    fun toggleFocusedItem() {
        val item = _uiState.value.focusedItem ?: return
        when (item.state) {
            DownloadState.DOWNLOADING -> downloadManager.pauseDownload(item.rommId)
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED ->
                downloadManager.resumeDownload(item.gameId)
            DownloadState.QUEUED -> downloadManager.pauseDownload(item.rommId)
            else -> {}
        }
    }

    fun cancelFocusedItem() {
        val item = _uiState.value.focusedItem ?: return
        downloadManager.cancelDownload(item.rommId)
    }

    fun cancelDownload(rommId: Long) {
        downloadManager.cancelDownload(rommId)
    }

    fun pauseDownload(rommId: Long) {
        downloadManager.pauseDownload(rommId)
    }

    fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): Boolean = moveFocus(-1)
        override fun onDown(): Boolean = moveFocus(1)
        override fun onLeft(): Boolean = false
        override fun onRight(): Boolean = false
        override fun onConfirm(): Boolean {
            if (_uiState.value.canToggle) {
                toggleFocusedItem()
                return true
            }
            return false
        }
        override fun onBack(): Boolean {
            onBack()
            return true
        }
        override fun onMenu(): Boolean = false
        override fun onSecondaryAction(): Boolean {
            if (_uiState.value.canCancel) {
                cancelFocusedItem()
                return true
            }
            return false
        }
    }
}
