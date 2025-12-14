package com.nendo.argosy.data.emulator

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RetroArchConfigParser"

data class RetroArchSaveConfig(
    val savefileDirectory: String?,
    val savefilesInContentDir: Boolean,
    val sortSavefilesEnable: Boolean,
    val sortSavefilesByContentEnable: Boolean
)

@Singleton
class RetroArchConfigParser @Inject constructor() {

    private val configPaths = listOf(
        "/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg",
        "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
        "/storage/emulated/0/RetroArch/retroarch.cfg"
    )

    fun findConfigFile(packageName: String): File? {
        val packageSpecificPath = when (packageName) {
            "com.retroarch" -> "/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg"
            "com.retroarch.aarch64" -> "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg"
            else -> null
        }

        if (packageSpecificPath != null) {
            val file = File(packageSpecificPath)
            if (file.exists()) return file
        }

        val portableConfig = File("/storage/emulated/0/RetroArch/retroarch.cfg")
        if (portableConfig.exists()) return portableConfig

        return configPaths
            .map { File(it) }
            .firstOrNull { it.exists() }
    }

    fun parse(packageName: String): RetroArchSaveConfig? {
        val configFile = findConfigFile(packageName)
        if (configFile == null) {
            Log.d(TAG, "No retroarch.cfg found for $packageName")
            return null
        }

        Log.d(TAG, "Parsing config: ${configFile.absolutePath}")
        return parseFile(configFile)
    }

    private fun parseFile(file: File): RetroArchSaveConfig {
        val config = mutableMapOf<String, String>()

        try {
            file.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        val (key, value) = trimmed.split("=", limit = 2)
                        config[key.trim()] = value.trim().removeSurrounding("\"")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config file", e)
        }

        val savefileDirectory = config["savefile_directory"]?.takeIf {
            it != "default" && it.isNotBlank()
        }
        val savefilesInContentDir = config["savefiles_in_content_dir"] == "true"
        val sortBySystem = config["sort_savefiles_enable"] == "true"
        val sortByCore = config["sort_savefiles_by_content_enable"] == "true"

        Log.d(TAG, "Parsed: savefileDirectory=$savefileDirectory, inContentDir=$savefilesInContentDir, sortBySystem=$sortBySystem, sortByCore=$sortByCore")

        return RetroArchSaveConfig(
            savefileDirectory = savefileDirectory,
            savefilesInContentDir = savefilesInContentDir,
            sortSavefilesEnable = sortBySystem,
            sortSavefilesByContentEnable = sortByCore
        )
    }

    fun resolveSavePaths(
        packageName: String,
        systemName: String?,
        coreName: String?,
        contentDirectory: String? = null
    ): List<String> {
        val config = parse(packageName)
        val paths = mutableListOf<String>()

        val baseDirs = mutableListOf<String>()

        if (config?.savefilesInContentDir == true && contentDirectory != null) {
            baseDirs.add(contentDirectory)
        }

        config?.savefileDirectory?.let { baseDirs.add(it) }

        if (baseDirs.isEmpty()) {
            baseDirs.addAll(listOf(
                "/storage/emulated/0/RetroArch/saves",
                "/storage/emulated/0/Android/data/$packageName/files/saves"
            ))
        }

        for (base in baseDirs) {
            if (systemName != null && coreName != null) {
                paths.add("$base/$systemName/$coreName")
            }
            if (coreName != null) {
                paths.add("$base/$coreName")
            }
            if (systemName != null) {
                paths.add("$base/$systemName")
            }
            paths.add(base)
        }

        Log.d(TAG, "resolveSavePaths: generated ${paths.size} paths for package=$packageName, system=$systemName, core=$coreName")
        return paths.distinct()
    }
}
