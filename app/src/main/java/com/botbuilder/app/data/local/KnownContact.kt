package com.botbuilder.app.data.local

import androidx.room.Entity

/** Every chat_id that has ever messaged the bot — required for Broadcast,
 *  since Telegram bots can only message users who messaged them first. */
@Entity(tableName = "known_contacts", primaryKeys = ["chatId"])
data class KnownContact(
    val chatId: Long,
    val username: String?,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)
