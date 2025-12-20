package com.nendo.argosy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nendo.argosy.data.sync.SaveSyncDownloadObserver
import com.nendo.argosy.data.sync.SaveSyncWorker
import com.nendo.argosy.data.update.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@HiltAndroidApp
class ArgosyApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var saveSyncDownloadObserver: SaveSyncDownloadObserver

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
        SaveSyncWorker.schedule(this)
        saveSyncDownloadObserver.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { hostname, _ -> isAllowedImageHost(hostname) }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }

    private fun isAllowedImageHost(hostname: String): Boolean {
        val allowedHosts = listOf(
            "images.launchbox-app.com",
            "media.retroachievements.org",
            "retroachievements.org",
            "steamcdn-a.akamaihd.net",
            "cdn.cloudflare.steamstatic.com",
            "cdn.akamai.steamstatic.com",
            "steamcommunity.com",
            "store.steampowered.com"
        )
        return allowedHosts.any { hostname.endsWith(it) }
    }
}
