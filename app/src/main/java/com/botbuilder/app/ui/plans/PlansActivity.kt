package com.botbuilder.app.ui.plans

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.botbuilder.app.billing.BillingConfig
import com.botbuilder.app.billing.BillingManager
import com.botbuilder.app.billing.PlanLimits
import com.botbuilder.app.billing.PlanTier
import com.botbuilder.app.billing.currentTier
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

private const val ACCENT = 0xFF6C74B8.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()

class PlansActivity : AppCompatActivity() {

    private lateinit var secureStore: SecureStore
    private lateinit var db: AppDatabase
    private lateinit var billingManager: BillingManager

    private lateinit var currentPlanText: TextView
    private lateinit var usageText: TextView
    private lateinit var offersContainer: LinearLayout

    private var proDetails: ProductDetails? = null
    private var maxDetails: ProductDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(applicationContext)
        db = AppDatabase.getInstance(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEEF7.toInt())
        }
        val padding = dp(20)

        val title = TextView(this).apply {
            text = "Plans & Usage"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }

        currentPlanText = TextView(this).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
            setPadding(padding, 0, padding, dp(6))
        }

        usageText = TextView(this).apply {
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(20))
        }

        offersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
        }

        val restoreButton = MaterialButton(this).apply {
            text = "Restore purchases"
            isAllCaps = false
            textSize = 13f
            backgroundTintList = android.content.res.ColorStateList.valueOf(0x00000000)
            setTextColor(TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setOnClickListener {
                billingManager.refreshEntitlement()
                Toast.makeText(this@PlansActivity, "Checking your Play account…", Toast.LENGTH_SHORT).show()
            }
        }

        val scroll = ScrollView(this).apply {
            addView(LinearLayout(this@PlansActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(offersContainer)
                addView(restoreButton)
            })
        }

        root.addView(title)
        root.addView(currentPlanText)
        root.addView(usageText)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        billingManager = BillingManager(applicationContext, secureStore) { tier ->
            runOnUiThread {
                renderCurrentPlan(tier)
                buildOfferButtons(tier)
            }
        }
        billingManager.startConnection {
            billingManager.queryOffers { offers ->
                proDetails = offers.firstOrNull { it.productId == BillingConfig.PRO_PRODUCT_ID }
                maxDetails = offers.firstOrNull { it.productId == BillingConfig.MAX_PRODUCT_ID }
                runOnUiThread { buildOfferButtons(secureStore.currentTier()) }
            }
        }

        renderCurrentPlan(secureStore.currentTier())
        refreshUsageText()
    }

    override fun onDestroy() {
        billingManager.endConnection()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderCurrentPlan(tier: PlanTier) {
        currentPlanText.text = "Current plan: ${tier.displayName}"
    }

    private fun refreshUsageText() {
        lifecycleScope.launch {
            val tier = secureStore.currentTier()
            secureStore.rolloverUsageIfNewMonth()
            val ruleCount = db.replyRuleDao().count()
            usageText.text = "Auto-replies: $ruleCount/${PlanLimits.maxReplyRules(tier)}   •   " +
                "Messages this month: ${secureStore.messagesThisMonth}/${PlanLimits.maxMessagesPerMonth(tier)}   •   " +
                "AI replies this month: ${secureStore.aiRepliesThisMonth}/${PlanLimits.maxAiRepliesPerMonth(tier)}"
        }
    }

    private fun buildOfferButtons(currentTier: PlanTier) {
        offersContainer.removeAllViews()

        offersContainer.addView(planCard(
            name = "Pro",
            priceLine = "\$4.99/mo or ≈ \$41.99/yr",
            bullets = listOf("250 auto-replies", "5,000 messages/mo", "1,000 AI replies/mo", "Unlimited broadcast recipients, no branding tag"),
            isCurrent = currentTier == PlanTier.PRO,
            onMonthly = { launchPurchase(proDetails, BillingConfig.PRO_MONTHLY_BASE_PLAN) },
            onYearly = { launchPurchase(proDetails, BillingConfig.PRO_YEARLY_BASE_PLAN) }
        ))

        offersContainer.addView(planCard(
            name = "Max",
            priceLine = "\$12.99/mo or ≈ \$108.99/yr",
            bullets = listOf("2,000 auto-replies", "50,000 messages/mo", "10,000 AI replies/mo", "Unlimited broadcast recipients, no branding tag"),
            isCurrent = currentTier == PlanTier.MAX,
            onMonthly = { launchPurchase(maxDetails, BillingConfig.MAX_MONTHLY_BASE_PLAN) },
            onYearly = { launchPurchase(maxDetails, BillingConfig.MAX_YEARLY_BASE_PLAN) }
        ))
    }

    private fun launchPurchase(details: ProductDetails?, basePlanId: String) {
        if (details == null) {
            Toast.makeText(this, "Plans aren't available right now — check back in a moment", Toast.LENGTH_SHORT).show()
            return
        }
        billingManager.launchPurchaseFlow(this, details, basePlanId)
    }

    private fun planCard(
        name: String,
        priceLine: String,
        bullets: List<String>,
        isCurrent: Boolean,
        onMonthly: () -> Unit,
        onYearly: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(14) }
        }

        val nameRow = TextView(this).apply {
            text = if (isCurrent) "$name  •  Your current plan" else name
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        }
        val price = TextView(this).apply {
            text = priceLine
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(2), 0, dp(8))
        }
        card.addView(nameRow)
        card.addView(price)

        bullets.forEach { bullet ->
            card.addView(TextView(this).apply {
                text = "• $bullet"
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, dp(2), 0, dp(2))
            })
        }

        if (!isCurrent) {
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            val monthlyButton = MaterialButton(this).apply {
                text = "Monthly"
                isAllCaps = false
                textSize = 13f
                cornerRadius = dp(14)
                backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
                setOnClickListener { onMonthly() }
            }
            val yearlyButton = MaterialButton(this).apply {
                text = "Yearly (save ~30%)"
                isAllCaps = false
                textSize = 13f
                cornerRadius = dp(14)
                backgroundTintList = android.content.res.ColorStateList.valueOf(0x00000000)
                setTextColor(ACCENT)
                setOnClickListener { onYearly() }
            }
            buttonRow.addView(monthlyButton, LinearLayout.LayoutParams(0, dp(44), 1f).also { it.marginEnd = dp(8) })
            buttonRow.addView(yearlyButton, LinearLayout.LayoutParams(0, dp(44), 1f).also { it.marginStart = dp(8) })
            card.addView(buttonRow)
        }

        return card
    }
}
