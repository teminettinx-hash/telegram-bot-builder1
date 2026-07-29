package com.botbuilder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Handles the "click a link → open Telegram → tap Start → get a file" flow.
 * Telegram supports this natively via deep links shaped like:
 *   https://t.me/<your_bot_username>?start=<payload>
 * When tapped, Telegram opens the bot and sends "/start <payload>" once the user taps Start.
 * This rule maps a payload to a file to send back automatically.
 *
 * Leave `payload` blank to define the DEFAULT rule — used when someone taps Start
 * with no link (i.e. found your bot directly and typed /start themselves).
 */
@Entity(tableName = "file_delivery_rules")
data class FileDeliveryRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,          // friendly name, e.g. "Free ebook"
    val payload: String,        // e.g. "ebook1" — blank means default/no-link welcome
    val fileUrl: String,        // direct public URL to the file (Telegram fetches it)
    val caption: String = ""    // optional message sent alongside the file
)
