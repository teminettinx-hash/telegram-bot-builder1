package com.botbuilder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A command that shows up in Telegram's native "/" menu (the popup showing
 * /newbot, /deletebot etc. that BotFather has). This requires actually
 * registering the list with Telegram via setMyCommands — just replying to
 * "/price" as text does NOT make it appear in that menu.
 */
@Entity(tableName = "bot_commands")
data class BotCommand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,      // no leading slash, lowercase, e.g. "price"
    val description: String,  // shown next to the command in the popup, e.g. "See our price list"
    val answer: String        // what the bot replies when this command is used
)
