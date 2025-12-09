package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveSyncRepository
import java.time.Instant
import javax.inject.Inject

class PreLaunchSyncUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao
) {
    sealed class Result {
        data object Ready : Result()
        data object NoConnection : Result()
        data class ServerNewer(val serverTimestamp: Instant) : Result()
    }

    suspend operator fun invoke(gameId: Long, emulatorPackage: String): Result {
        val game = gameDao.getById(gameId) ?: return Result.Ready
        val rommId = game.rommId ?: return Result.Ready

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorId = emulatorConfig?.packageName?.let { pkg ->
            EmulatorRegistry.getByPackage(pkg)?.id
        } ?: EmulatorRegistry.getByPackage(emulatorPackage)?.id
        ?: return Result.Ready

        return when (val syncResult = saveSyncRepository.preLaunchSync(gameId, rommId, emulatorId)) {
            is SaveSyncRepository.PreLaunchSyncResult.NoConnection -> Result.NoConnection
            is SaveSyncRepository.PreLaunchSyncResult.NoServerSave -> Result.Ready
            is SaveSyncRepository.PreLaunchSyncResult.LocalIsNewer -> Result.Ready
            is SaveSyncRepository.PreLaunchSyncResult.ServerIsNewer -> Result.ServerNewer(syncResult.serverTimestamp)
        }
    }
}
