package com.nendo.argosy.ui.coil

import android.content.pm.PackageManager
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

private const val TAG = "AppIconFetcher"

data class AppIconData(val packageName: String)

class AppIconFetcher(
    private val data: AppIconData,
    private val packageManager: PackageManager
) : Fetcher {

    @Suppress("SwallowedException")
    override suspend fun fetch(): FetchResult? {
        return try {
            val appInfo = packageManager.getApplicationInfo(data.packageName, 0)
            val drawable = packageManager.getApplicationIcon(appInfo)
            DrawableResult(
                drawable = drawable,
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: ${data.packageName}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon for ${data.packageName}", e)
            null
        }
    }

    class Factory(
        private val packageManager: PackageManager
    ) : Fetcher.Factory<AppIconData> {

        override fun create(
            data: AppIconData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AppIconFetcher(data, packageManager)
    }
}
