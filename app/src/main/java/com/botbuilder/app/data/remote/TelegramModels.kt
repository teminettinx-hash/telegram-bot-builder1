package com.botbuilder.app.data.remote

import com.google.gson.annotations.SerializedName

data class TgResponse<T>(
    val ok: Boolean,
    val result: T?
)

data class TgUser(
    val id: Long,
    @SerializedName("is_bot") val isBot: Boolean,
    @SerializedName("first_name") val firstName: String,
    val username: String?
)

data class TgUpdate(
    @SerializedName("update_id") val updateId: Long,
    val message: TgMessage?
)

data class TgMessage(
    @SerializedName("message_id") val messageId: Long,
    val from: TgUser?,
    val chat: TgChat,
    val text: String?,
    val date: Long
)

data class TgChat(
    val id: Long,
    val username: String?,
    @SerializedName("first_name") val firstName: String?
)

data class SendMessageBody(
    @SerializedName("chat_id") val chatId: Long,
    val text: String,
    @SerializedName("parse_mode") val parseMode: String? = "HTML",
    @SerializedName("reply_markup") val replyMarkup: String? = null
)

data class SendDocumentBody(
    @SerializedName("chat_id") val chatId: Long,
    val document: String,   // public URL — Telegram fetches and sends the file itself
    val caption: String? = null
)

data class TgBotCommand(
    val command: String,
    val description: String
)

data class SetMyCommandsBody(
    val commands: List<TgBotCommand>
)
