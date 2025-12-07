package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.AnimationSpeed
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UiDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.data.update.AppInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.MigrateStorageUseCase
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

enum class SettingsSection {
    MAIN,
    SERVER,
    SYNC_SETTINGS,
    STORAGE,
    DISPLAY,
    CONTROLS,
    EMULATORS,
    ABOUT
}

enum class ConnectionStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    NOT_CONFIGURED
}

data class PlatformEmulatorConfig(
    val platform: PlatformEntity,
    val selectedEmulator: String?,
    val isUserConfigured: Boolean,
    val availableEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef> = emptyList()
) {
    val hasInstalledEmulators: Boolean get() = availableEmulators.isNotEmpty()
}

data class EmulatorPickerInfo(
    val platformId: String,
    val platformName: String,
    val installedEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef>,
    val selectedEmulatorName: String?
)

data class DisplayState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val uiDensity: UiDensity = UiDensity.NORMAL
)

data class ControlsState(
    val hapticEnabled: Boolean = true,
    val nintendoButtonLayout: Boolean = false,
    val swapStartSelect: Boolean = false
)

data class EmulatorState(
    val platforms: List<PlatformEmulatorConfig> = emptyList(),
    val installedEmulators: List<InstalledEmulator> = emptyList(),
    val canAutoAssign: Boolean = false,
    val showEmulatorPicker: Boolean = false,
    val emulatorPickerInfo: EmulatorPickerInfo? = null,
    val emulatorPickerFocusIndex: Int = 0
)

data class StorageState(
    val romStoragePath: String = "",
    val downloadedGamesSize: Long = 0,
    val downloadedGamesCount: Int = 0,
    val maxConcurrentDownloads: Int = 1
)

data class ServerState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommVersion: String? = null,
    val lastRommSync: java.time.Instant? = null,
    val rommConfiguring: Boolean = false,
    val rommConfigUrl: String = "",
    val rommConfigUsername: String = "",
    val rommConfigPassword: String = "",
    val rommConnecting: Boolean = false,
    val rommConfigError: String? = null,
    val rommFocusField: Int? = null,
    val syncScreenshotsEnabled: Boolean = false
)

data class SyncSettingsState(
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val showRegionPicker: Boolean = false,
    val regionPickerFocusIndex: Int = 0,
    val totalGames: Int = 0,
    val totalPlatforms: Int = 0
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val hasChecked: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val readyToInstall: Boolean = false
)

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.MAIN,
    val focusedIndex: Int = 0,
    val colorFocusIndex: Int = 0,
    val display: DisplayState = DisplayState(),
    val controls: ControlsState = ControlsState(),
    val emulators: EmulatorState = EmulatorState(),
    val server: ServerState = ServerState(),
    val storage: StorageState = StorageState(),
    val syncSettings: SyncSettingsState = SyncSettingsState(),
    val launchFolderPicker: Boolean = false,
    val showMigrationDialog: Boolean = false,
    val pendingStoragePath: String? = null,
    val isMigrating: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val updateCheck: UpdateCheckState = UpdateCheckState()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val imageCacheManager: ImageCacheManager,
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val migrateStorageUseCase: MigrateStorageUseCase,
    private val updateRepository: UpdateRepository,
    private val appInstaller: AppInstaller
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val _downloadUpdateEvent = MutableSharedFlow<Unit>()
    val downloadUpdateEvent: SharedFlow<Unit> = _downloadUpdateEvent.asSharedFlow()

    val imageCacheProgress: StateFlow<ImageCacheProgress> = imageCacheManager.progress

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val installedEmulators = emulatorDetector.detectEmulators()
            val platforms = platformDao.observePlatformsWithGames().first()

            val installedPackages = installedEmulators.map { it.def.packageName }.toSet()

            val platformConfigs = platforms.map { platform ->
                val defaultConfig = emulatorConfigDao.getDefaultForPlatform(platform.id)
                val available = installedEmulators.filter { platform.id in it.def.supportedPlatforms }
                val isUserConfigured = defaultConfig != null

                val recommended = EmulatorRegistry.getRecommendedEmulators()[platform.id] ?: emptyList()
                val downloadable = recommended
                    .mapNotNull { EmulatorRegistry.getById(it) }
                    .filter { it.packageName !in installedPackages && it.downloadUrl != null }

                PlatformEmulatorConfig(
                    platform = platform,
                    selectedEmulator = defaultConfig?.displayName,
                    isUserConfigured = isUserConfigured,
                    availableEmulators = available,
                    downloadableEmulators = downloadable
                )
            }

            val canAutoAssign = platformConfigs.any { !it.isUserConfigured && it.availableEmulators.isNotEmpty() }

            val connectionState = romMRepository.connectionState.value
            val connectionStatus = when {
                prefs.rommBaseUrl.isNullOrBlank() -> ConnectionStatus.NOT_CONFIGURED
                connectionState is RomMRepository.ConnectionState.Connected -> ConnectionStatus.ONLINE
                else -> ConnectionStatus.OFFLINE
            }
            val rommVersion = (connectionState as? RomMRepository.ConnectionState.Connected)?.version

            val downloadedSize = gameRepository.getDownloadedGamesSize()
            val downloadedCount = gameRepository.getDownloadedGamesCount()

            _uiState.update { state ->
                state.copy(
                    display = state.display.copy(
                        themeMode = prefs.themeMode,
                        primaryColor = prefs.primaryColor,
                        animationSpeed = prefs.animationSpeed,
                        uiDensity = prefs.uiDensity
                    ),
                    controls = state.controls.copy(
                        hapticEnabled = prefs.hapticEnabled,
                        nintendoButtonLayout = prefs.nintendoButtonLayout,
                        swapStartSelect = prefs.swapStartSelect
                    ),
                    emulators = state.emulators.copy(
                        platforms = platformConfigs,
                        installedEmulators = installedEmulators,
                        canAutoAssign = canAutoAssign
                    ),
                    server = state.server.copy(
                        connectionStatus = connectionStatus,
                        rommUrl = prefs.rommBaseUrl ?: "",
                        rommUsername = prefs.rommUsername ?: "",
                        rommVersion = rommVersion,
                        lastRommSync = prefs.lastRommSync,
                        syncScreenshotsEnabled = prefs.syncScreenshotsEnabled
                    ),
                    storage = state.storage.copy(
                        romStoragePath = prefs.romStoragePath ?: "",
                        downloadedGamesSize = downloadedSize,
                        downloadedGamesCount = downloadedCount,
                        maxConcurrentDownloads = prefs.maxConcurrentDownloads
                    ),
                    syncSettings = state.syncSettings.copy(
                        syncFilters = prefs.syncFilters,
                        totalPlatforms = platforms.count { it.gameCount > 0 },
                        totalGames = platforms.sumOf { it.gameCount }
                    )
                )
            }
        }
    }

    fun autoAssignAllEmulators() {
        viewModelScope.launch {
            for (config in _uiState.value.emulators.platforms) {
                if (!config.isUserConfigured && config.availableEmulators.isNotEmpty()) {
                    val preferred = emulatorDetector.getPreferredEmulator(config.platform.id)
                    if (preferred != null) {
                        setPlatformEmulator(config.platform.id, preferred)
                    }
                }
            }
        }
    }

    fun refreshEmulators() {
        viewModelScope.launch {
            val previousCounts = _uiState.value.emulators.platforms.associate {
                it.platform.id to it.availableEmulators.size
            }

            loadSettings()

            val currentPlatforms = _uiState.value.emulators.platforms
            for (config in currentPlatforms) {
                val prevCount = previousCounts[config.platform.id] ?: 0
                val currentCount = config.availableEmulators.size

                if (prevCount == 0 && currentCount > 0 && !config.isUserConfigured) {
                    val preferred = emulatorDetector.getPreferredEmulator(config.platform.id)
                    if (preferred != null) {
                        setPlatformEmulator(config.platform.id, preferred)
                    }
                }
            }
        }
    }

    fun showEmulatorPicker(config: PlatformEmulatorConfig) {
        if (config.availableEmulators.isEmpty() && config.downloadableEmulators.isEmpty()) return
        _uiState.update {
            it.copy(
                emulators = it.emulators.copy(
                    showEmulatorPicker = true,
                    emulatorPickerInfo = EmulatorPickerInfo(
                        platformId = config.platform.id,
                        platformName = config.platform.name,
                        installedEmulators = config.availableEmulators,
                        downloadableEmulators = config.downloadableEmulators,
                        selectedEmulatorName = config.selectedEmulator
                    ),
                    emulatorPickerFocusIndex = 0
                )
            )
        }
    }

    fun dismissEmulatorPicker() {
        _uiState.update {
            it.copy(
                emulators = it.emulators.copy(
                    showEmulatorPicker = false,
                    emulatorPickerInfo = null,
                    emulatorPickerFocusIndex = 0
                )
            )
        }
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        _uiState.update { state ->
            val info = state.emulators.emulatorPickerInfo ?: return@update state
            val hasInstalled = info.installedEmulators.isNotEmpty()
            val totalItems = (if (hasInstalled) 1 else 0) + info.installedEmulators.size + info.downloadableEmulators.size
            val maxIndex = (totalItems - 1).coerceAtLeast(0)
            val newIndex = (state.emulators.emulatorPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(emulators = state.emulators.copy(emulatorPickerFocusIndex = newIndex))
        }
    }

    fun confirmEmulatorPickerSelection() {
        viewModelScope.launch {
            val state = _uiState.value
            val info = state.emulators.emulatorPickerInfo ?: return@launch
            val index = state.emulators.emulatorPickerFocusIndex
            val hasInstalled = info.installedEmulators.isNotEmpty()

            if (hasInstalled) {
                when {
                    index == 0 -> {
                        setPlatformEmulator(info.platformId, null)
                        dismissEmulatorPicker()
                    }
                    index <= info.installedEmulators.size -> {
                        val emulator = info.installedEmulators[index - 1]
                        setPlatformEmulator(info.platformId, emulator)
                        dismissEmulatorPicker()
                    }
                    else -> {
                        val downloadIndex = index - 1 - info.installedEmulators.size
                        val emulator = info.downloadableEmulators.getOrNull(downloadIndex)
                        emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
                    }
                }
            } else {
                val emulator = info.downloadableEmulators.getOrNull(index)
                emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
            }
        }
    }

    fun navigateToSection(section: SettingsSection) {
        _uiState.update { it.copy(currentSection = section, focusedIndex = 0) }
        when (section) {
            SettingsSection.EMULATORS -> refreshEmulators()
            SettingsSection.SERVER -> checkRommConnection()
            SettingsSection.SYNC_SETTINGS -> loadLibrarySettings()
            else -> {}
        }
    }

    private fun loadLibrarySettings() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            _uiState.update { it.copy(syncSettings = it.syncSettings.copy(syncFilters = prefs.syncFilters)) }
        }
    }

    fun checkRommConnection() {
        val url = _uiState.value.server.rommUrl
        if (url.isBlank()) {
            _uiState.update { it.copy(server = it.server.copy(connectionStatus = ConnectionStatus.NOT_CONFIGURED)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(server = it.server.copy(connectionStatus = ConnectionStatus.CHECKING)) }
            try {
                val result = romMRepository.getLibrarySummary()
                val status = if (result is RomMResult.Success) {
                    ConnectionStatus.ONLINE
                } else {
                    ConnectionStatus.OFFLINE
                }
                _uiState.update { it.copy(server = it.server.copy(connectionStatus = status)) }
            } catch (e: Exception) {
                Log.e(TAG, "checkRommConnection: failed", e)
                _uiState.update { it.copy(server = it.server.copy(connectionStatus = ConnectionStatus.OFFLINE)) }
            }
        }
    }

    fun navigateBack(): Boolean {
        val state = _uiState.value
        return when {
            state.syncSettings.showRegionPicker -> {
                dismissRegionPicker()
                true
            }
            state.emulators.showEmulatorPicker -> {
                dismissEmulatorPicker()
                true
            }
            state.server.rommConfiguring -> {
                cancelRommConfig()
                true
            }
            state.currentSection == SettingsSection.SYNC_SETTINGS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = 1) }
                true
            }
            state.currentSection != SettingsSection.MAIN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = 0) }
                true
            }
            else -> false
        }
    }

    private val colorCount = 7

    fun moveFocus(delta: Int) {
        if (_uiState.value.syncSettings.showRegionPicker) {
            moveRegionPickerFocus(delta)
            return
        }
        if (_uiState.value.emulators.showEmulatorPicker) {
            moveEmulatorPickerFocus(delta)
            return
        }
        _uiState.update { state ->
            val maxIndex = when (state.currentSection) {
                SettingsSection.MAIN -> 5
                SettingsSection.SERVER -> when {
                    state.server.rommConfiguring -> 4
                    state.server.connectionStatus == ConnectionStatus.ONLINE ||
                    state.server.connectionStatus == ConnectionStatus.OFFLINE -> 2
                    else -> 0
                }
                SettingsSection.SYNC_SETTINGS -> 7
                SettingsSection.STORAGE -> 2
                SettingsSection.DISPLAY -> 3
                SettingsSection.CONTROLS -> 2
                SettingsSection.EMULATORS -> {
                    val platformCount = state.emulators.platforms.size
                    val autoAssignOffset = if (state.emulators.canAutoAssign) 1 else 0
                    (platformCount + autoAssignOffset - 1).coerceAtLeast(0)
                }
                SettingsSection.ABOUT -> 3
            }
            state.copy(focusedIndex = (state.focusedIndex + delta).coerceIn(0, maxIndex))
        }
    }

    fun moveColorFocus(delta: Int) {
        _uiState.update { state ->
            state.copy(colorFocusIndex = (state.colorFocusIndex + delta).coerceIn(0, colorCount - 1))
        }
    }

    fun selectFocusedColor() {
        val colors = listOf<Int?>(
            null,
            0xFF9575CD.toInt(),
            0xFF4DB6AC.toInt(),
            0xFFFFB74D.toInt(),
            0xFF81C784.toInt(),
            0xFFF06292.toInt(),
            0xFF64B5F6.toInt()
        )
        val color = colors.getOrNull(_uiState.value.colorFocusIndex)
        setPrimaryColor(color)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            _uiState.update { it.copy(display = it.display.copy(themeMode = mode)) }
        }
    }

    fun setPrimaryColor(color: Int?) {
        viewModelScope.launch {
            preferencesRepository.setCustomColors(color, null, null)
            _uiState.update { it.copy(display = it.display.copy(primaryColor = color)) }
        }
    }

    fun setAnimationSpeed(speed: AnimationSpeed) {
        viewModelScope.launch {
            preferencesRepository.setAnimationSpeed(speed)
            _uiState.update { it.copy(display = it.display.copy(animationSpeed = speed)) }
        }
    }

    fun setUiDensity(density: UiDensity) {
        viewModelScope.launch {
            preferencesRepository.setUiDensity(density)
            _uiState.update { it.copy(display = it.display.copy(uiDensity = density)) }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHapticEnabled(enabled)
            _uiState.update { it.copy(controls = it.controls.copy(hapticEnabled = enabled)) }
        }
    }

    fun setNintendoButtonLayout(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNintendoButtonLayout(enabled)
            _uiState.update { it.copy(controls = it.controls.copy(nintendoButtonLayout = enabled)) }
        }
    }

    fun setSwapStartSelect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSwapStartSelect(enabled)
            _uiState.update { it.copy(controls = it.controls.copy(swapStartSelect = enabled)) }
        }
    }

    fun showRegionPicker() {
        _uiState.update { it.copy(syncSettings = it.syncSettings.copy(showRegionPicker = true, regionPickerFocusIndex = 0)) }
    }

    fun dismissRegionPicker() {
        _uiState.update { it.copy(syncSettings = it.syncSettings.copy(showRegionPicker = false, regionPickerFocusIndex = 0)) }
    }

    fun moveRegionPickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = SyncFilterPreferences.ALL_KNOWN_REGIONS.size - 1
            val newIndex = (state.syncSettings.regionPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(syncSettings = state.syncSettings.copy(regionPickerFocusIndex = newIndex))
        }
    }

    fun confirmRegionPickerSelection() {
        val state = _uiState.value
        val region = SyncFilterPreferences.ALL_KNOWN_REGIONS.getOrNull(state.syncSettings.regionPickerFocusIndex) ?: return
        toggleRegion(region)
    }

    fun toggleRegion(region: String) {
        viewModelScope.launch {
            val current = _uiState.value.syncSettings.syncFilters.enabledRegions
            val updated = if (region in current) current - region else current + region
            preferencesRepository.setSyncFilterRegions(updated)
            _uiState.update {
                it.copy(syncSettings = it.syncSettings.copy(syncFilters = it.syncSettings.syncFilters.copy(enabledRegions = updated)))
            }
        }
    }

    fun toggleRegionMode() {
        viewModelScope.launch {
            val current = _uiState.value.syncSettings.syncFilters.regionMode
            val next = when (current) {
                RegionFilterMode.INCLUDE -> RegionFilterMode.EXCLUDE
                RegionFilterMode.EXCLUDE -> RegionFilterMode.INCLUDE
            }
            preferencesRepository.setSyncFilterRegionMode(next)
            _uiState.update {
                it.copy(syncSettings = it.syncSettings.copy(syncFilters = it.syncSettings.syncFilters.copy(regionMode = next)))
            }
        }
    }

    fun setExcludeBeta(exclude: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterExcludeBeta(exclude)
            _uiState.update {
                it.copy(syncSettings = it.syncSettings.copy(syncFilters = it.syncSettings.syncFilters.copy(excludeBeta = exclude)))
            }
        }
    }

    fun setExcludePrototype(exclude: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterExcludePrototype(exclude)
            _uiState.update {
                it.copy(syncSettings = it.syncSettings.copy(syncFilters = it.syncSettings.syncFilters.copy(excludePrototype = exclude)))
            }
        }
    }

    fun setExcludeDemo(exclude: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterExcludeDemo(exclude)
            _uiState.update {
                it.copy(syncSettings = it.syncSettings.copy(syncFilters = it.syncSettings.syncFilters.copy(excludeDemo = exclude)))
            }
        }
    }

    fun setDeleteOrphans(delete: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterDeleteOrphans(delete)
            _uiState.update {
                it.copy(syncSettings = it.syncSettings.copy(syncFilters = it.syncSettings.syncFilters.copy(deleteOrphans = delete)))
            }
        }
    }

    fun toggleSyncScreenshots() {
        viewModelScope.launch {
            val newValue = !_uiState.value.server.syncScreenshotsEnabled
            preferencesRepository.setSyncScreenshotsEnabled(newValue)
            _uiState.update { it.copy(server = it.server.copy(syncScreenshotsEnabled = newValue)) }
            if (newValue) {
                imageCacheManager.resumePendingScreenshotCache()
            }
        }
    }

    fun cycleMaxConcurrentDownloads() {
        viewModelScope.launch {
            val current = _uiState.value.storage.maxConcurrentDownloads
            val next = if (current >= 5) 1 else current + 1
            preferencesRepository.setMaxConcurrentDownloads(next)
            _uiState.update { it.copy(storage = it.storage.copy(maxConcurrentDownloads = next)) }
        }
    }

    private fun adjustMaxConcurrentDownloads(delta: Int) {
        viewModelScope.launch {
            val current = _uiState.value.storage.maxConcurrentDownloads
            val next = (current + delta).coerceIn(1, 5)
            if (next != current) {
                preferencesRepository.setMaxConcurrentDownloads(next)
                _uiState.update { it.copy(storage = it.storage.copy(maxConcurrentDownloads = next)) }
            }
        }
    }

    fun openFolderPicker() {
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun clearFolderPickerFlag() {
        _uiState.update { it.copy(launchFolderPicker = false) }
    }

    fun setStoragePath(uriString: String) {
        val currentState = _uiState.value
        if (currentState.storage.downloadedGamesCount > 0 && currentState.storage.romStoragePath.isNotBlank()) {
            _uiState.update {
                it.copy(
                    showMigrationDialog = true,
                    pendingStoragePath = uriString
                )
            }
        } else {
            applyStoragePath(uriString)
        }
    }

    fun confirmMigration() {
        val pendingPath = _uiState.value.pendingStoragePath ?: return
        _uiState.update { it.copy(showMigrationDialog = false) }
        migrateDownloads(pendingPath)
    }

    fun cancelMigration() {
        _uiState.update {
            it.copy(
                showMigrationDialog = false,
                pendingStoragePath = null
            )
        }
    }

    fun skipMigration() {
        val pendingPath = _uiState.value.pendingStoragePath ?: return
        _uiState.update { it.copy(showMigrationDialog = false) }
        applyStoragePath(pendingPath)
    }

    private fun applyStoragePath(uriString: String) {
        viewModelScope.launch {
            preferencesRepository.setRomStoragePath(uriString)
            _uiState.update {
                it.copy(
                    storage = it.storage.copy(romStoragePath = uriString),
                    pendingStoragePath = null
                )
            }
        }
    }

    private fun migrateDownloads(newPath: String) {
        viewModelScope.launch {
            val oldPath = _uiState.value.storage.romStoragePath
            _uiState.update { it.copy(isMigrating = true, pendingStoragePath = null) }

            migrateStorageUseCase(oldPath, newPath)

            _uiState.update { it.copy(storage = it.storage.copy(romStoragePath = newPath), isMigrating = false) }
            refreshCollectionStats()
        }
    }

    private fun refreshCollectionStats() {
        viewModelScope.launch {
            val downloadedSize = gameRepository.getDownloadedGamesSize()
            val downloadedCount = gameRepository.getDownloadedGamesCount()
            _uiState.update {
                it.copy(
                    storage = it.storage.copy(
                        downloadedGamesSize = downloadedSize,
                        downloadedGamesCount = downloadedCount
                    )
                )
            }
        }
    }

    fun setPlatformEmulator(platformId: String, emulator: InstalledEmulator?) {
        viewModelScope.launch {
            configureEmulatorUseCase.setForPlatform(platformId, emulator)
            loadSettings()
        }
    }

    fun setRomStoragePath(path: String) {
        viewModelScope.launch {
            preferencesRepository.setRomStoragePath(path)
            _uiState.update { it.copy(storage = it.storage.copy(romStoragePath = path)) }
        }
    }

    fun syncRomm() {
        viewModelScope.launch {
            when (val result = syncLibraryUseCase()) {
                is SyncLibraryResult.Error -> notificationManager.showError(result.message)
                is SyncLibraryResult.Success -> loadSettings()
            }
        }
    }

    fun checkForUpdates() {
        if (BuildConfig.DEBUG) return

        viewModelScope.launch {
            _uiState.update { it.copy(updateCheck = it.updateCheck.copy(isChecking = true, error = null)) }

            when (val state = updateRepository.checkForUpdates()) {
                is UpdateState.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            updateCheck = UpdateCheckState(
                                isChecking = false,
                                updateAvailable = true,
                                latestVersion = state.release.tagName,
                                downloadUrl = state.apkAsset.downloadUrl
                            )
                        )
                    }
                }
                is UpdateState.UpToDate -> {
                    _uiState.update {
                        it.copy(updateCheck = UpdateCheckState(isChecking = false, hasChecked = true, updateAvailable = false))
                    }
                }
                is UpdateState.Error -> {
                    _uiState.update {
                        it.copy(updateCheck = UpdateCheckState(isChecking = false, error = state.message))
                    }
                }
                else -> {
                    _uiState.update { it.copy(updateCheck = UpdateCheckState(isChecking = false)) }
                }
            }
        }
    }

    fun downloadAndInstallUpdate(context: android.content.Context) {
        val state = _uiState.value.updateCheck
        val url = state.downloadUrl ?: return
        val version = state.latestVersion ?: return

        if (state.isDownloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(updateCheck = it.updateCheck.copy(isDownloading = true, downloadProgress = 0, error = null)) }

            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(context, url, version) { progress ->
                        _uiState.update { it.copy(updateCheck = it.updateCheck.copy(downloadProgress = progress)) }
                    }
                }

                _uiState.update {
                    it.copy(updateCheck = it.updateCheck.copy(isDownloading = false, readyToInstall = true))
                }

                appInstaller.installApk(context, apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download update", e)
                _uiState.update {
                    it.copy(updateCheck = it.updateCheck.copy(isDownloading = false, error = e.message ?: "Download failed"))
                }
            }
        }
    }

    private fun downloadApk(
        context: android.content.Context,
        url: String,
        version: String,
        onProgress: (Int) -> Unit
    ): File {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response")
        val contentLength = body.contentLength()
        val apkFile = appInstaller.getApkCacheFile(context, version)

        apkFile.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val progress = ((bytesRead * 100) / contentLength).toInt()
                        onProgress(progress)
                    }
                }
            }
        }

        return apkFile
    }

    fun startRommConfig() {
        _uiState.update {
            it.copy(
                server = it.server.copy(
                    rommConfiguring = true,
                    rommConfigUrl = it.server.rommUrl,
                    rommConfigUsername = it.server.rommUsername,
                    rommConfigPassword = "",
                    rommConfigError = null
                ),
                focusedIndex = 0
            )
        }
    }

    fun cancelRommConfig() {
        _uiState.update {
            it.copy(
                server = it.server.copy(
                    rommConfiguring = false,
                    rommConfigUrl = "",
                    rommConfigUsername = "",
                    rommConfigPassword = "",
                    rommConfigError = null
                ),
                focusedIndex = 0
            )
        }
    }

    fun setRommConfigUrl(url: String) {
        _uiState.update { it.copy(server = it.server.copy(rommConfigUrl = url)) }
    }

    fun setRommConfigUsername(username: String) {
        _uiState.update { it.copy(server = it.server.copy(rommConfigUsername = username)) }
    }

    fun setRommConfigPassword(password: String) {
        _uiState.update { it.copy(server = it.server.copy(rommConfigPassword = password)) }
    }

    fun clearRommFocusField() {
        _uiState.update { it.copy(server = it.server.copy(rommFocusField = null)) }
    }

    fun connectToRomm() {
        val state = _uiState.value
        if (state.server.rommConfigUrl.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(server = it.server.copy(rommConnecting = true, rommConfigError = null)) }

            when (val result = romMRepository.connect(state.server.rommConfigUrl)) {
                is RomMResult.Success -> {
                    if (state.server.rommConfigUsername.isNotBlank() && state.server.rommConfigPassword.isNotBlank()) {
                        when (val loginResult = romMRepository.login(state.server.rommConfigUsername, state.server.rommConfigPassword)) {
                            is RomMResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        server = it.server.copy(
                                            rommConnecting = false,
                                            rommConfiguring = false,
                                            connectionStatus = ConnectionStatus.ONLINE,
                                            rommUrl = state.server.rommConfigUrl,
                                            rommUsername = state.server.rommConfigUsername
                                        )
                                    )
                                }
                                loadSettings()
                            }
                            is RomMResult.Error -> {
                                _uiState.update {
                                    it.copy(server = it.server.copy(rommConnecting = false, rommConfigError = loginResult.message))
                                }
                            }
                        }
                    } else {
                        _uiState.update {
                            it.copy(server = it.server.copy(rommConnecting = false, rommConfigError = "Username and password required"))
                        }
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update {
                        it.copy(server = it.server.copy(rommConnecting = false, rommConfigError = result.message))
                    }
                }
            }
        }
    }

    fun handleConfirm() {
        val state = _uiState.value
        when (state.currentSection) {
            SettingsSection.MAIN -> {
                val section = when (state.focusedIndex) {
                    0 -> SettingsSection.SERVER
                    1 -> SettingsSection.STORAGE
                    2 -> SettingsSection.DISPLAY
                    3 -> SettingsSection.CONTROLS
                    4 -> SettingsSection.EMULATORS
                    5 -> SettingsSection.ABOUT
                    else -> null
                }
                section?.let { navigateToSection(it) }
            }
            SettingsSection.SERVER -> {
                when {
                    state.server.rommConfiguring -> when (state.focusedIndex) {
                        0, 1, 2 -> _uiState.update { it.copy(server = it.server.copy(rommFocusField = state.focusedIndex)) }
                        3 -> connectToRomm()
                        4 -> cancelRommConfig()
                    }
                    else -> when (state.focusedIndex) {
                        0 -> startRommConfig()
                        1 -> navigateToSection(SettingsSection.SYNC_SETTINGS)
                        2 -> if (state.server.connectionStatus == ConnectionStatus.ONLINE) syncRomm()
                    }
                }
            }
            SettingsSection.SYNC_SETTINGS -> {
                when (state.focusedIndex) {
                    0 -> {} // Sync Images - info only
                    1 -> toggleSyncScreenshots()
                    2 -> showRegionPicker()
                    3 -> toggleRegionMode()
                    4 -> setExcludeBeta(!state.syncSettings.syncFilters.excludeBeta)
                    5 -> setExcludePrototype(!state.syncSettings.syncFilters.excludePrototype)
                    6 -> setExcludeDemo(!state.syncSettings.syncFilters.excludeDemo)
                    7 -> setDeleteOrphans(!state.syncSettings.syncFilters.deleteOrphans)
                }
            }
            SettingsSection.STORAGE -> {
                when (state.focusedIndex) {
                    0 -> openFolderPicker()
                    1 -> {} // Max Active Downloads - handled by left/right
                    2 -> {} // Downloaded info - display only
                }
            }
            SettingsSection.DISPLAY -> {
                when (state.focusedIndex) {
                    0 -> {
                        val next = when (state.display.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        setThemeMode(next)
                    }
                    2 -> {
                        val next = when (state.display.animationSpeed) {
                            AnimationSpeed.SLOW -> AnimationSpeed.NORMAL
                            AnimationSpeed.NORMAL -> AnimationSpeed.FAST
                            AnimationSpeed.FAST -> AnimationSpeed.OFF
                            AnimationSpeed.OFF -> AnimationSpeed.SLOW
                        }
                        setAnimationSpeed(next)
                    }
                    3 -> {
                        val next = when (state.display.uiDensity) {
                            UiDensity.COMPACT -> UiDensity.NORMAL
                            UiDensity.NORMAL -> UiDensity.SPACIOUS
                            UiDensity.SPACIOUS -> UiDensity.COMPACT
                        }
                        setUiDensity(next)
                    }
                }
            }
            SettingsSection.CONTROLS -> {
                when (state.focusedIndex) {
                    0 -> setHapticEnabled(!state.controls.hapticEnabled)
                    1 -> setNintendoButtonLayout(!state.controls.nintendoButtonLayout)
                    2 -> setSwapStartSelect(!state.controls.swapStartSelect)
                }
            }
            SettingsSection.EMULATORS -> {
                val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
                if (state.emulators.canAutoAssign && state.focusedIndex == 0) {
                    autoAssignAllEmulators()
                } else {
                    val platformIndex = state.focusedIndex - focusOffset
                    val config = state.emulators.platforms.getOrNull(platformIndex)
                    if (config != null) {
                        showEmulatorPicker(config)
                    }
                }
            }
            SettingsSection.ABOUT -> {
                when (state.focusedIndex) {
                    3 -> {
                        if (state.updateCheck.updateAvailable) {
                            viewModelScope.launch { _downloadUpdateEvent.emit(Unit) }
                        } else {
                            checkForUpdates()
                        }
                    }
                }
            }
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            moveFocus(-1)
            return true
        }

        override fun onDown(): Boolean {
            moveFocus(1)
            return true
        }

        override fun onLeft(): Boolean {
            val state = _uiState.value
            if (state.currentSection == SettingsSection.DISPLAY && state.focusedIndex == 1) {
                moveColorFocus(-1)
                return true
            }
            if (state.currentSection == SettingsSection.STORAGE && state.focusedIndex == 1) {
                adjustMaxConcurrentDownloads(-1)
                return true
            }
            return false
        }

        override fun onRight(): Boolean {
            val state = _uiState.value
            if (state.currentSection == SettingsSection.DISPLAY && state.focusedIndex == 1) {
                moveColorFocus(1)
                return true
            }
            if (state.currentSection == SettingsSection.STORAGE && state.focusedIndex == 1) {
                adjustMaxConcurrentDownloads(1)
                return true
            }
            return false
        }

        override fun onConfirm(): Boolean {
            val state = _uiState.value
            if (state.syncSettings.showRegionPicker) {
                confirmRegionPickerSelection()
                return true
            }
            if (state.emulators.showEmulatorPicker) {
                confirmEmulatorPickerSelection()
                return true
            }
            if (state.currentSection == SettingsSection.DISPLAY && state.focusedIndex == 1) {
                selectFocusedColor()
                return true
            }
            handleConfirm()
            return true
        }

        override fun onBack(): Boolean {
            return if (!navigateBack()) {
                onBack()
                true
            } else {
                true
            }
        }

        override fun onMenu(): Boolean = false
    }
}
