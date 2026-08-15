package ru.maxalert.notifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maxalert.notifier.max.IncomingMaxMessage
import ru.maxalert.notifier.max.MaxClient
import ru.maxalert.notifier.max.MaxSession

/**
 * Duty mode.
 *
 * While the watcher is on, this service holds a pinned notification -- the honest signal
 * that the phone is actually on watch, and the fastest way to stand down. It also owns the
 * app's own connection to MAX, which is the source that keeps working when MAX sends no
 * notification of its own.
 */
class MaxWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsStore: SettingsStore
    private lateinit var session: MaxSession
    private var client: MaxClient? = null
    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        session = MaxSession(this)
        EventLog.load(this)
        AlertState.load(this)
        startForeground(NOTIFICATION_ID, dutyNotification(getString(R.string.watch_idle)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STAND_DOWN -> {
                // The one-tap off switch from the shade: stop watching, do not just hide.
                settingsStore.save(settingsStore.load().copy(enabled = false))
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (loop == null) loop = scope.launch { runForever() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.close()
        scope.cancel()
        running = false
        status = "выключено"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runForever() {
        running = true
        var backoff = 5_000L

        while (true) {
            val settings = settingsStore.load()
            if (!settings.useDirectConnection || !session.loggedIn) {
                updateStatus(getString(R.string.watch_notifications_only))
                delay(IDLE_POLL_MS)
                continue
            }

            try {
                val active = MaxClient(
                    session = session,
                    scope = scope,
                    onMessage = ::onMaxMessage,
                    onState = ::onClientState,
                )
                client = active
                active.connect()
                if (!active.login()) {
                    updateStatus(getString(R.string.watch_needs_login))
                    delay(NEEDS_LOGIN_RETRY_MS)
                    continue
                }

                catchUp(active, settings)
                updateStatus(getString(R.string.watch_online))
                backoff = 5_000L
                while (running) delay(60_000)
            } catch (error: Throwable) {
                Log.w(TAG, "max connection failed: ${error.message}")
                updateStatus(getString(R.string.watch_reconnect, backoff / 1000))
                client?.close()
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /**
     * Reads what was said in the watched chats while we were away and lets the pipeline decide
     * from the whole run -- so an alarm raised during an outage still rings, and one that was
     * called off in the meantime does not.
     */
    private suspend fun catchUp(client: MaxClient, settings: AlertSettings) {
        val since = session.lastSeenTime
        if (since == 0L) {
            session.lastSeenTime = System.currentTimeMillis()
            return
        }

        val filter = settings.chatFilter.trim()
        val chats = client.chats.filter { (_, title) ->
            filter.isEmpty() || title.contains(filter, ignoreCase = true)
        }
        if (chats.isEmpty()) return

        val missed = mutableListOf<Triple<String, String, Long>>()
        chats.forEach { (chatId, title) ->
            runCatching { client.history(chatId, since) }
                .onSuccess { messages ->
                    messages.forEach { message -> missed += Triple(title, message.text, message.time) }
                }
                .onFailure { Log.w(TAG, "history for $chatId failed: ${it.message}") }
        }

        if (missed.isEmpty()) return
        Log.i(TAG, "replaying ${missed.size} missed messages")
        AlertPipeline.replay(this, settings, missed.sortedBy { it.third }, "пропущенное")
        session.lastSeenTime = missed.maxOf { it.third }
    }

    private fun onClientState(state: MaxClient.State, detail: String?) {
        when (state) {
            MaxClient.State.ONLINE -> updateStatus(getString(R.string.watch_online))
            MaxClient.State.CONNECTING -> updateStatus(getString(R.string.watch_connecting))
            MaxClient.State.NEEDS_LOGIN -> updateStatus(getString(R.string.watch_needs_login))
            MaxClient.State.OFFLINE -> updateStatus(detail ?: getString(R.string.watch_offline))
        }
    }

    private fun onMaxMessage(message: IncomingMaxMessage) {
        val settings = settingsStore.load()
        session.lastSeenTime = maxOf(session.lastSeenTime, message.time)
        AlertPipeline.handle(
            context = this,
            settings = settings,
            chat = message.chatTitle,
            text = message.text,
            time = if (message.time > 0) message.time else System.currentTimeMillis(),
            key = "max:${message.chatId}:${message.messageId}",
            source = "своё подключение",
        )
        refreshNotification()
    }

    private fun updateStatus(text: String) {
        status = text
        refreshNotification()
    }

    private fun refreshNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, dutyNotification(status))
    }

    private fun dutyNotification(text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_watch),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val standDown = PendingIntent.getService(
            this,
            2,
            Intent(this, MaxWatchService::class.java).setAction(ACTION_STAND_DOWN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alert = AlertState.state
        val title = if (alert.active) {
            getString(R.string.duty_alert_active)
        } else {
            getString(R.string.duty_title)
        }
        val body = if (alert.active) {
            "${alert.chat}: ${alert.text}".take(200)
        } else {
            text
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_alert, getString(R.string.duty_stand_down), standDown)
            .build()
    }

    companion object {
        private const val TAG = "MaxAlert"
        private const val CHANNEL_ID = "max-alert-watch"
        private const val NOTIFICATION_ID = 4241
        private const val MAX_BACKOFF_MS = 300_000L
        private const val NEEDS_LOGIN_RETRY_MS = 60_000L
        private const val IDLE_POLL_MS = 30_000L

        const val ACTION_STOP = "ru.maxalert.notifier.STOP_WATCH"
        const val ACTION_STAND_DOWN = "ru.maxalert.notifier.STAND_DOWN"

        var status by mutableStateOf("выключено")
            private set
        var running by mutableStateOf(false)
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MaxWatchService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MaxWatchService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
