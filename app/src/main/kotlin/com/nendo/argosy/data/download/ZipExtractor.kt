package com.nendo.argosy.data.download

import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val TAG = "ZipExtractor"

data class ExtractedGameFiles(
    val gameFile: File?,
    val updateFiles: List<File>,
    val dlcFiles: List<File> = emptyList(),
    val gameFolder: File
)

data class ExtractedMultiDiscFiles(
    val discFiles: List<File>,
    val m3uFile: File?,
    val gameFolder: File
)

private val NSW_GAME_EXTENSIONS = setOf("xci", "nsp", "nca", "nro")
private val NSW_UPDATE_EXTENSIONS = setOf("nsp")
private val NSW_PLATFORM_SLUGS = setOf("switch", "nsw")
private val MULTI_DISC_PLATFORM_SLUGS = setOf("psx", "saturn", "dreamcast", "dc", "segacd", "pcenginecd", "pce-cd")
private val DISC_EXTENSIONS = setOf("bin", "cue", "chd", "iso", "img", "mdf", "gdi", "cdi")
private val ZIP_MAGIC_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

data class PlatformExtractConfig(
    val platformSlugs: Set<String>,
    val gameExtensions: Set<String>,
    val updateExtensions: Set<String>,
    val dlcExtensions: Set<String>,
    val updateFolder: String,
    val dlcFolder: String
)

private val PLATFORM_CONFIGS = listOf(
    PlatformExtractConfig(
        platformSlugs = setOf("switch", "nsw"),
        gameExtensions = setOf("xci", "nsp", "nca", "nro"),
        updateExtensions = setOf("nsp"),
        dlcExtensions = setOf("nsp"),
        updateFolder = "updates",
        dlcFolder = "dlc"
    ),
    PlatformExtractConfig(
        platformSlugs = setOf("vita", "psvita"),
        gameExtensions = setOf("vpk", "zip"),
        updateExtensions = setOf("vpk"),
        dlcExtensions = setOf("vpk"),
        updateFolder = "update",
        dlcFolder = "addcont"
    ),
    PlatformExtractConfig(
        platformSlugs = setOf("wiiu"),
        gameExtensions = setOf("wua", "wud", "wux", "wup", "rpx"),
        updateExtensions = setOf("wup"),
        dlcExtensions = setOf("wup"),
        updateFolder = "update",
        dlcFolder = "aoc"
    ),
    PlatformExtractConfig(
        platformSlugs = setOf("wii"),
        gameExtensions = setOf("wbfs", "iso", "ciso", "wia", "rvz"),
        updateExtensions = setOf("wad"),
        dlcExtensions = setOf("wad"),
        updateFolder = "wad",
        dlcFolder = "wad"
    )
)

object ZipExtractor {

    fun isNswPlatform(platformSlug: String): Boolean {
        return platformSlug.lowercase() in NSW_PLATFORM_SLUGS
    }

    fun isMultiDiscPlatform(platformSlug: String): Boolean {
        return platformSlug.lowercase() in MULTI_DISC_PLATFORM_SLUGS
    }

    fun getPlatformConfig(platformSlug: String): PlatformExtractConfig? {
        val slug = platformSlug.lowercase()
        return PLATFORM_CONFIGS.find { slug in it.platformSlugs }
    }

    fun hasUpdateSupport(platformSlug: String): Boolean {
        return getPlatformConfig(platformSlug) != null
    }

    fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return file.inputStream().use { stream ->
            val header = ByteArray(4)
            stream.read(header) == 4 && header.contentEquals(ZIP_MAGIC_BYTES)
        }
    }

    fun isZipStream(inputStream: InputStream): Boolean {
        val header = ByteArray(4)
        val bytesRead = inputStream.read(header)
        return bytesRead == 4 && header.contentEquals(ZIP_MAGIC_BYTES)
    }

    fun extractForNsw(
        zipFilePath: File,
        gameTitle: String,
        platformDir: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): ExtractedGameFiles {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }
        val updatesFolder = File(gameFolder, "updates")
        val dlcFolder = File(gameFolder, "dlc")

        Log.d(TAG, "=== NSW Extraction Start ===")
        Log.d(TAG, "ZIP file: ${zipFilePath.absolutePath}")
        Log.d(TAG, "ZIP exists: ${zipFilePath.exists()}, size: ${zipFilePath.length()}")
        Log.d(TAG, "Game folder: ${gameFolder.absolutePath}")

        val extractedFiles = mutableListOf<File>()
        var gameFile: File? = null
        val updateFiles = mutableListOf<File>()
        val dlcFiles = mutableListOf<File>()

        ZipFile(zipFilePath).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            val totalBytes = entries.sumOf { it.size }
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            val progressThreshold = 1024 * 1024L // Report every 1MB
            Log.d(TAG, "Total entries found: ${entries.size}, total bytes: $totalBytes")

            entries.forEach { entry ->
                val fileName = File(entry.name).name
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val parentPath = File(entry.name).parent?.lowercase() ?: ""

                val isUpdate = parentPath.contains("update")
                val isDlc = parentPath.contains("dlc") || parentPath.contains("aoc")

                val targetFile = when {
                    extension == "xci" -> File(gameFolder, fileName)
                    isUpdate && extension in NSW_UPDATE_EXTENSIONS -> {
                        updatesFolder.mkdirs()
                        File(updatesFolder, fileName)
                    }
                    isDlc && extension in NSW_UPDATE_EXTENSIONS -> {
                        dlcFolder.mkdirs()
                        File(dlcFolder, fileName)
                    }
                    extension in NSW_GAME_EXTENSIONS && gameFile == null -> File(gameFolder, fileName)
                    extension in NSW_UPDATE_EXTENSIONS && gameFile != null -> {
                        updatesFolder.mkdirs()
                        File(updatesFolder, fileName)
                    }
                    else -> File(gameFolder, fileName)
                }

                Log.d(TAG, "Extracting: ${entry.name} -> ${targetFile.absolutePath}, size: ${entry.size}")

                zip.getInputStream(entry).buffered().use { input ->
                    targetFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            if (bytesWritten - lastReportedBytes >= progressThreshold) {
                                onProgress?.invoke(bytesWritten, totalBytes)
                                lastReportedBytes = bytesWritten
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                Log.d(TAG, "Extracted: $fileName, expected=${entry.size}, actual=${targetFile.length()}")

                if (targetFile.length() == 0L && entry.size > 0) {
                    Log.e(TAG, "ERROR: File $fileName extracted as 0 bytes but expected ${entry.size}")
                }

                extractedFiles.add(targetFile)

                when {
                    extension == "xci" || (extension in NSW_GAME_EXTENSIONS && gameFile == null) -> gameFile = targetFile
                    targetFile.parentFile == updatesFolder -> updateFiles.add(targetFile)
                    targetFile.parentFile == dlcFolder -> dlcFiles.add(targetFile)
                }
            }
        }

        Log.d(TAG, "=== NSW Extraction Complete ===")
        Log.d(TAG, "Game file: ${gameFile?.absolutePath}, size: ${gameFile?.length() ?: 0}")
        Log.d(TAG, "Update files: ${updateFiles.size}")
        Log.d(TAG, "DLC files: ${dlcFiles.size}")
        Log.d(TAG, "Total extracted: ${extractedFiles.size}")

        return ExtractedGameFiles(
            gameFile = gameFile,
            updateFiles = updateFiles,
            dlcFiles = dlcFiles,
            gameFolder = gameFolder
        )
    }

    fun extractMultiDisc(
        zipFile: File,
        gameTitle: String,
        platformDir: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): ExtractedMultiDiscFiles {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }

        val discFiles = mutableListOf<File>()
        var m3uFile: File? = null
        val totalBytes = zipFile.length()
        var bytesWritten = 0L
        var lastReportedBytes = 0L
        val progressThreshold = 1024 * 1024L // Report every 1MB

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                if (!zipEntry.isDirectory) {
                    val fileName = File(zipEntry.name).name
                    val extension = fileName.substringAfterLast('.', "").lowercase()
                    val targetFile = File(gameFolder, fileName)

                    targetFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = zis.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            if (bytesWritten - lastReportedBytes >= progressThreshold) {
                                onProgress?.invoke(bytesWritten, totalBytes)
                                lastReportedBytes = bytesWritten
                            }
                            bytes = zis.read(buffer)
                        }
                    }

                    when {
                        extension == "m3u" -> m3uFile = targetFile
                        extension in DISC_EXTENSIONS -> discFiles.add(targetFile)
                    }
                }
                zis.closeEntry()
                zipEntry = zis.nextEntry
            }
        }

        if (m3uFile == null && discFiles.size > 1) {
            m3uFile = generateM3uFile(gameFolder, sanitizedTitle, discFiles)
        }

        return ExtractedMultiDiscFiles(
            discFiles = discFiles.sortedBy { it.name },
            m3uFile = m3uFile,
            gameFolder = gameFolder
        )
    }

    private fun generateM3uFile(gameFolder: File, gameTitle: String, discFiles: List<File>): File {
        val m3uFile = File(gameFolder, "$gameTitle.m3u")
        val sortedDiscs = discFiles
            .filter { it.extension.lowercase() !in setOf("cue", "gdi") || discFiles.none { other ->
                other.nameWithoutExtension == it.nameWithoutExtension && other.extension.lowercase() in setOf("bin", "img")
            }}
            .sortedWith(compareBy(
                { extractDiscNumber(it.name) ?: Int.MAX_VALUE },
                { it.name }
            ))

        val content = sortedDiscs.joinToString("\n") { it.name }
        m3uFile.writeText(content, Charsets.US_ASCII)
        return m3uFile
    }

    private fun extractDiscNumber(fileName: String): Int? {
        val patterns = listOf(
            Regex("""[Dd]isc\s*(\d+)"""),
            Regex("""[Dd]isk\s*(\d+)"""),
            Regex("""[Cc][Dd]\s*(\d+)"""),
            Regex("""\((\d+)\s*of\s*\d+\)""")
        )
        for (pattern in patterns) {
            pattern.find(fileName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    fun organizeNswSingleFile(
        romFile: File,
        gameTitle: String,
        platformDir: File
    ): File {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }

        val extension = romFile.name.substringAfterLast('.', "").lowercase()
        val targetFileName = if (romFile.name.startsWith(sanitizedTitle)) {
            romFile.name
        } else {
            "$sanitizedTitle.${extension}"
        }

        val targetFile = File(gameFolder, targetFileName)

        if (romFile.absolutePath != targetFile.absolutePath && !romFile.renameTo(targetFile)) {
            romFile.copyTo(targetFile, overwrite = true)
            romFile.delete()
        }

        return targetFile
    }

    fun getUpdatesFolder(localPath: String, platformSlug: String? = null): File? {
        val romFile = File(localPath)
        if (!romFile.exists()) return null

        val gameFolder = romFile.parentFile ?: return null
        val config = platformSlug?.let { getPlatformConfig(it) }
        val folderName = config?.updateFolder ?: "updates"
        val updatesFolder = File(gameFolder, folderName)

        return if (updatesFolder.exists() && updatesFolder.isDirectory) {
            updatesFolder
        } else {
            null
        }
    }

    fun getDlcFolder(localPath: String, platformSlug: String): File? {
        val romFile = File(localPath)
        if (!romFile.exists()) return null

        val gameFolder = romFile.parentFile ?: return null
        val config = getPlatformConfig(platformSlug) ?: return null
        val dlcFolder = File(gameFolder, config.dlcFolder)

        return if (dlcFolder.exists() && dlcFolder.isDirectory) {
            dlcFolder
        } else {
            null
        }
    }

    fun listUpdateFiles(localPath: String, platformSlug: String? = null): List<File> {
        val config = platformSlug?.let { getPlatformConfig(it) }
        val updatesFolder = getUpdatesFolder(localPath, platformSlug) ?: return emptyList()
        val updateExtensions = config?.updateExtensions ?: NSW_UPDATE_EXTENSIONS

        return updatesFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in updateExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun listDlcFiles(localPath: String, platformSlug: String): List<File> {
        val config = getPlatformConfig(platformSlug) ?: return emptyList()
        val dlcFolder = getDlcFolder(localPath, platformSlug) ?: return emptyList()

        return dlcFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in config.dlcExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }
}
