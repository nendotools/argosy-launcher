package com.nendo.argosy.data.launcher

import android.os.Environment
import android.util.Log
import com.nendo.argosy.data.remote.steam.SteamStoreSearchApi
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private const val TAG = "GameHubLogScanner"

data class ScannedSteamGame(
    val appId: Long,
    val name: String
)

object GameHubLogScanner {

    private val appIdPattern = Pattern.compile("\"appId\":(\\d+)")
    private val namePattern = Pattern.compile("\"name\":\"([^\"]+)\"")
    private val wineProcessPattern = Pattern.compile(
        """Wine user process detected: [a-z]:\\steamapps\\common\\([^\\]+)\\"""
    )

    private val api: SteamStoreSearchApi by lazy { createApi() }

    private fun createApi(): SteamStoreSearchApi {
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://store.steampowered.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamStoreSearchApi::class.java)
    }

    private fun getLogDir(packageName: String): File = File(
        Environment.getExternalStorageDirectory(),
        "Android/data/$packageName/files/Documents/XiaoKunLogcat"
    )

    suspend fun scan(packageName: String): List<ScannedSteamGame> {
        val logDir = getLogDir(packageName)
        if (!logDir.exists() || !logDir.canRead()) {
            Log.w(TAG, "Log directory not accessible: ${logDir.absolutePath}")
            return emptyList()
        }

        val games = mutableMapOf<Long, ScannedSteamGame>()
        val gameNamesWithoutAppId = mutableSetOf<String>()

        logDir.listFiles()?.filter { it.name.startsWith("XiaoKunLogInfo") }?.forEach { file ->
            try {
                scanFile(file, games, gameNamesWithoutAppId)
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning ${file.name}", e)
            }
        }

        for (gameName in gameNamesWithoutAppId) {
            if (games.values.none { it.name.equals(gameName, ignoreCase = true) }) {
                val appId = searchAppId(gameName)
                if (appId != null) {
                    games[appId] = ScannedSteamGame(appId = appId, name = gameName)
                    Log.d(TAG, "Resolved via search: $gameName -> $appId")
                } else {
                    Log.w(TAG, "Could not find app ID for: $gameName")
                }
            }
        }

        Log.d(TAG, "Found ${games.size} unique Steam games")
        return games.values.toList()
    }

    private fun scanFile(
        file: File,
        games: MutableMap<Long, ScannedSteamGame>,
        gameNamesWithoutAppId: MutableSet<String>
    ) {
        file.useLines { lines ->
            lines.forEach { line ->
                if (line.contains("ACFWriter extend") && line.contains("subTask")) {
                    extractGameFromAcfLine(line)?.let { game ->
                        games[game.appId] = game
                    }
                } else if (line.contains("Wine user process detected:")) {
                    extractGameNameFromWineLine(line)?.let { name ->
                        gameNamesWithoutAppId.add(name)
                    }
                }
            }
        }
    }

    private fun extractGameFromAcfLine(line: String): ScannedSteamGame? {
        return try {
            val jsonStart = line.indexOf("{")
            if (jsonStart == -1) return null

            val json = line.substring(jsonStart)

            val appIdMatcher = appIdPattern.matcher(json)
            val nameMatcher = namePattern.matcher(json)

            if (appIdMatcher.find() && nameMatcher.find()) {
                val appId = appIdMatcher.group(1)?.toLongOrNull() ?: return null
                val name = nameMatcher.group(1) ?: return null
                ScannedSteamGame(appId = appId, name = name)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ACF line", e)
            null
        }
    }

    private fun extractGameNameFromWineLine(line: String): String? {
        return try {
            val matcher = wineProcessPattern.matcher(line)
            if (matcher.find()) {
                matcher.group(1)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Wine line", e)
            null
        }
    }

    private suspend fun searchAppId(gameName: String): Long? {
        return try {
            val response = api.search(gameName)
            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                val match = items.firstOrNull { item ->
                    item.name.equals(gameName, ignoreCase = true)
                } ?: items.firstOrNull()
                match?.id
            } else {
                Log.w(TAG, "Search API error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for: $gameName", e)
            null
        }
    }
}
