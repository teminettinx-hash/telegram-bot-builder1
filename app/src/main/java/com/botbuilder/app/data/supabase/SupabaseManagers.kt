package com.botbuilder.app.data.supabase

import com.botbuilder.app.data.local.*

class SupabaseAuthManager(private val secureStore: SecureStore) {
    private val authApi = SupabaseAuthApi.create()

    fun isSignedIn(): Boolean = secureStore.supabaseAccessToken != null

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> {
        return safeSupabaseCall {
            val response = authApi.signUp(
                SupabaseConfig.ANON_KEY,
                SignUpBody(email = email, password = password, data = mapOf("name" to name))
            )
            saveSession(response, fallbackName = name)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return safeSupabaseCall {
            val response = authApi.signIn(SupabaseConfig.ANON_KEY, SignInBody(email = email, password = password))
            saveSession(response, fallbackName = null)
        }
    }

    fun signOut() {
        secureStore.clearSupabaseSession()
    }

    private fun saveSession(response: AuthResponse, fallbackName: String?) {
        if (response.accessToken == null || response.user == null) {
            throw IllegalStateException("Sign-in succeeded but no session was returned — check that 'Confirm email' is off in Supabase for instant sign-in.")
        }
        secureStore.supabaseAccessToken = response.accessToken
        secureStore.supabaseRefreshToken = response.refreshToken
        secureStore.supabaseUserId = response.user.id
        secureStore.supabaseUserEmail = response.user.email
        secureStore.supabaseUserName = (response.user.userMetadata?.get("name") as? String) ?: fallbackName
    }
}

/**
 * Syncs bot CONFIGURATION (replies, commands, AI settings) to Supabase.
 * Deliberately NOT synced: bot token and AI API key — those stay local-only
 * in EncryptedSharedPreferences for security.
 */
class CloudSyncManager(
    private val db: AppDatabase,
    private val secureStore: SecureStore
) {
    private val syncApi = SupabaseSyncApi.create()

    private fun bearer() = "Bearer ${secureStore.supabaseAccessToken ?: throw IllegalStateException("Not signed in")}"
    private fun userId() = secureStore.supabaseUserId ?: throw IllegalStateException("Not signed in")

    suspend fun backupToCloud(): Result<Unit> = safeSupabaseCall {
        val replies = db.replyRuleDao().getAllOnce()
        val commands = db.botCommandDao().getAllOnce()

        val row = UserConfigRow(
            user_id = userId(),
            name = secureStore.supabaseUserName,
            replies = replies.map {
                mapOf(
                    "label" to it.label, "keywords" to it.keywords, "answer" to it.answer,
                    "matchType" to it.matchType.name, "caseSensitive" to it.caseSensitive, "priority" to it.priority
                )
            },
            commands = commands.map {
                mapOf("command" to it.command, "description" to it.description, "answer" to it.answer)
            },
            file_links = emptyList(),
            ai_settings = mapOf(
                "enabled" to secureStore.aiEnabled,
                "provider" to secureStore.aiProvider,
                "model" to secureStore.aiModel,
                "temperature" to secureStore.aiTemperature,
                "maxTokens" to secureStore.aiMaxTokens,
                "systemPrompt" to secureStore.aiSystemPrompt
            )
        )
        syncApi.upsertConfig(SupabaseConfig.ANON_KEY, bearer(), row)
    }

    suspend fun restoreFromCloud(): Result<Unit> = safeSupabaseCall {
        val rows = syncApi.getConfig(SupabaseConfig.ANON_KEY, bearer(), "eq.${userId()}")
        val row = rows.firstOrNull() ?: throw Exception("No backup found for this account yet")

        row.replies.forEach {
            db.replyRuleDao().upsert(
                ReplyRule(
                    label = it["label"] as? String ?: "",
                    keywords = it["keywords"] as? String ?: "",
                    answer = it["answer"] as? String ?: "",
                    matchType = MatchType.valueOf(it["matchType"] as? String ?: "CONTAINS"),
                    caseSensitive = it["caseSensitive"] as? Boolean ?: false,
                    priority = (it["priority"] as? Double)?.toInt() ?: 0
                )
            )
        }
        row.commands.forEach {
            db.botCommandDao().upsert(
                BotCommand(
                    command = it["command"] as? String ?: "",
                    description = it["description"] as? String ?: "",
                    answer = it["answer"] as? String ?: ""
                )
            )
        }
        val ai = row.ai_settings
        secureStore.aiEnabled = ai["enabled"] as? Boolean ?: false
        secureStore.aiProvider = ai["provider"] as? String
        secureStore.aiModel = ai["model"] as? String
        secureStore.aiTemperature = (ai["temperature"] as? Double)?.toFloat() ?: 0.7f
        secureStore.aiMaxTokens = (ai["maxTokens"] as? Double)?.toInt() ?: 512
        secureStore.aiSystemPrompt = ai["systemPrompt"] as? String
    }
}
