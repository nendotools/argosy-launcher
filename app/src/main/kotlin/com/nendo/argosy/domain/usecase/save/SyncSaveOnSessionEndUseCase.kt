package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import java.io.File
import java.time.Instant
import javax.inject.Inject

class SyncSaveOnSessionEndUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector
) {
    sealed class Result {
        data object Uploaded : Result()
        data object Queued : Result()
        data class Conflict(
            val gameId: Long,
            val localTimestamp: Instant,
            val serverTimestamp: Instant
        ) : Result()
        data object NoSaveFound : Result()
        data object NotConfigured : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(gameId: Long, emulatorPackage: String): Result {
        val game = gameDao.getById(gameId) ?: return Result.Error("Game not found")
        if (game.rommId == null) return Result.NotConfigured

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorId = resolveEmulatorId(emulatorConfig?.packageName, emulatorPackage)
            ?: return Result.NotConfigured

        val savePath = saveSyncRepository.discoverSavePath(emulatorId, game.title, game.platformId)
            ?: return Result.NoSaveFound

        val saveFile = File(savePath)
        if (!saveFile.exists()) return Result.NoSaveFound

        val localModified = Instant.ofEpochMilli(saveFile.lastModified())

        saveSyncRepository.createOrUpdateSyncEntity(
            gameId = gameId,
            rommId = game.rommId,
            emulatorId = emulatorId,
            localPath = savePath,
            localUpdatedAt = localModified
        )

        return when (val syncResult = saveSyncRepository.uploadSave(gameId, emulatorId)) {
            is SaveSyncResult.Success -> Result.Uploaded
            is SaveSyncResult.Conflict -> Result.Conflict(
                syncResult.gameId,
                syncResult.localTimestamp,
                syncResult.serverTimestamp
            )
            is SaveSyncResult.Error -> {
                saveSyncRepository.queueUpload(gameId, emulatorId, savePath)
                Result.Queued
            }
            is SaveSyncResult.NoSaveFound -> Result.NoSaveFound
            is SaveSyncResult.NotConfigured -> Result.NotConfigured
        }
    }

    private fun resolveEmulatorId(configPackage: String?, launchPackage: String): String? {
        val packageToResolve = configPackage ?: launchPackage

        EmulatorRegistry.getByPackage(packageToResolve)?.let { return it.id }

        val family = EmulatorRegistry.findFamilyForPackage(packageToResolve)
        if (family != null) {
            return family.baseId
        }

        return emulatorDetector.getByPackage(packageToResolve)?.id
    }
}
