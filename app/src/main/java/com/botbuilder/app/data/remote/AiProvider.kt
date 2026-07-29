package com.botbuilder.app.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

data class AiConfig(
    val provider: String,       // "openai" | "gemini" | "anthropic" | "openrouter" | "custom"
    val apiKey: String,
    val baseUrl: String?,       // used for custom provider, optional override otherwise
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String
)

interface AiProvider {
    suspend fun getReply(userMessage: String, config: AiConfig): Result<String>
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private val JSON = "application/json".toMediaType()
private val gson = Gson()

/** Single adapter that dispatches to the right provider based on config.provider. */
class AiProviderRouter : AiProvider {

    override suspend fun getReply(userMessage: String, config: AiConfig): Result<String> {
        return try {
            when (config.provider.lowercase()) {
                "openai" -> openAiCompatible(userMessage, config, "https://api.openai.com/v1/chat/completions")
                "openrouter" -> openAiCompatible(userMessage, config, "https://openrouter.ai/api/v1/chat/completions")
                "custom" -> openAiCompatible(userMessage, config, config.baseUrl ?: return Result.failure(IllegalStateException("Custom base URL not set")))
                "gemini" -> gemini(userMessage, config)
                "anthropic" -> anthropic(userMessage, config)
                else -> Result.failure(IllegalArgumentException("Unknown AI provider: ${config.provider}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openAiCompatible(userMessage: String, config: AiConfig, url: String): Result<String> {
        val body = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("temperature", config.temperature)
            addProperty("max_tokens", config.maxTokens)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to config.systemPrompt),
                mapOf("role" to "user", "content" to userMessage)
            )))
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return Result.failure(Exception("AI request failed: HTTP ${resp.code} ${resp.body?.string()}"))
            val json = JsonParser.parseString(resp.body?.string()).asJsonObject
            val text = json.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString
            return Result.success(text.trim())
        }
    }

    private fun gemini(userMessage: String, config: AiConfig): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
        val body = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf("role" to "user", "parts" to listOf(mapOf("text" to userMessage)))
            )))
            add("systemInstruction", gson.toJsonTree(
                mapOf("parts" to listOf(mapOf("text" to config.systemPrompt)))
            ))
            add("generationConfig", gson.toJsonTree(
                mapOf("temperature" to config.temperature, "maxOutputTokens" to config.maxTokens)
            ))
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return Result.failure(Exception("Gemini request failed: HTTP ${resp.code} ${resp.body?.string()}"))
            val json = JsonParser.parseString(resp.body?.string()).asJsonObject
            val text = json.getAsJsonArray("candidates")[0].asJsonObject
                .getAsJsonObject("content").getAsJsonArray("parts")[0].asJsonObject
                .get("text").asString
            return Result.success(text.trim())
        }
    }

    private fun anthropic(userMessage: String, config: AiConfig): Result<String> {
        val url = "https://api.anthropic.com/v1/messages"
        val body = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("max_tokens", config.maxTokens)
            addProperty("temperature", config.temperature)
            addProperty("system", config.systemPrompt)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to userMessage)
            )))
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return Result.failure(Exception("Anthropic request failed: HTTP ${resp.code} ${resp.body?.string()}"))
            val json = JsonParser.parseString(resp.body?.string()).asJsonObject
            val text = json.getAsJsonArray("content")[0].asJsonObject.get("text").asString
            return Result.success(text.trim())
        }
    }
}
