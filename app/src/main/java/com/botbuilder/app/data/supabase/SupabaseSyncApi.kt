package com.botbuilder.app.data.supabase

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class UserConfigRow(
    val user_id: String,
    val name: String?,
    val replies: List<Map<String, Any?>>,
    val commands: List<Map<String, Any?>>,
    val file_links: List<Map<String, Any?>>,
    val ai_settings: Map<String, Any?>
)

interface SupabaseSyncApi {
    @Headers("Content-Type: application/json", "Prefer: resolution=merge-duplicates")
    @POST("rest/v1/user_bot_config")
    suspend fun upsertConfig(
        @Header("apikey") apikey: String,
        @Header("Authorization") bearer: String,
        @Body body: UserConfigRow
    )

    @GET("rest/v1/user_bot_config")
    suspend fun getConfig(
        @Header("apikey") apikey: String,
        @Header("Authorization") bearer: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*"
    ): List<UserConfigRow>

    companion object {
        fun create(): SupabaseSyncApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(SupabaseConfig.PROJECT_URL + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseSyncApi::class.java)
        }
    }
}
