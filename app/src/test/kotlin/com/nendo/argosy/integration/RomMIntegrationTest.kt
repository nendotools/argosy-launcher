package com.nendo.argosy.integration

import com.nendo.argosy.data.remote.romm.RomMApi
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

abstract class RomMIntegrationTest {

    protected lateinit var api: RomMApi
    private var token: String = ""

    @Before
    fun setupApi() {
        Assume.assumeTrue(
            "RomM test credentials not available - skipping integration tests",
            RomMTestConfig.isAvailable
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder().build()

        api = Retrofit.Builder()
            .baseUrl(RomMTestConfig.url)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }

    protected fun authenticate() {
        runBlocking {
            val response = api.login(
                username = RomMTestConfig.username,
                password = RomMTestConfig.password
            )
            if (response.isSuccessful) {
                token = response.body()?.accessToken ?: error("Login succeeded but no token returned")
                rebuildApiWithToken()
            } else {
                error("Login failed: ${response.code()} ${response.message()}")
            }
        }
    }

    private fun rebuildApiWithToken() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder().build()

        api = Retrofit.Builder()
            .baseUrl(RomMTestConfig.url)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }
}
