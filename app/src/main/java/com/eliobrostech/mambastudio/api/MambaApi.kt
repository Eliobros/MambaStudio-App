package com.eliobrostech.mambastudio.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface MambaApiService {
    @POST("executar")
    suspend fun executarCodigo(@Body request: MambaRequest): MambaResponse

    companion object {
        private const val BASE_URL = "https://mambascript-api.mozhost.shop/"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        fun create(): MambaApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MambaApiService::class.java)
        }
    }
}
