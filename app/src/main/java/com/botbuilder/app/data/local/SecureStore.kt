package com.botbuilder.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Holds the bot token and AI API key. Never logged, never exported in plaintext. */
class SecureStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var botToken: String?
        get() = prefs.getString(KEY_BOT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_BOT_TOKEN, value).apply()

    var botUsername: String?
        get() = prefs.getString(KEY_BOT_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_BOT_USERNAME, value).apply()

    var aiApiKey: String?
        get() = prefs.getString(KEY_AI_KEY, null)
        set(value) = prefs.edit().putString(KEY_AI_KEY, value).apply()

    var aiProvider: String?
        get() = prefs.getString(KEY_AI_PROVIDER, "gemini")
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()

    var aiBaseUrl: String?
        get() = prefs.getString(KEY_AI_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_AI_BASE_URL, value).apply()

    var aiModel: String?
        get() = prefs.getString(KEY_AI_MODEL, null)
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    var aiSystemPrompt: String?
        get() = prefs.getString(KEY_AI_SYSTEM_PROMPT, "You are a friendly, helpful assistant.")
        set(value) = prefs.edit().putString(KEY_AI_SYSTEM_PROMPT, value).apply()

    var aiTemperature: Float
        get() = prefs.getFloat(KEY_AI_TEMP, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_AI_TEMP, value).apply()

    var aiMaxTokens: Int
        get() = prefs.getInt(KEY_AI_MAX_TOKENS, 512)
        set(value) = prefs.edit().putInt(KEY_AI_MAX_TOKENS, value).apply()

    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    // --- Billing / plan entitlement ---

    /** Cached tier name ("FREE", "PRO", "MAX"). Source of truth is Play Billing;
     *  this is just a fast local cache so enforcement doesn't need a network round trip
     *  on every single incoming message. */
    var planTier: String?
        get() = prefs.getString(KEY_PLAN_TIER, "FREE")
        set(value) = prefs.edit().putString(KEY_PLAN_TIER, value).apply()

    // --- Usage metering (resets monthly) ---

    /** "yyyy-MM" of the last time usage counters were reset. */
    var usageMonthKey: String?
        get() = prefs.getString(KEY_USAGE_MONTH, null)
        set(value) = prefs.edit().putString(KEY_USAGE_MONTH, value).apply()

    var messagesThisMonth: Int
        get() = prefs.getInt(KEY_MESSAGES_THIS_MONTH, 0)
        set(value) = prefs.edit().putInt(KEY_MESSAGES_THIS_MONTH, value).apply()

    var aiRepliesThisMonth: Int
        get() = prefs.getInt(KEY_AI_REPLIES_THIS_MONTH, 0)
        set(value) = prefs.edit().putInt(KEY_AI_REPLIES_THIS_MONTH, value).apply()

    /** True once the "you've hit this month's limit" notice has already been sent,
     *  so the bot doesn't spam that message on every subsequent incoming text. */
    var messageLimitNoticeSent: Boolean
        get() = prefs.getBoolean(KEY_MESSAGE_LIMIT_NOTICE_SENT, false)
        set(value) = prefs.edit().putBoolean(KEY_MESSAGE_LIMIT_NOTICE_SENT, value).apply()

    /** Call before checking/incrementing usage. Resets counters when the calendar month changed. */
    fun rolloverUsageIfNewMonth() {
        val currentKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        if (usageMonthKey != currentKey) {
            usageMonthKey = currentKey
            messagesThisMonth = 0
            aiRepliesThisMonth = 0
            messageLimitNoticeSent = false
        }
    }

    fun clearBotToken() = prefs.edit().remove(KEY_BOT_TOKEN).apply()

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_BOT_USERNAME = "bot_username"
        private const val KEY_AI_KEY = "ai_api_key"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_SYSTEM_PROMPT = "ai_system_prompt"
        private const val KEY_AI_TEMP = "ai_temperature"
        private const val KEY_AI_MAX_TOKENS = "ai_max_tokens"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_PLAN_TIER = "plan_tier"
        private const val KEY_USAGE_MONTH = "usage_month"
        private const val KEY_MESSAGES_THIS_MONTH = "messages_this_month"
        private const val KEY_AI_REPLIES_THIS_MONTH = "ai_replies_this_month"
        private const val KEY_MESSAGE_LIMIT_NOTICE_SENT = "message_limit_notice_sent"
    }
}
