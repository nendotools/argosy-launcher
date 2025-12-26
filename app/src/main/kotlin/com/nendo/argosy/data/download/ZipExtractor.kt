package com.nendo.argosy.data.download

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ExtractedGameFiles(
    val gameFile: File?,
    val updateFiles: List<File>,
    val gameFolder: File
)

private val NSW_GAME_EXTENSIONS = setOf("xci", "nsp", "nca", "nro")
private val NSW_UPDATE_EXTENSIONS = setOf("nsp")
private val NSW_PLATFORM_SLUGS = setOf("switch", "nsw")
private val ZIP_MAGIC_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

object ZipExtractor {

    fun isNswPlatform(platformSlug: String): Boolean {
        return platformSlug.lowercase() in NSW_PLATFORM_SLUGS
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
        zipFile: File,
        gameTitle: String,
        platformDir: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): ExtractedGameFiles {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }
        val updatesFolder = File(gameFolder, "updates")

        val extractedFiles = mutableListOf<File>()
        var gameFile: File? = null
        val updateFiles = mutableListOf<File>()

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            val entries = mutableListOf<Pair<String, Long>>()

            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries.add(entry.name to entry.size)
                }
                entry = zis.nextEntry
            }

            val totalEntries = entries.size
            var currentEntry = 0

            ZipInputStream(zipFile.inputStream().buffered()).use { zis2 ->
                var zipEntry = zis2.nextEntry
                while (zipEntry != null) {
                    if (!zipEntry.isDirectory) {
                        val fileName = File(zipEntry.name).name
                        val extension = fileName.substringAfterLast('.', "").lowercase()

                        val targetFile = when {
                            extension == "xci" -> {
                                File(gameFolder, fileName)
                            }
                            extension in NSW_UPDATE_EXTENSIONS && gameFile != null -> {
                                updatesFolder.mkdirs()
                                File(updatesFolder, fileName)
                            }
                            extension in NSW_GAME_EXTENSIONS && gameFile == null -> {
                                File(gameFolder, fileName)
                            }
                            extension in NSW_UPDATE_EXTENSIONS -> {
                                updatesFolder.mkdirs()
                                File(updatesFolder, fileName)
                            }
                            else -> {
                                File(gameFolder, fileName)
                            }
                        }

                        targetFile.outputStream().buffered().use { output ->
                            zis2.copyTo(output)
                        }

                        extractedFiles.add(targetFile)

                        if (extension == "xci" || (extension in NSW_GAME_EXTENSIONS && gameFile == null)) {
                            if (extension == "xci" || gameFile == null) {
                                gameFile = targetFile
                            }
                        }
                        if (targetFile.parentFile == updatesFolder) {
                            updateFiles.add(targetFile)
                        }

                        currentEntry++
                        onProgress?.invoke(currentEntry, totalEntries)
                    }
                    zis2.closeEntry()
                    zipEntry = zis2.nextEntry
                }
            }
        }

        return ExtractedGameFiles(
            gameFile = gameFile,
            updateFiles = updateFiles,
            gameFolder = gameFolder
        )
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

        if (romFile.absolutePath != targetFile.absolutePath) {
            if (!romFile.renameTo(targetFile)) {
                romFile.copyTo(targetFile, overwrite = true)
                romFile.delete()
            }
        }

        return targetFile
    }

    fun getUpdatesFolder(localPath: String): File? {
        val romFile = File(localPath)
        if (!romFile.exists()) return null

        val gameFolder = romFile.parentFile ?: return null
        val updatesFolder = File(gameFolder, "updates")

        return if (updatesFolder.exists() && updatesFolder.isDirectory) {
            updatesFolder
        } else {
            null
        }
    }

    fun listUpdateFiles(localPath: String): List<File> {
        val updatesFolder = getUpdatesFolder(localPath) ?: return emptyList()
        return updatesFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in NSW_UPDATE_EXTENSIONS }
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
