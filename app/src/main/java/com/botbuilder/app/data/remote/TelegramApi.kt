package com.botbuilder.app.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TelegramApi {
    // Using @Url with a full absolute address instead of @Path + base URL resolution.
    // Reason: a bot token looks like "8748634758:AAHj..." — when combined as a relative
    // path segment, the colon right after the leading digits gets misread as a URL
    // "scheme separator" (like mailto: or tel:), which breaks base+relative resolution.
    // Building the full URL ourselves sidesteps that ambiguity entirely.
    @GET
    suspend fun getMe(@Url url: String): TgResponse<TgUser>

    @GET
    suspend fun getUpdates(
        @Url url: String,
        @Query("offset") offset: Long?,
        @Query("timeout") timeout: Int = 30
    ): TgResponse<List<TgUpdate>>

    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Body body: SendMessageBody
    ): TgResponse<TgMessage>

    @POST
    suspend fun sendDocument(
        @Url url: String,
        @Body body: SendDocumentBody
    ): TgResponse<TgMessage>

    // This is what actually makes the "/" popup menu appear in Telegram, showing
    // your commands with descriptions — like BotFather's /newbot, /deletebot list.
    @POST
    suspend fun setMyCommands(
        @Url url: String,
        @Body body: SetMyCommandsBody
    ): TgResponse<Boolean>

    companion object {
        private const val BASE = "https://api.telegram.org/"

        fun getMeUrl(token: String) = "${BASE}bot$token/getMe"
        fun getUpdatesUrl(token: String) = "${BASE}bot$token/getUpdates"
        fun sendMessageUrl(token: String) = "${BASE}bot$token/sendMessage"
        fun sendDocumentUrl(token: String) = "${BASE}bot$token/sendDocument"
        fun setMyCommandsUrl(token: String) = "${BASE}bot$token/setMyCommands"

        // Long-poll timeout (30s) needs a longer client read timeout or every poll will fail.
        fun create(): TelegramApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS) // > getUpdates timeout param
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TelegramApi::class.java)
        }
    }
}

