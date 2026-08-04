package com.botbuilder.app.billing

/** The three tiers from the pricing table. FREE is the default with no purchase. */
enum class PlanTier(val displayName: String) {
    FREE("Free"),
    PRO("Pro"),
    MAX("Max");

    companion object {
        fun fromProductId(productId: String): PlanTier? = when (productId) {
            BillingConfig.PRO_PRODUCT_ID -> PRO
            BillingConfig.MAX_PRODUCT_ID -> MAX
            else -> null
        }
    }
}

/** Product / base-plan IDs you must create in Play Console → Monetize → Subscriptions.
 *  Base plan IDs are the "yearly ~30% off" discount plans under each product. */
object BillingConfig {
    const val PRO_PRODUCT_ID = "bot_builder_pro"
    const val MAX_PRODUCT_ID = "bot_builder_max"

    const val PRO_MONTHLY_BASE_PLAN = "pro-monthly"
    const val PRO_YEARLY_BASE_PLAN = "pro-yearly"
    const val MAX_MONTHLY_BASE_PLAN = "max-monthly"
    const val MAX_YEARLY_BASE_PLAN = "max-yearly"
}

/** TESTER BUILD FLAG — when true, every limit check below returns "unlimited" no matter
 *  what tier the account is on, and broadcast branding never shows. This does NOT change
 *  anything else about the app — same features, same screens, just no caps while testing.
 *  Flip back to false before this build goes to anyone but you. */
object TesterMode {
    const val UNRESTRICTED = true
}

/** Numeric caps straight from the pricing table. "Bots connected" is intentionally
 *  omitted — this build only supports one bot per install, so it isn't enforceable
 *  yet (would require multi-bot architecture, tracked as separate future work). */
object PlanLimits {
    fun maxReplyRules(tier: PlanTier): Int {
        if (TesterMode.UNRESTRICTED) return Int.MAX_VALUE
        return when (tier) {
            PlanTier.FREE -> 10
            PlanTier.PRO -> 250
            PlanTier.MAX -> 2000
        }
    }

    fun maxMessagesPerMonth(tier: PlanTier): Int {
        if (TesterMode.UNRESTRICTED) return Int.MAX_VALUE
        return when (tier) {
            PlanTier.FREE -> 100
            PlanTier.PRO -> 5000
            PlanTier.MAX -> 50000
        }
    }

    fun maxAiRepliesPerMonth(tier: PlanTier): Int {
        if (TesterMode.UNRESTRICTED) return Int.MAX_VALUE
        return when (tier) {
            PlanTier.FREE -> 20
            PlanTier.PRO -> 1000
            PlanTier.MAX -> 10000
        }
    }

    fun maxBroadcastRecipients(tier: PlanTier): Int {
        if (TesterMode.UNRESTRICTED) return Int.MAX_VALUE
        return when (tier) {
            PlanTier.FREE -> 50
            PlanTier.PRO -> Int.MAX_VALUE
            PlanTier.MAX -> Int.MAX_VALUE
        }
    }

    fun showsBroadcastBranding(tier: PlanTier): Boolean {
        if (TesterMode.UNRESTRICTED) return false
        return tier == PlanTier.FREE
    }
}

/** Reads the locally cached tier (kept in sync by BillingManager). */
fun com.botbuilder.app.data.local.SecureStore.currentTier(): PlanTier =
    try {
        PlanTier.valueOf(planTier ?: "FREE")
    } catch (e: IllegalArgumentException) {
        PlanTier.FREE
    }
