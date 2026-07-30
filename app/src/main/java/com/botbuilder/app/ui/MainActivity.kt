package com.botbuilder.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.botbuilder.app.R
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.databinding.ActivityMainBinding
import com.botbuilder.app.service.BotPollingService
import com.botbuilder.app.service.BotStatusBus
import com.botbuilder.app.ui.ai.AiSettingsActivity
import com.botbuilder.app.ui.broadcast.BroadcastActivity
import com.botbuilder.app.ui.commands.BotCommandsActivity
import com.botbuilder.app.ui.files.FileLinksActivity
import com.botbuilder.app.ui.plans.PlansActivity
import com.botbuilder.app.ui.replies.AutoRepliesActivity
import com.botbuilder.app.ui.settings.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var secureStore: SecureStore
    private lateinit var db: AppDatabase

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: bot still works without notification permission, just less visible */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            secureStore = SecureStore(applicationContext)
            db = AppDatabase.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e("MainActivity", "Init failed", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        requestNotificationPermissionIfNeeded()

        binding.imageAvatar.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonStartBot.setOnClickListener { startBot() }
        binding.buttonStopBot.setOnClickListener { stopBot() }
        binding.buttonManageReplies.setOnClickListener {
            startActivity(Intent(this, AutoRepliesActivity::class.java))
        }
        binding.buttonFileLinks.setOnClickListener {
            startActivity(Intent(this, FileLinksActivity::class.java))
        }
        binding.buttonAiSettings.setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }
        binding.buttonBotCommands.setOnClickListener {
            startActivity(Intent(this, BotCommandsActivity::class.java))
        }
        binding.buttonBroadcast.setOnClickListener {
            startActivity(Intent(this, BroadcastActivity::class.java))
        }
        binding.buttonPlans.setOnClickListener {
            startActivity(Intent(this, PlansActivity::class.java))
        }

        setupBottomNav()

        if (BotPollingService.isRunning(applicationContext)) {
            BotStatusBus.update(BotStatusBus.State.Running(System.currentTimeMillis()))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BotStatusBus.state.collect { state -> renderStatus(state) }
            }
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        binding.bottomNav.menu.findItem(R.id.nav_dashboard).isChecked = true
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_replies -> {
                    startActivity(Intent(this, AutoRepliesActivity::class.java))
                    false // don't visually select — we're navigating away
                }
                R.id.nav_ai -> {
                    startActivity(Intent(this, AiSettingsActivity::class.java))
                    false
                }
                R.id.nav_more -> {
                    showMoreSheet()
                    false
                }
                else -> false
            }
        }
    }

    private fun showMoreSheet() {
        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(16), dp(8), dp(24))
        }

        val items = listOf(
            Triple("Bot Settings", R.drawable.ic_settings) { startActivity(Intent(this, SettingsActivity::class.java)) },
            Triple("Bot Commands", R.drawable.ic_terminal) { startActivity(Intent(this, BotCommandsActivity::class.java)) },
            Triple("File Links", R.drawable.ic_link) { startActivity(Intent(this, FileLinksActivity::class.java)) },
            Triple("Broadcast", R.drawable.ic_campaign) { startActivity(Intent(this, BroadcastActivity::class.java)) },
            Triple("Plans & Usage", R.drawable.ic_analytics) { startActivity(Intent(this, PlansActivity::class.java)) }
        )

        items.forEach { (label, iconRes, action) ->
            val row = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, container, false) as TextView
            row.text = label
            row.textSize = 16f
            row.setPadding(dp(16), dp(14), dp(16), dp(14))
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
            row.compoundDrawablePadding = dp(16)
            row.setOnClickListener {
                sheet.dismiss()
                action()
            }
            container.addView(row)
        }

        sheet.setContentView(container)
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startBot() {
        if (secureStore.botToken.isNullOrBlank()) {
            binding.textStatus.text = "Connect a bot token in Settings first"
            return
        }
        try {
            val intent = Intent(this, BotPollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
            binding.textStatus.text = "Couldn't start bot: ${e.message}"
        }
    }

    private fun stopBot() {
        stopService(Intent(this, BotPollingService::class.java))
        BotStatusBus.update(BotStatusBus.State.Stopped)
    }

    /** Reflects the bot's actual lifecycle, and keeps whichever action is available
     *  styled as the primary (filled) pill, per the design — not just enabled/disabled. */
    private fun renderStatus(state: BotStatusBus.State) {
        val username = secureStore.botUsername
        val prefix = username?.let { "@$it — " } ?: ""

        val (statusLabel, canStart, canStop) = when (state) {
            is BotStatusBus.State.Stopped -> Triple(if (username != null) "${prefix}Stopped" else "Not connected", true, false)
            is BotStatusBus.State.Starting -> Triple("${prefix}Starting…", false, true)
            is BotStatusBus.State.Running -> Triple("${prefix}Running — listening for messages", false, true)
            is BotStatusBus.State.Error -> Triple("${prefix}Problem: ${state.message}", false, true)
        }

        binding.textStatus.text = statusLabel
        binding.buttonStartBot.isEnabled = canStart
        binding.buttonStopBot.isEnabled = canStop
        stylePillButton(binding.buttonStartBot, active = canStart)
        stylePillButton(binding.buttonStopBot, active = canStop)
    }

    private fun stylePillButton(button: com.google.android.material.button.MaterialButton, active: Boolean) {
        val bg = ContextCompat.getColor(this, if (active) R.color.md_primary else R.color.md_surface_variant)
        val fg = ContextCompat.getColor(this, if (active) R.color.md_on_primary else R.color.md_on_surface_variant)
        button.backgroundTintList = ColorStateList.valueOf(bg)
        button.setTextColor(fg)
        button.iconTint = ColorStateList.valueOf(fg)
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            try {
                val ruleCount = db.replyRuleDao().count()
                binding.textReplyCountChip.text = "$ruleCount Auto-reply rules"
                binding.textAiStatusChip.text = if (secureStore.aiEnabled) "AI Replies: ON" else "AI Replies: OFF"

                if (secureStore.botToken.isNullOrBlank()) {
                    binding.textStatus.text = "Not connected — tap Settings to add your bot token"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Status refresh failed", e)
            }
        }
    }
}
