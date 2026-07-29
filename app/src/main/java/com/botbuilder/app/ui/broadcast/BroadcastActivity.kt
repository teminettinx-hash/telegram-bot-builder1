package com.botbuilder.app.ui.broadcast

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botbuilder.app.billing.PlanLimits
import com.botbuilder.app.billing.currentTier
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.remote.SendMessageBody
import com.botbuilder.app.data.remote.TelegramApi
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ACCENT = 0xFF6C74B8.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()
private const val BRANDING_TAG = "\n\n— Sent via Bot Builder"

/** Only messages people who have already messaged the bot at least once —
 *  that's a Telegram Bot API rule, not a choice this app is making. */
class BroadcastActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var secureStore: SecureStore
    private lateinit var messageInput: TextInputEditText
    private lateinit var contactsText: TextView
    private lateinit var statusText: TextView
    private lateinit var sendButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getInstance(applicationContext)
        secureStore = SecureStore(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEEF7.toInt())
        }
        val padding = dp(20)

        val title = TextView(this).apply {
            text = "Broadcast"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }
        val subtitle = TextView(this).apply {
            text = "Send one message to everyone who has messaged your bot before."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(16))
        }

        contactsText = TextView(this).apply {
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(12))
        }

        val formContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
        }

        val til = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox).apply {
            hint = "Your broadcast message"
            boxBackgroundColor = 0x14000000
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            boxStrokeWidth = 0
            boxStrokeWidthFocused = dp(1)
            setBoxStrokeColorStateList(android.content.res.ColorStateList.valueOf(ACCENT))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        messageInput = TextInputEditText(til.context).apply {
            setTextColor(TEXT_PRIMARY)
            minLines = 4
            isSingleLine = false
        }
        til.addView(messageInput)
        formContainer.addView(til)

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(10), 0, dp(10))
        }
        formContainer.addView(statusText)

        sendButton = MaterialButton(this).apply {
            text = "Send broadcast"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { confirmAndSend() }
        }
        formContainer.addView(sendButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).also { it.topMargin = dp(6) })

        val scroll = ScrollView(this).apply { addView(formContainer) }

        root.addView(title)
        root.addView(subtitle)
        root.addView(contactsText)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        refreshContactCount()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshContactCount() {
        lifecycleScope.launch {
            val tier = secureStore.currentTier()
            val cap = PlanLimits.maxBroadcastRecipients(tier)
            val total = db.knownContactDao().getAll().size
            val capText = if (cap == Int.MAX_VALUE) "no cap" else "capped at $cap on your ${tier.displayName} plan"
            contactsText.text = "$total people have messaged your bot ($capText)."
        }
    }

    private fun confirmAndSend() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Write a message first", Toast.LENGTH_SHORT).show()
            return
        }
        if (secureStore.botToken.isNullOrBlank()) {
            Toast.makeText(this, "Connect a bot token in Settings first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val tier = secureStore.currentTier()
            val cap = PlanLimits.maxBroadcastRecipients(tier)
            val allContacts = db.knownContactDao().getAll()
            val recipients = if (cap == Int.MAX_VALUE) allContacts else allContacts.take(cap)
            val truncated = allContacts.size > recipients.size

            val message = if (PlanLimits.showsBroadcastBranding(tier)) text + BRANDING_TAG else text

            AlertDialog.Builder(this@BroadcastActivity)
                .setTitle("Send to ${recipients.size} people?")
                .setMessage(
                    if (truncated)
                        "Your ${tier.displayName} plan caps broadcasts at $cap recipients — ${allContacts.size - recipients.size} people won't receive this. Upgrade to reach everyone."
                    else
                        "This will message everyone who has ever contacted your bot. This can't be undone."
                )
                .setPositiveButton("Send") { _, _ -> sendBroadcast(recipients.map { it.chatId }, message) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun sendBroadcast(chatIds: List<Long>, message: String) {
        sendButton.isEnabled = false
        lifecycleScope.launch {
            val token = secureStore.botToken ?: return@launch
            val api = TelegramApi.create()
            var sent = 0
            var failed = 0

            chatIds.forEachIndexed { index, chatId ->
                statusText.text = "Sending… ${index + 1}/${chatIds.size}"
                try {
                    val response = api.sendMessage(TelegramApi.sendMessageUrl(token), SendMessageBody(chatId = chatId, text = message))
                    if (response.ok) sent++ else failed++
                } catch (e: Exception) {
                    failed++
                }
                delay(40) // small pacing gap so we don't trip Telegram's flood limits
            }

            statusText.text = "Done — sent to $sent, failed for $failed."
            sendButton.isEnabled = true
        }
    }
}
