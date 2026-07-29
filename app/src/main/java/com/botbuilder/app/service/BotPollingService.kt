package com.botbuilder.app.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.repository.BotRepository
import kotlinx.coroutines.*

class BotPollingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: BotRepository
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        val secureStore = SecureStore(applicationContext)
        repository = BotRepository(db, secureStore)
        BotStatusBus.update(BotStatusBus.State.Starting)
        startForeground(NOTIF_ID, buildNotification("Bot is starting…"))

        // Best-effort: make sure Telegram's "/" popup reflects whatever commands
        // are saved locally, in case the user forgot to hit "Sync to Telegram".
        scope.launch {
            repository.syncCommandsToTelegram()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingJob?.isActive != true) {
            pollingJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun CoroutineScope.pollLoop() {
        var consecutiveFailures = 0
        while (isActive) {
            try {
                val updates = repository.pollOnce()
                updates.forEach { repository.handleUpdate(it) }

                if (consecutiveFailures > 0 || BotStatusBus.state.value !is BotStatusBus.State.Running) {
                    BotStatusBus.update(BotStatusBus.State.Running(System.currentTimeMillis()))
                    updateNotification("Bot is running", "Listening for Telegram messages")
                }
                consecutiveFailures = 0
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    val friendly = when {
                        e.message?.contains("Unable to resolve host") == true -> "No internet connection"
                        e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true ->
                            "Telegram rejected the bot token"
                        else -> e.message ?: "Connection error"
                    }
                    BotStatusBus.update(BotStatusBus.State.Error(friendly))
                    updateNotification("Bot has a problem", friendly)
                }
                // Exponential backoff on repeated failures, capped at 60s
                val backoffMs = (2000L * consecutiveFailures).coerceAtMost(60_000L)
                delay(backoffMs)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "bot_polling_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Bot Running", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(text)
            .setContentText("Telegram Bot Builder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val channelId = "bot_polling_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        BotStatusBus.update(BotStatusBus.State.Stopped)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1001

        /** Lets any screen check if the service is currently alive, e.g. right after
         *  process death when the in-memory BotStatusBus has reset to Stopped. */
        fun isRunning(context: android.content.Context): Boolean {
            val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == BotPollingService::class.java.name }
        }
    }
}
