package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.PendingSaveSyncEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SaveSyncRepository"

sealed class SaveSyncResult {
    data object Success : SaveSyncResult()
    data class Conflict(
        val gameId: Long,
        val localTimestamp: Instant,
        val serverTimestamp: Instant
    ) : SaveSyncResult()
    data class Error(val message: String) : SaveSyncResult()
    data object NoSaveFound : SaveSyncResult()
    data object NotConfigured : SaveSyncResult()
}

@Singleton
class SaveSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSaveSyncDao: PendingSaveSyncDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val gameDao: GameDao,
    private val retroArchConfigParser: RetroArchConfigParser
) {
    private var api: RomMApi? = null

    fun setApi(api: RomMApi?) {
        this.api = api
    }

    fun observeNewSavesCount(): Flow<Int> = saveSyncDao.observeNewSavesCount()

    fun observePendingCount(): Flow<Int> = pendingSaveSyncDao.observePendingCount()

    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformId: String,
        romPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (userConfig?.isUserOverride == true) {
            return@withContext findSaveInPath(userConfig.savePathPattern, gameTitle)
        }

        val config = SavePathRegistry.getConfig(emulatorId) ?: return@withContext null

        val paths = if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            val packageName = if (emulatorId == "retroarch_64") "com.retroarch.aarch64" else "com.retroarch"
            val coreName = SavePathRegistry.getRetroArchCore(platformId)
            val contentDir = romPath?.let { File(it).parent }
            retroArchConfigParser.resolveSavePaths(packageName, platformId, coreName, contentDir)
        } else {
            SavePathRegistry.resolvePath(config, platformId)
        }

        for (basePath in paths) {
            val saveFile = findSaveInPath(basePath, gameTitle, config.saveExtensions)
            if (saveFile != null) {
                emulatorSaveConfigDao.upsert(
                    EmulatorSaveConfigEntity(
                        emulatorId = emulatorId,
                        savePathPattern = basePath,
                        isAutoDetected = true,
                        lastVerifiedAt = Instant.now()
                    )
                )
                return@withContext saveFile
            }
        }

        null
    }

    private fun findSaveInPath(
        basePath: String,
        gameTitle: String,
        extensions: List<String> = listOf("*")
    ): String? {
        val dir = File(basePath)
        if (!dir.exists() || !dir.isDirectory) return null

        val sanitizedTitle = sanitizeFileName(gameTitle).lowercase()
        val files = dir.listFiles() ?: return null

        return files.firstOrNull { file ->
            val name = file.nameWithoutExtension.lowercase()
            val ext = file.extension.lowercase()
            val matchesName = name == sanitizedTitle ||
                name.contains(sanitizedTitle) ||
                sanitizedTitle.contains(name)
            val matchesExt = extensions.contains("*") || extensions.contains(ext)
            file.isFile && matchesName && matchesExt
        }?.absolutePath
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformId: String,
        romPath: String?
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return constructRetroArchSavePath(emulatorId, gameTitle, platformId, romPath)
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformId)
        val baseDir = resolvedPaths.firstOrNull { File(it).exists() }
            ?: resolvedPaths.firstOrNull()
            ?: return null

        val extension = config.saveExtensions.firstOrNull { it != "*" } ?: "sav"
        val sanitizedName = sanitizeFileName(gameTitle)
        val fileName = "$sanitizedName.$extension"

        return "$baseDir/$fileName"
    }

    private fun constructRetroArchSavePath(
        emulatorId: String,
        gameTitle: String,
        platformId: String,
        romPath: String?
    ): String? {
        val packageName = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            else -> "com.retroarch"
        }

        val raConfig = retroArchConfigParser.parse(packageName)
        val coreName = SavePathRegistry.getRetroArchCore(platformId) ?: return null
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: return null
        val extension = saveConfig.saveExtensions.firstOrNull() ?: "srm"

        val baseDir = when {
            raConfig?.savefilesInContentDir == true && romPath != null -> {
                File(romPath).parent
            }
            raConfig?.savefileDirectory != null -> {
                if (raConfig.sortSavefilesByContentEnable) {
                    "${raConfig.savefileDirectory}/$coreName"
                } else {
                    raConfig.savefileDirectory
                }
            }
            else -> {
                val defaultPaths = SavePathRegistry.resolvePath(saveConfig, platformId)
                defaultPaths.firstOrNull { File(it).exists() }
                    ?: defaultPaths.firstOrNull()
            }
        } ?: return null

        val fileName = buildRetroArchFileName(gameTitle, romPath, extension)
        return "$baseDir/$fileName"
    }

    private fun buildRetroArchFileName(
        gameTitle: String,
        romPath: String?,
        extension: String
    ): String {
        if (romPath != null) {
            val romFile = File(romPath)
            val romName = romFile.nameWithoutExtension
            return "$romName.$extension"
        }

        val sanitized = gameTitle
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return "$sanitized.$extension"
    }

    fun constructSavePathWithFileName(
        emulatorId: String,
        platformId: String,
        romPath: String?,
        serverFileName: String
    ): String? {
        val baseDir = getSaveDirectory(emulatorId, platformId, romPath) ?: return null
        return "$baseDir/$serverFileName"
    }

    private fun getSaveDirectory(
        emulatorId: String,
        platformId: String,
        romPath: String?
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return getRetroArchSaveDirectory(emulatorId, platformId, romPath)
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformId)
        return resolvedPaths.firstOrNull { File(it).exists() }
            ?: resolvedPaths.firstOrNull()
    }

    private fun getRetroArchSaveDirectory(
        emulatorId: String,
        platformId: String,
        romPath: String?
    ): String? {
        val packageName = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            else -> "com.retroarch"
        }

        val raConfig = retroArchConfigParser.parse(packageName)
        val coreName = SavePathRegistry.getRetroArchCore(platformId)
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: return null

        return when {
            raConfig?.savefilesInContentDir == true && romPath != null -> {
                File(romPath).parent
            }
            raConfig?.savefileDirectory != null -> {
                if (raConfig.sortSavefilesByContentEnable && coreName != null) {
                    "${raConfig.savefileDirectory}/$coreName"
                } else {
                    raConfig.savefileDirectory
                }
            }
            else -> {
                val defaultPaths = SavePathRegistry.resolvePath(saveConfig, platformId)
                defaultPaths.firstOrNull { File(it).exists() }
                    ?: defaultPaths.firstOrNull()
            }
        }
    }

    suspend fun getSyncStatus(gameId: Long, emulatorId: String): SaveSyncEntity? {
        return saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
    }

    suspend fun checkForAllServerUpdates(): List<SaveSyncEntity> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext emptyList()
        val downloadedGames = gameDao.getGamesWithLocalPath().filter { it.rommId != null }
        val platformIds = downloadedGames.mapNotNull { it.platformId.toLongOrNull() }.distinct()

        val allUpdates = mutableListOf<SaveSyncEntity>()
        for (platformId in platformIds) {
            allUpdates.addAll(checkForServerUpdates(platformId))
        }
        allUpdates
    }

    suspend fun checkForServerUpdates(platformId: Long): List<SaveSyncEntity> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext emptyList()

        val response = try {
            api.getSavesByPlatform(platformId)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        if (!response.isSuccessful) return@withContext emptyList()

        val serverSaves = response.body() ?: return@withContext emptyList()
        val updatedEntities = mutableListOf<SaveSyncEntity>()

        val downloadedGames = gameDao.getGamesWithLocalPath()
            .filter { it.rommId != null }

        for (serverSave in serverSaves) {
            val game = downloadedGames.find { it.rommId == serverSave.romId } ?: continue
            val emulatorId = serverSave.emulator ?: "default"
            val existing = saveSyncDao.getByGameAndEmulator(game.id, emulatorId)

            val serverTime = parseTimestamp(serverSave.updatedAt)

            if (existing == null || serverTime.isAfter(existing.serverUpdatedAt)) {
                val entity = SaveSyncEntity(
                    id = existing?.id ?: 0,
                    gameId = game.id,
                    rommId = game.rommId!!,
                    emulatorId = emulatorId,
                    rommSaveId = serverSave.id,
                    localSavePath = existing?.localSavePath,
                    localUpdatedAt = existing?.localUpdatedAt,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = existing?.lastSyncedAt,
                    syncStatus = determineSyncStatus(existing?.localUpdatedAt, serverTime)
                )
                saveSyncDao.upsert(entity)
                if (entity.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER) {
                    updatedEntities.add(entity)
                }
            }
        }

        updatedEntities
    }

    suspend fun checkSavesForGame(gameId: Long, rommId: Long): List<RomMSave> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext emptyList()

        val response = try {
            api.getSavesByRom(rommId)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        response.body() ?: emptyList()
    }

    private fun determineSyncStatus(localTime: Instant?, serverTime: Instant): String {
        if (localTime == null) return SaveSyncEntity.STATUS_SERVER_NEWER
        return when {
            serverTime.isAfter(localTime) -> SaveSyncEntity.STATUS_SERVER_NEWER
            localTime.isAfter(serverTime) -> SaveSyncEntity.STATUS_LOCAL_NEWER
            else -> SaveSyncEntity.STATUS_SYNCED
        }
    }

    suspend fun uploadSave(gameId: Long, emulatorId: String): SaveSyncResult = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext SaveSyncResult.NotConfigured

        val syncEntity = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        val game = gameDao.getById(gameId) ?: return@withContext SaveSyncResult.Error("Game not found")
        val rommId = game.rommId ?: return@withContext SaveSyncResult.NotConfigured

        val localPath = syncEntity?.localSavePath
            ?: discoverSavePath(emulatorId, game.title, game.platformId, game.localPath)
            ?: return@withContext SaveSyncResult.NoSaveFound

        val file = File(localPath)
        if (!file.exists()) return@withContext SaveSyncResult.NoSaveFound

        val localModified = Instant.ofEpochMilli(file.lastModified())

        if (syncEntity?.serverUpdatedAt != null &&
            syncEntity.serverUpdatedAt.isAfter(syncEntity.lastSyncedAt ?: Instant.EPOCH) &&
            syncEntity.serverUpdatedAt.isAfter(localModified)
        ) {
            return@withContext SaveSyncResult.Conflict(gameId, localModified, syncEntity.serverUpdatedAt)
        }

        try {
            val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", file.name, requestBody)

            val response = if (syncEntity?.rommSaveId != null) {
                api.updateSave(syncEntity.rommSaveId, filePart)
            } else {
                api.uploadSave(rommId, emulatorId, filePart)
            }

            if (response.isSuccessful) {
                val serverSave = response.body()!!
                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = syncEntity?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        rommSaveId = serverSave.id,
                        localSavePath = localPath,
                        localUpdatedAt = localModified,
                        serverUpdatedAt = parseTimestamp(serverSave.updatedAt),
                        lastSyncedAt = Instant.now(),
                        syncStatus = SaveSyncEntity.STATUS_SYNCED
                    )
                )
                SaveSyncResult.Success
            } else {
                Log.e(TAG, "uploadSave failed: ${response.code()}")
                SaveSyncResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadSave exception", e)
            SaveSyncResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun downloadSave(gameId: Long, emulatorId: String): SaveSyncResult = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext SaveSyncResult.NotConfigured

        val syncEntity = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
            ?: return@withContext SaveSyncResult.Error("No save tracking found")

        val saveId = syncEntity.rommSaveId
            ?: return@withContext SaveSyncResult.Error("No server save ID")

        val game = gameDao.getById(gameId)
            ?: return@withContext SaveSyncResult.Error("Game not found")

        val serverSave = try {
            api.getSave(saveId).body()
        } catch (e: Exception) {
            Log.e(TAG, "downloadSave: getSave failed", e)
            return@withContext SaveSyncResult.Error("Failed to get save info: ${e.message}")
        } ?: return@withContext SaveSyncResult.Error("Save not found on server")

        val targetPath = syncEntity.localSavePath
            ?: discoverSavePath(emulatorId, game.title, game.platformId, game.localPath)
            ?: constructSavePathWithFileName(emulatorId, platformId = game.platformId, romPath = game.localPath, serverFileName = serverSave.fileName)
            ?: return@withContext SaveSyncResult.Error("Cannot determine save path")

        try {
            val downloadPath = serverSave.downloadPath
                ?: return@withContext SaveSyncResult.Error("No download path available")

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Log.e(TAG, "downloadSave failed: ${response.code()}")
                return@withContext SaveSyncResult.Error("Download failed: ${response.code()}")
            }

            val targetFile = File(targetPath)
            targetFile.parentFile?.mkdirs()

            response.body()?.byteStream()?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            saveSyncDao.upsert(
                syncEntity.copy(
                    localSavePath = targetPath,
                    localUpdatedAt = Instant.now(),
                    lastSyncedAt = Instant.now(),
                    syncStatus = SaveSyncEntity.STATUS_SYNCED
                )
            )

            SaveSyncResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "downloadSave exception", e)
            SaveSyncResult.Error(e.message ?: "Download failed")
        }
    }

    suspend fun queueUpload(gameId: Long, emulatorId: String, localPath: String) {
        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return

        pendingSaveSyncDao.deleteByGameAndEmulator(gameId, emulatorId)
        pendingSaveSyncDao.insert(
            PendingSaveSyncEntity(
                gameId = gameId,
                rommId = rommId,
                emulatorId = emulatorId,
                localSavePath = localPath,
                action = PendingSaveSyncEntity.ACTION_UPLOAD
            )
        )
    }

    suspend fun processPendingUploads(): Int = withContext(Dispatchers.IO) {
        val pending = pendingSaveSyncDao.getRetryable()
        var processed = 0

        for (item in pending) {
            when (val result = uploadSave(item.gameId, item.emulatorId)) {
                is SaveSyncResult.Success -> {
                    pendingSaveSyncDao.delete(item.id)
                    processed++
                }
                is SaveSyncResult.Conflict -> {
                    // Leave in queue for user resolution
                }
                is SaveSyncResult.Error -> {
                    pendingSaveSyncDao.incrementRetry(item.id, result.message)
                }
                else -> {}
            }
        }

        processed
    }

    suspend fun updateSyncEntity(
        gameId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?
    ) {
        val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        if (existing != null) {
            saveSyncDao.upsert(
                existing.copy(
                    localSavePath = localPath ?: existing.localSavePath,
                    localUpdatedAt = localUpdatedAt ?: existing.localUpdatedAt
                )
            )
        }
    }

    suspend fun createOrUpdateSyncEntity(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?
    ): SaveSyncEntity {
        val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        val entity = SaveSyncEntity(
            id = existing?.id ?: 0,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            rommSaveId = existing?.rommSaveId,
            localSavePath = localPath ?: existing?.localSavePath,
            localUpdatedAt = localUpdatedAt ?: existing?.localUpdatedAt,
            serverUpdatedAt = existing?.serverUpdatedAt,
            lastSyncedAt = existing?.lastSyncedAt,
            syncStatus = existing?.syncStatus ?: SaveSyncEntity.STATUS_PENDING_UPLOAD
        )
        saveSyncDao.upsert(entity)
        return entity
    }

    suspend fun preLaunchSync(gameId: Long, rommId: Long, emulatorId: String): PreLaunchSyncResult =
        withContext(Dispatchers.IO) {
            val api = this@SaveSyncRepository.api ?: return@withContext PreLaunchSyncResult.NoConnection

            try {
                val serverSaves = checkSavesForGame(gameId, rommId)
                val serverSave = serverSaves.find { it.emulator == emulatorId || it.emulator == null }
                    ?: return@withContext PreLaunchSyncResult.NoServerSave

                val serverTime = parseTimestamp(serverSave.updatedAt)
                val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)

                val localFileExists = existing?.localSavePath?.let { File(it).exists() } ?: false

                if (localFileExists && existing?.localUpdatedAt != null && !serverTime.isAfter(existing.localUpdatedAt)) {
                    return@withContext PreLaunchSyncResult.LocalIsNewer
                }

                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        rommSaveId = serverSave.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
                    )
                )

                PreLaunchSyncResult.ServerIsNewer(serverTime)
            } catch (e: Exception) {
                PreLaunchSyncResult.NoConnection
            }
        }

    sealed class PreLaunchSyncResult {
        data object NoConnection : PreLaunchSyncResult()
        data object NoServerSave : PreLaunchSyncResult()
        data object LocalIsNewer : PreLaunchSyncResult()
        data class ServerIsNewer(val serverTimestamp: Instant) : PreLaunchSyncResult()
    }

    private fun parseTimestamp(timestamp: String): Instant {
        return try {
            Instant.parse(timestamp)
        } catch (e: Exception) {
            try {
                DateTimeFormatter.ISO_DATE_TIME.parse(timestamp, Instant::from)
            } catch (e2: Exception) {
                Instant.now()
            }
        }
    }
}
