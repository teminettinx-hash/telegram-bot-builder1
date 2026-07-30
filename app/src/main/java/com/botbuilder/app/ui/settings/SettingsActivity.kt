package com.botbuilder.app.ui.settings

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.remote.TelegramApi
import com.botbuilder.app.data.supabase.CloudSyncManager
import com.botbuilder.app.data.supabase.SupabaseAuthManager
import com.botbuilder.app.service.BotPollingService
import com.botbuilder.app.ui.auth.AuthActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

private const val ACCENT = 0xFF6C74B8.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()
private const val DANGER = 0xFFD64545.toInt()

/** The ONLY screen where the bot token is ever shown or edited.
 *  MainActivity never displays the raw token — just "Connected as @username". */
class SettingsActivity : AppCompatActivity() {

    private lateinit var secureStore: SecureStore
    private lateinit var authManager: SupabaseAuthManager
    private lateinit var cloudSync: CloudSyncManager
    private lateinit var tokenInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var syncStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(applicationContext)
        authManager = SupabaseAuthManager(secureStore)
        cloudSync = CloudSyncManager(AppDatabase.getInstance(applicationContext), secureStore)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEEF7.toInt())
        }
        val padding = dp(20)

        val title = TextView(this).apply {
            text = "Settings"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }
        val subtitle = TextView(this).apply {
            text = "Your bot token stays on this device, encrypted. It's never shown on the home screen — only here."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(20))
        }

        val formContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
        }

        val (tokenTil, tokenField) = filledInput("Bot token from BotFather")
        tokenInput = tokenField
        tokenInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        tokenInput.setText(secureStore.botToken ?: "")
        formContainer.addView(tokenTil)

        statusText = TextView(this).apply {
            text = secureStore.botUsername?.let { "Connected as @$it" } ?: "Not connected yet"
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(4), 0, dp(16))
        }
        formContainer.addView(statusText)

        val saveButton = MaterialButton(this).apply {
            text = "Save & Verify Token"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { saveToken() }
        }
        formContainer.addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).also { it.topMargin = dp(4) })

        val disconnectButton = MaterialButton(this).apply {
            text = "Disconnect bot"
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0x00000000)
            setTextColor(DANGER)
            setOnClickListener { confirmDisconnect() }
        }
        formContainer.addView(disconnectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).also { it.topMargin = dp(4) })

        // --- Account / Cloud Sync ---
        val accountTitle = TextView(this).apply {
            text = secureStore.supabaseUserName?.let { "Account — $it" } ?: (secureStore.supabaseUserEmail ?: "Account")
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(28), 0, dp(4))
        }
        formContainer.addView(accountTitle)

        val accountSubtitle = TextView(this).apply {
            text = "Back up your replies, commands, and AI settings so they follow you to a new device."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, 0, 0, dp(12))
        }
        formContainer.addView(accountSubtitle)

        val syncRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val backupButton = MaterialButton(this).apply {
            text = "Backup"
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(14)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { backupToCloud() }
        }
        val restoreButton = MaterialButton(this).apply {
            text = "Restore"
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(14)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0x00000000)
            strokeColor = android.content.res.ColorStateList.valueOf(ACCENT)
            strokeWidth = dp(1)
            setTextColor(ACCENT)
            setOnClickListener { restoreFromCloud() }
        }
        syncRow.addView(backupButton, LinearLayout.LayoutParams(0, dp(44), 1f).also { it.marginEnd = dp(6) })
        syncRow.addView(restoreButton, LinearLayout.LayoutParams(0, dp(44), 1f).also { it.marginStart = dp(6) })
        formContainer.addView(syncRow)

        syncStatusText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(8), 0, dp(4))
        }
        formContainer.addView(syncStatusText)

        val signOutButton = MaterialButton(this).apply {
            text = "Sign out"
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0x00000000)
            setTextColor(DANGER)
            setOnClickListener { signOut() }
        }
        formContainer.addView(signOutButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).also { it.topMargin = dp(4) })

        val scroll = ScrollView(this).apply { addView(formContainer) }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun filledInput(hintText: String): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox).apply {
            hint = hintText
            boxBackgroundColor = 0x14000000
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            boxStrokeWidth = 0
            boxStrokeWidthFocused = dp(1)
            setBoxStrokeColorStateList(android.content.res.ColorStateList.valueOf(ACCENT))
            isEndIconVisible = true
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(10) }
        }
        val edit = TextInputEditText(til.context).apply {
            setTextColor(TEXT_PRIMARY)
        }
        til.addView(edit)
        return til to edit
    }

    private fun saveToken() {
        var token = tokenInput.text.toString().trim()
        token = token
            .removePrefix("https://api.telegram.org/bot")
            .removePrefix("http://api.telegram.org/bot")
            .removePrefix("bot")
            .removePrefix("@")
            .replace(" ", "")
            .trim()

        val tokenPattern = Regex("""\d{6,10}:[A-Za-z0-9_-]{30,40}""")
        if (token.isEmpty()) {
            statusText.text = "Paste your bot token first"
            return
        }
        if (!tokenPattern.matches(token)) {
            statusText.text = "That doesn't look like a bot token. In BotFather, use /mybots → your bot → API Token."
            return
        }

        statusText.text = "Checking token with Telegram…"
        lifecycleScope.launch {
            try {
                val api = TelegramApi.create()
                val me = api.getMe(TelegramApi.getMeUrl(token))
                if (me.ok && me.result != null) {
                    secureStore.botToken = token
                    secureStore.botUsername = me.result.username
                    statusText.text = "Connected as @${me.result.username}"
                    Toast.makeText(this@SettingsActivity, "Bot token saved. Go back and tap Start to go live.", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "Telegram rejected this token — double check it in BotFather"
                }
            } catch (e: Exception) {
                val friendly = when {
                    e.message?.contains("Unable to resolve host") == true -> "No internet connection"
                    e.message?.contains("timeout", ignoreCase = true) == true -> "Telegram took too long to respond, try again"
                    else -> "Couldn't connect: ${e.message}"
                }
                statusText.text = friendly
            }
        }
    }

    private fun backupToCloud() {
        syncStatusText.text = "Backing up…"
        lifecycleScope.launch {
            val result = cloudSync.backupToCloud()
            syncStatusText.text = if (result.isSuccess) "Backed up ✓" else "Backup failed: ${result.exceptionOrNull()?.message}"
        }
    }

    private fun restoreFromCloud() {
        syncStatusText.text = "Restoring…"
        lifecycleScope.launch {
            val result = cloudSync.restoreFromCloud()
            syncStatusText.text = if (result.isSuccess) "Restored ✓" else "Restore failed: ${result.exceptionOrNull()?.message}"
        }
    }

    private fun signOut() {
        authManager.signOut()
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    private fun confirmDisconnect() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect this bot?")
            .setMessage("This stops the bot and removes the saved token from this device. You'll need to paste it again to reconnect.")
            .setPositiveButton("Disconnect") { _, _ -> disconnect() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun disconnect() {
        stopService(Intent(this, BotPollingService::class.java))
        secureStore.clearBotToken()
        secureStore.botUsername = null
        tokenInput.setText("")
        statusText.text = "Disconnected"
        Toast.makeText(this, "Bot disconnected", Toast.LENGTH_SHORT).show()
    }
}
