package com.nendo.argosy.ui.screens.firstrun

import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FirstRunStep {
    WELCOME,
    ROMM_LOGIN,
    ROMM_SUCCESS,
    ROM_PATH,
    SAVE_SYNC,
    COMPLETE
}

data class FirstRunUiState(
    val currentStep: FirstRunStep = FirstRunStep.WELCOME,
    val focusedIndex: Int = 0,
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommPassword: String = "",
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val rommGameCount: Int = 0,
    val rommPlatformCount: Int = 0,
    val romStoragePath: String? = null,
    val folderSelected: Boolean = false,
    val launchFolderPicker: Boolean = false,
    val saveSyncEnabled: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val rommFocusField: Int? = null
)

@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstRunUiState())
    val uiState: StateFlow<FirstRunUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROM_PATH
                FirstRunStep.ROM_PATH -> FirstRunStep.SAVE_SYNC
                FirstRunStep.SAVE_SYNC -> FirstRunStep.COMPLETE
                FirstRunStep.COMPLETE -> FirstRunStep.COMPLETE
            }
            state.copy(currentStep = nextStep, focusedIndex = 0)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROM_PATH -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.SAVE_SYNC -> FirstRunStep.ROM_PATH
                FirstRunStep.COMPLETE -> FirstRunStep.SAVE_SYNC
            }
            state.copy(currentStep = prevStep, focusedIndex = 0)
        }
    }

    fun getMaxFocusIndex(): Int {
        val state = _uiState.value
        return when (state.currentStep) {
            FirstRunStep.WELCOME -> 0
            FirstRunStep.ROMM_LOGIN -> 4
            FirstRunStep.ROMM_SUCCESS -> 0
            FirstRunStep.ROM_PATH -> {
                when {
                    !state.hasStoragePermission -> 0
                    state.folderSelected -> 1
                    else -> 0
                }
            }
            FirstRunStep.SAVE_SYNC -> 1
            FirstRunStep.COMPLETE -> 0
        }
    }

    fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        val maxIndex = getMaxFocusIndex()
        val newIndex = (state.focusedIndex + delta).coerceIn(0, maxIndex)
        if (newIndex == state.focusedIndex) return false
        _uiState.update { it.copy(focusedIndex = newIndex) }
        return true
    }

    fun setRommFocusField(index: Int) {
        _uiState.update { it.copy(rommFocusField = index) }
    }

    fun clearRommFocusField() {
        _uiState.update { it.copy(rommFocusField = null) }
    }

    fun setRommUrl(url: String) {
        _uiState.update { it.copy(rommUrl = url, connectionError = null) }
    }

    fun setRommUsername(username: String) {
        _uiState.update { it.copy(rommUsername = username, connectionError = null) }
    }

    fun setRommPassword(password: String) {
        _uiState.update { it.copy(rommPassword = password, connectionError = null) }
    }

    fun connectToRomm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }

            val url = _uiState.value.rommUrl
            val username = _uiState.value.rommUsername
            val password = _uiState.value.rommPassword

            val connectResult = romMRepository.connect(url)
            if (connectResult is RomMResult.Error) {
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        connectionError = "Could not connect to server: ${connectResult.message}"
                    )
                }
                return@launch
            }

            val loginResult = romMRepository.login(username, password)
            when (loginResult) {
                is RomMResult.Success -> {
                    when (val summary = romMRepository.getLibrarySummary()) {
                        is RomMResult.Success -> {
                            val (platformCount, gameCount) = summary.data
                            _uiState.update { state ->
                                state.copy(
                                    isConnecting = false,
                                    currentStep = FirstRunStep.ROMM_SUCCESS,
                                    rommGameCount = gameCount,
                                    rommPlatformCount = platformCount
                                )
                            }
                        }
                        is RomMResult.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    isConnecting = false,
                                    connectionError = "Failed to fetch library: ${summary.message}"
                                )
                            }
                        }
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isConnecting = false,
                            connectionError = "Login failed: ${loginResult.message}"
                        )
                    }
                }
            }
        }
    }

    fun openFolderPicker() {
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun clearFolderPickerFlag() {
        _uiState.update { it.copy(launchFolderPicker = false) }
    }

    fun setStoragePath(path: String) {
        _uiState.update {
            it.copy(
                romStoragePath = path,
                folderSelected = true
            )
        }
    }

    fun proceedFromRomPath() {
        val state = _uiState.value
        if (state.hasStoragePermission && state.folderSelected) {
            nextStep()
        }
    }

    fun enableSaveSync() {
        _uiState.update { it.copy(saveSyncEnabled = true) }
        nextStep()
    }

    fun skipSaveSync() {
        _uiState.update { it.copy(saveSyncEnabled = false) }
        nextStep()
    }

    fun completeSetup() {
        val state = _uiState.value
        if (!state.hasStoragePermission || !state.folderSelected) return

        viewModelScope.launch {
            state.romStoragePath?.let { path ->
                preferencesRepository.setRomStoragePath(path)
            }
            preferencesRepository.setSaveSyncEnabled(state.saveSyncEnabled)
            preferencesRepository.setFirstRunComplete()
        }
    }

    fun checkStoragePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        _uiState.update { it.copy(hasStoragePermission = hasPermission) }
    }

    fun onStoragePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = granted) }
    }

    fun handleConfirm(
        onRequestPermission: () -> Unit,
        onChooseFolder: () -> Unit
    ) {
        val state = _uiState.value
        when (state.currentStep) {
            FirstRunStep.WELCOME -> nextStep()
            FirstRunStep.ROMM_LOGIN -> {
                when (state.focusedIndex) {
                    in 0..2 -> setRommFocusField(state.focusedIndex)
                    3 -> if (!state.isConnecting && state.rommUrl.isNotBlank()) connectToRomm()
                    4 -> previousStep()
                }
            }
            FirstRunStep.ROMM_SUCCESS -> nextStep()
            FirstRunStep.ROM_PATH -> {
                if (!state.hasStoragePermission) {
                    onRequestPermission()
                } else if (state.folderSelected) {
                    if (state.focusedIndex == 0) proceedFromRomPath()
                    else onChooseFolder()
                } else {
                    onChooseFolder()
                }
            }
            FirstRunStep.SAVE_SYNC -> {
                if (state.focusedIndex == 0) enableSaveSync() else skipSaveSync()
            }
            FirstRunStep.COMPLETE -> {}
        }
    }

    fun createInputHandler(
        onComplete: () -> Unit,
        onRequestPermission: () -> Unit,
        onChooseFolder: () -> Unit
    ) = FirstRunInputHandler(this, onComplete, onRequestPermission, onChooseFolder)
}
