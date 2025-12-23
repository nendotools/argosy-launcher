package com.nendo.argosy.data.launcher

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nendo.argosy.data.remote.steam.SteamStoreSearchApi
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "GameNativeScanner"
private const val PACKAGE_NAME = "app.gamenative"
private const val STEAM_COMMON_PATH = "files/Steam/steamapps/common"
private const val DOWNLOAD_COMPLETE_MARKER = ".download_complete"

object GameNativeScanner {

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

    suspend fun scan(context: Context): List<ScannedSteamGame> = withContext(Dispatchers.IO) {
        val games = mutableListOf<ScannedSteamGame>()

        val storagePaths = getStoragePaths(context)
        Log.d(TAG, "Scanning ${storagePaths.size} storage paths")

        for (storagePath in storagePaths) {
            val commonDir = File(storagePath, "Android/data/$PACKAGE_NAME/$STEAM_COMMON_PATH")
            if (!commonDir.exists() || !commonDir.canRead()) {
                Log.d(TAG, "Skipping inaccessible path: ${commonDir.absolutePath}")
                continue
            }

            Log.d(TAG, "Scanning: ${commonDir.absolutePath}")
            scanDirectory(commonDir, games)
        }

        Log.d(TAG, "Found ${games.size} GameNative games")
        games
    }

    private suspend fun scanDirectory(
        commonDir: File,
        games: MutableList<ScannedSteamGame>
    ) {
        val gameFolders = commonDir.listFiles { file ->
            file.isDirectory && File(file, DOWNLOAD_COMPLETE_MARKER).exists()
        } ?: return

        Log.d(TAG, "Found ${gameFolders.size} game folders with download complete marker")

        for (folder in gameFolders) {
            val gameName = folder.name
            val appId = searchAppId(gameName)

            if (appId != null) {
                games.add(ScannedSteamGame(appId = appId, name = gameName))
                Log.d(TAG, "Matched: $gameName -> $appId")
            } else {
                Log.w(TAG, "Could not find app ID for: $gameName")
            }
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

    private fun getStoragePaths(context: Context): List<File> {
        val paths = mutableListOf<File>()

        Environment.getExternalStorageDirectory()?.let { paths.add(it) }

        context.getExternalFilesDirs(null).forEach { dir ->
            dir?.parentFile?.parentFile?.parentFile?.parentFile?.let { root ->
                if (root.absolutePath != Environment.getExternalStorageDirectory()?.absolutePath) {
                    paths.add(root)
                }
            }
        }

        return paths.distinct()
    }
}
