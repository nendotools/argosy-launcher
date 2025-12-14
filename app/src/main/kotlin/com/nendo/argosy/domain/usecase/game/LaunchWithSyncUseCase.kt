package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LaunchWithSyncUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val saveSyncRepository: SaveSyncRepository
) {
    fun invoke(gameId: Long): Flow<SyncState> = flow {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            emit(SyncState.Skipped)
            return@flow
        }

        if (!romMRepository.isConnected()) {
            emit(SyncState.Skipped)
            return@flow
        }

        val game = gameDao.getById(gameId)
        if (game == null || game.rommId == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val preferredEmulator = emulatorDetector.getPreferredEmulator(game.platformId)
        val emulatorPackage = emulatorConfig?.packageName ?: preferredEmulator?.def?.packageName
        if (emulatorPackage == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        val emulatorId = resolveEmulatorId(emulatorPackage)
        if (emulatorId == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        val saveConfig = SavePathRegistry.getConfig(emulatorId)
        if (saveConfig == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        emit(SyncState.CheckingConnection)

        val syncResult = saveSyncRepository.preLaunchSync(gameId, game.rommId, emulatorId)

        when (syncResult) {
            is SaveSyncRepository.PreLaunchSyncResult.NoConnection -> {
                emit(SyncState.Skipped)
            }
            is SaveSyncRepository.PreLaunchSyncResult.NoServerSave -> {
                emit(SyncState.Complete)
            }
            is SaveSyncRepository.PreLaunchSyncResult.LocalIsNewer -> {
                emit(SyncState.Complete)
            }
            is SaveSyncRepository.PreLaunchSyncResult.ServerIsNewer -> {
                emit(SyncState.Downloading)
                val downloadResult = saveSyncRepository.downloadSave(gameId, emulatorId)
                when (downloadResult) {
                    is com.nendo.argosy.data.repository.SaveSyncResult.Success -> {
                        emit(SyncState.Complete)
                    }
                    is com.nendo.argosy.data.repository.SaveSyncResult.Error -> {
                        emit(SyncState.Error(downloadResult.message))
                    }
                    else -> {
                        emit(SyncState.Complete)
                    }
                }
            }
        }
    }

    private fun resolveEmulatorId(packageName: String): String? {
        EmulatorRegistry.getByPackage(packageName)?.let { return it.id }

        val family = EmulatorRegistry.findFamilyForPackage(packageName)
        if (family != null) {
            return family.baseId
        }

        return emulatorDetector.getByPackage(packageName)?.id
    }
}
