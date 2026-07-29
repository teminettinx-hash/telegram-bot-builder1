package com.botbuilder.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.databinding.ActivityMainBinding
import com.botbuilder.app.service.BotPollingService
import com.botbuilder.app.service.BotStatusBus
import com.botbuilder.app.ui.settings.SettingsActivity
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
            // Surface init failures instead of the app silently doing nothing
            Log.e("MainActivity", "Init failed", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        requestNotificationPermissionIfNeeded()

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonStartBot.setOnClickListener { startBot() }
        binding.buttonStopBot.setOnClickListener { stopBot() }
        binding.buttonManageReplies.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.replies.AutoRepliesActivity::class.java))
        }
        binding.buttonFileLinks.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.files.FileLinksActivity::class.java))
        }
        binding.buttonAiSettings.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.ai.AiSettingsActivity::class.java))
        }
        binding.buttonBotCommands.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.commands.BotCommandsActivity::class.java))
        }
        binding.buttonBroadcast.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.broadcast.BroadcastActivity::class.java))
        }
        binding.buttonPlans.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.plans.PlansActivity::class.java))
        }

        // Seed the initial paint from the real OS-level service state (covers the case
        // where the activity was recreated after process death but the service is still
        // alive), then switch to live updates below.
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
    }

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

    /** Reflects the bot's actual lifecycle instead of a fixed "starting…" string. */
    private fun renderStatus(state: BotStatusBus.State) {
        val username = secureStore.botUsername
        val prefix = username?.let { "@$it — " } ?: ""

        when (state) {
            is BotStatusBus.State.Stopped -> {
                binding.textStatus.text = if (username != null) "${prefix}Stopped" else "Not connected"
                binding.buttonStartBot.isEnabled = true
                binding.buttonStopBot.isEnabled = false
            }
            is BotStatusBus.State.Starting -> {
                binding.textStatus.text = "${prefix}Starting…"
                binding.buttonStartBot.isEnabled = false
                binding.buttonStopBot.isEnabled = true
            }
            is BotStatusBus.State.Running -> {
                binding.textStatus.text = "${prefix}Running — listening for messages"
                binding.buttonStartBot.isEnabled = false
                binding.buttonStopBot.isEnabled = true
            }
            is BotStatusBus.State.Error -> {
                binding.textStatus.text = "${prefix}Problem: ${state.message}"
                binding.buttonStartBot.isEnabled = false
                binding.buttonStopBot.isEnabled = true
            }
        }
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            try {
                val ruleCount = db.replyRuleDao().count()
                binding.textRuleCount.text = ruleCount.toString()
                binding.textAiStatus.text = if (secureStore.aiEnabled) "ON" else "OFF"

                // If we're not connected at all yet, make sure that shows even before
                // the very first BotStatusBus emission (which defaults to Stopped anyway).
                if (secureStore.botToken.isNullOrBlank()) {
                    binding.textStatus.text = "Not connected — tap Settings to add your bot token"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Status refresh failed", e)
            }
        }
    }
}
