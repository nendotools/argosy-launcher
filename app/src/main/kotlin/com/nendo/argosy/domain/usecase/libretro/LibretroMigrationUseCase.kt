package com.nendo.argosy.domain.usecase.libretro

import android.util.Log
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val TAG = "LibretroMigration"

class LibretroMigrationUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val coreManager: LibretroCoreManager
) {
    suspend fun runMigrationIfNeeded(): MigrationResult {
        if (preferencesRepository.isBuiltinMigrationComplete().first()) {
            Log.d(TAG, "Migration already complete, skipping")
            return MigrationResult.AlreadyComplete
        }

        Log.i(TAG, "Starting built-in emulator migration for existing users")

        val platformsWithGames = platformDao.getPlatformsWithGames()
        if (platformsWithGames.isEmpty()) {
            Log.d(TAG, "No platforms with games, marking migration complete")
            preferencesRepository.setBuiltinMigrationComplete()
            return MigrationResult.NoPlatforms
        }

        val supportedPlatformSlugs = platformsWithGames
            .map { it.slug }
            .filter { LibretroCoreRegistry.isPlatformSupported(it) }
            .toSet()

        if (supportedPlatformSlugs.isEmpty()) {
            Log.d(TAG, "No supported platforms found, marking migration complete")
            preferencesRepository.setBuiltinMigrationComplete()
            return MigrationResult.NoSupportedPlatforms
        }

        Log.i(TAG, "Found ${supportedPlatformSlugs.size} supported platforms: $supportedPlatformSlugs")

        val missingCores = coreManager.getMissingCoresForPlatforms(supportedPlatformSlugs)
        val downloadedCores = mutableListOf<String>()
        val failedCores = mutableListOf<String>()

        for (coreInfo in missingCores) {
            Log.i(TAG, "Downloading core: ${coreInfo.displayName}")
            val result = coreManager.downloadCoreById(coreInfo.coreId)
            if (result.isSuccess) {
                downloadedCores.add(coreInfo.displayName)
                Log.i(TAG, "Successfully downloaded ${coreInfo.displayName}")
            } else {
                failedCores.add(coreInfo.displayName)
                Log.e(TAG, "Failed to download ${coreInfo.displayName}", result.exceptionOrNull())
            }
        }

        var platformsSetToBuiltin = 0
        for (platform in platformsWithGames) {
            if (platform.slug !in supportedPlatformSlugs) continue

            val existingConfig = emulatorConfigDao.getDefaultForPlatform(platform.id)

            if (existingConfig == null) {
                Log.i(TAG, "No emulator configured for ${platform.slug}, setting to built-in")
                emulatorConfigDao.insert(
                    EmulatorConfigEntity(
                        platformId = platform.id,
                        gameId = null,
                        packageName = EmulatorRegistry.BUILTIN_PACKAGE,
                        displayName = "Built-in",
                        coreName = null,
                        isDefault = true
                    )
                )
                platformsSetToBuiltin++
            } else {
                Log.d(TAG, "Platform ${platform.slug} already has emulator: ${existingConfig.packageName}")
            }
        }

        preferencesRepository.setBuiltinMigrationComplete()

        Log.i(TAG, "Migration complete: downloaded ${downloadedCores.size} cores, " +
            "failed ${failedCores.size}, set $platformsSetToBuiltin platforms to built-in")

        return MigrationResult.Success(
            coresDownloaded = downloadedCores,
            coresFailed = failedCores,
            platformsSetToBuiltin = platformsSetToBuiltin
        )
    }
}

sealed class MigrationResult {
    data object AlreadyComplete : MigrationResult()
    data object NoPlatforms : MigrationResult()
    data object NoSupportedPlatforms : MigrationResult()
    data class Success(
        val coresDownloaded: List<String>,
        val coresFailed: List<String>,
        val platformsSetToBuiltin: Int
    ) : MigrationResult()
}
