package com.botbuilder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_logs")
data class ConversationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val username: String?,
    val incomingMessage: String,
    val botReply: String,
    val repliedByAi: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
