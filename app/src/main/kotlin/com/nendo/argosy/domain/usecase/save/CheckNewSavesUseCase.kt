package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.repository.SaveSyncRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CheckNewSavesUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val platformDao: PlatformDao
) {
    data class Result(
        val newSavesCount: Int,
        val platformsChecked: Int
    )

    suspend operator fun invoke(): Result {
        val platforms = platformDao.observePlatformsWithGames().first()
        var totalNew = 0

        for (platform in platforms) {
            val platformId = platform.id.toLongOrNull() ?: continue
            val newSaves = saveSyncRepository.checkForServerUpdates(platformId)
            totalNew += newSaves.size
        }

        return Result(totalNew, platforms.size)
    }
}
