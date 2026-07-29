package com.botbuilder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MatchType { EXACT, CONTAINS, STARTS_WITH, ENDS_WITH }

/**
 * A single auto-reply rule. Keywords are stored as a delimited string
 * (pipe-separated) so a single rule can trigger on multiple phrases,
 * e.g. "price|cost|how much" all pointing to the same answer.
 */
@Entity(tableName = "reply_rules")
data class ReplyRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,                 // friendly name, e.g. "Pricing"
    val keywords: String,              // pipe-separated keyword list
    val answer: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val caseSensitive: Boolean = false,
    val priority: Int = 0,             // lower number = checked first
    val buttonsJson: String? = null    // optional serialized inline keyboard
) {
    fun keywordList(): List<String> = keywords.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    /** Returns true if the incoming message matches ANY of this rule's keywords. */
    fun matches(incoming: String): Boolean {
        val text = if (caseSensitive) incoming else incoming.lowercase()
        return keywordList().any { rawKeyword ->
            val kw = if (caseSensitive) rawKeyword else rawKeyword.lowercase()
            when (matchType) {
                MatchType.EXACT -> text == kw
                MatchType.CONTAINS -> text.contains(kw)
                MatchType.STARTS_WITH -> text.startsWith(kw)
                MatchType.ENDS_WITH -> text.endsWith(kw)
            }
        }
    }
}
