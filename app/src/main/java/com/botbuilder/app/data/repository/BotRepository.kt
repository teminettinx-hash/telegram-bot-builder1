package com.botbuilder.app.data.repository

import com.botbuilder.app.billing.PlanLimits
import com.botbuilder.app.billing.currentTier
import com.botbuilder.app.data.local.*
import com.botbuilder.app.data.remote.*

class BotRepository(
    private val db: AppDatabase,
    private val secureStore: SecureStore,
    private val telegramApi: TelegramApi = TelegramApi.create(),
    private val aiProvider: AiProvider = AiProviderRouter()
) {
    private var lastUpdateOffset: Long? = null

    /** Fetches new updates via long polling. Call in a loop from the foreground service. */
    suspend fun pollOnce(): List<TgUpdate> {
        val token = secureStore.botToken ?: return emptyList()
        val response = telegramApi.getUpdates(TelegramApi.getUpdatesUrl(token), lastUpdateOffset)
        val updates = response.result.orEmpty()
        if (updates.isNotEmpty()) {
            lastUpdateOffset = updates.maxOf { it.updateId } + 1
        }
        return updates
    }

    /** Pushes the saved command list to Telegram so it shows in the native "/" popup menu.
     *  Must be called any time commands are added/edited/removed, or the popup won't update. */
    suspend fun syncCommandsToTelegram(): Result<Unit> {
        val token = secureStore.botToken ?: return Result.failure(IllegalStateException("No bot token saved"))
        return try {
            val commands = db.botCommandDao().getAllOnce()
            val body = SetMyCommandsBody(commands.map { TgBotCommand(command = it.command, description = it.description) })
            val response = telegramApi.setMyCommands(TelegramApi.setMyCommandsUrl(token), body)
            if (response.ok) Result.success(Unit) else Result.failure(Exception("Telegram rejected the command list"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Full pipeline: /start deep links -> match rules -> AI fallback -> send -> log. */
    suspend fun handleUpdate(update: TgUpdate) {
        val message = update.message ?: return
        val text = message.text ?: return
        val chatId = message.chat.id
        val username = message.chat.username ?: message.chat.firstName

        // Track contact for broadcast eligibility
        db.knownContactDao().upsert(KnownContact(chatId = chatId, username = username, lastSeen = System.currentTimeMillis()))

        // Handle deep-link file delivery: someone tapped a link shaped like
        // https://t.me/<bot>?start=<payload>, which sends "/start <payload>" here.
        if (text.startsWith("/start")) {
            handleStartCommand(text, chatId, username)
            return
        }

        // --- Plan enforcement: monthly message cap ---
        secureStore.rolloverUsageIfNewMonth()
        val tier = secureStore.currentTier()
        val messageCap = PlanLimits.maxMessagesPerMonth(tier)
        if (secureStore.messagesThisMonth >= messageCap) {
            if (!secureStore.messageLimitNoticeSent) {
                secureStore.messageLimitNoticeSent = true
                val token = secureStore.botToken ?: return
                val notice = "This bot has reached its message limit for this month (${tier.displayName} plan: $messageCap/mo). It'll resume next month, or upgrade for a higher limit."
                telegramApi.sendMessage(TelegramApi.sendMessageUrl(token), SendMessageBody(chatId = chatId, text = notice))
            }
            return
        }
        secureStore.messagesThisMonth += 1

        val rules = db.replyRuleDao().getAllOnce()

        // Registered slash commands (the ones that show in Telegram's "/" popup) take priority
        if (text.startsWith("/")) {
            val commandWord = text.removePrefix("/").split(" ").first().lowercase()
            val command = db.botCommandDao().findByCommand(commandWord)
            if (command != null) {
                val token = secureStore.botToken ?: return
                telegramApi.sendMessage(TelegramApi.sendMessageUrl(token), SendMessageBody(chatId = chatId, text = command.answer))
                db.conversationLogDao().insert(
                    ConversationLog(chatId = chatId, username = username, incomingMessage = text, botReply = command.answer, repliedByAi = false)
                )
                return
            }
        }

        val matchedRule = rules.firstOrNull { it.matches(text) }

        // --- Plan enforcement: monthly AI reply cap ---
        val aiCapReached = secureStore.aiRepliesThisMonth >= PlanLimits.maxAiRepliesPerMonth(tier)

        val (replyText, usedAi) = when {
            matchedRule != null -> matchedRule.answer to false
            secureStore.aiEnabled && !aiCapReached -> {
                val aiReply = callAi(text, rules)
                if (aiReply != null) secureStore.aiRepliesThisMonth += 1
                (aiReply ?: "Sorry, I didn't understand that.") to (aiReply != null)
            }
            else -> "Sorry, I didn't understand that." to false
        }

        val token = secureStore.botToken ?: return
        telegramApi.sendMessage(TelegramApi.sendMessageUrl(token), SendMessageBody(chatId = chatId, text = replyText))

        db.conversationLogDao().insert(
            ConversationLog(
                chatId = chatId,
                username = username,
                incomingMessage = text,
                botReply = replyText,
                repliedByAi = usedAi
            )
        )
    }

    /** /start with no payload = plain "found the bot" open. /start <payload> = came from a shared link. */
    private suspend fun handleStartCommand(text: String, chatId: Long, username: String?) {
        val token = secureStore.botToken ?: return
        val payload = text.removePrefix("/start").trim()

        val rule = db.fileDeliveryRuleDao().findByPayload(payload)
            ?: db.fileDeliveryRuleDao().findByPayload("") // fall back to the default rule if no link-specific one matches

        if (rule != null && rule.fileUrl.isNotBlank()) {
            telegramApi.sendDocument(
                TelegramApi.sendDocumentUrl(token),
                SendDocumentBody(chatId = chatId, document = rule.fileUrl, caption = rule.caption.ifBlank { null })
            )
            db.conversationLogDao().insert(
                ConversationLog(chatId = chatId, username = username, incomingMessage = text, botReply = "[sent file: ${rule.label}]", repliedByAi = false)
            )
        } else {
            val welcome = "Welcome! Send me a message and I'll help."
            telegramApi.sendMessage(TelegramApi.sendMessageUrl(token), SendMessageBody(chatId = chatId, text = welcome))
            db.conversationLogDao().insert(
                ConversationLog(chatId = chatId, username = username, incomingMessage = text, botReply = welcome, repliedByAi = false)
            )
        }
    }

    /** Builds the AI system prompt by appending the user's saved reply rules as reference data,
     *  so AI answers (e.g. prices) stay in sync with what the user already configured. */
    private fun buildSystemPrompt(baseRules: List<ReplyRule>): String {
        val base = secureStore.aiSystemPrompt ?: "You are a helpful assistant."
        if (baseRules.isEmpty()) return base

        val reference = baseRules.joinToString("\n") { rule ->
            "- ${rule.label}: ${rule.answer}"
        }
        return """
            $base

            Reference information — use this to answer accurately when relevant:
            $reference

            Only use the above when relevant. If the question isn't covered, answer naturally and helpfully.
        """.trimIndent()
    }

    private suspend fun callAi(userMessage: String, rules: List<ReplyRule>): String? {
        val apiKey = secureStore.aiApiKey ?: return null
        val config = AiConfig(
            provider = secureStore.aiProvider ?: "gemini",
            apiKey = apiKey,
            baseUrl = secureStore.aiBaseUrl,
            model = secureStore.aiModel ?: defaultModelFor(secureStore.aiProvider),
            temperature = secureStore.aiTemperature,
            maxTokens = secureStore.aiMaxTokens,
            systemPrompt = buildSystemPrompt(rules)
        )
        return aiProvider.getReply(userMessage, config).getOrNull()
    }

    private fun defaultModelFor(provider: String?): String = when (provider) {
        "openai" -> "gpt-4o-mini"
        "gemini" -> "gemini-1.5-flash"
        "anthropic" -> "claude-3-5-haiku-20241022"
        "openrouter" -> "openai/gpt-4o-mini"
        else -> "gpt-4o-mini"
    }
}
