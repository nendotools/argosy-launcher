package com.nendo.argosy.data.remote.steam

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SteamStoreSearchApi {

    @GET("api/storesearch/")
    suspend fun search(
        @Query("term") term: String,
        @Query("cc") countryCode: String = "us",
        @Query("l") language: String = "en"
    ): Response<SteamStoreSearchResponse>
}

@JsonClass(generateAdapter = true)
data class SteamStoreSearchResponse(
    val total: Int,
    val items: List<SteamStoreSearchItem>?
)

@JsonClass(generateAdapter = true)
data class SteamStoreSearchItem(
    val type: String,
    val name: String,
    val id: Long,
    @Json(name = "tiny_image") val tinyImage: String?
)
