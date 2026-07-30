package com.botbuilder.app.data.supabase

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class SignUpBody(
    val email: String,
    val password: String,
    val data: Map<String, String>
)

data class SignInBody(
    val email: String,
    val password: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    val user: SupabaseUser?
)

data class SupabaseUser(
    val id: String,
    val email: String?,
    @SerializedName("user_metadata") val userMetadata: Map<String, Any>?
)

interface SupabaseAuthApi {
    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    suspend fun signUp(@Header("apikey") apikey: String, @Body body: SignUpBody): AuthResponse

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Header("apikey") apikey: String, @Body body: SignInBody): AuthResponse

    companion object {
        fun create(): SupabaseAuthApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(SupabaseConfig.PROJECT_URL + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseAuthApi::class.java)
        }
    }
}

suspend fun <T> safeSupabaseCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val message = when {
            body?.contains("User already registered", ignoreCase = true) == true -> "An account with this email already exists — try logging in instead."
            body?.contains("Invalid login credentials", ignoreCase = true) == true -> "Incorrect email or password."
            body?.contains("Password should be at least", ignoreCase = true) == true -> "Password is too short — use at least 6 characters."
            else -> "Something went wrong: ${body ?: e.message()}"
        }
        Result.failure(Exception(message))
    } catch (e: Exception) {
        Result.failure(Exception("Network error: ${e.message ?: "couldn't reach the server"}"))
    }
}
