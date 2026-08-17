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
import java.io.IOException
import kotlinx.coroutines.launch
import ru.maxalert.notifier.max.IncomingMaxMessage
import ru.maxalert.notifier.max.MaxClient
import ru.maxalert.notifier.max.MaxSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** Redraws the pinned line the moment the level changes, whichever source changed it. */
    private val alertListener: () -> Unit = { refreshNotification() }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        session = MaxSession(this)
        EventLog.load(this)
        AlertState.load(this)
        AlertState.addListener(alertListener)
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

            ACTION_CLEAR_ALERT -> {
                // Lifting the alert from the shade: silence the sound and drop the state.
                AlarmController.stop(this)
                AlertState.clear(this)
                refreshNotification()
                return START_STICKY
            }
        }

        if (loop == null) loop = scope.launch { runForever() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AlertState.removeListener(alertListener)
        client?.close()
        scope.cancel()
        running = false
        online = false
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
                // Watch the socket, do not sleep through its death. This loop used to wait in
                // 60 s steps, so a connection dropped one second in was noticed a minute
                // later -- a minute in which the second source was silently not watching.
                while (running && active.connected) delay(CONNECTION_WATCH_MS)
                if (running) throw IOException("соединение потеряно")
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
        online = state == MaxClient.State.ONLINE
        if (online) session.lastOnlineAt = System.currentTimeMillis()
        when (state) {
            MaxClient.State.ONLINE -> {
                statusDetail = null
                updateStatus(getString(R.string.watch_online))
            }
            MaxClient.State.CONNECTING -> updateStatus(getString(R.string.watch_connecting))
            MaxClient.State.NEEDS_LOGIN -> updateStatus(getString(R.string.watch_needs_login))
            MaxClient.State.OFFLINE -> {
                statusDetail = ConnectionTrouble.detail(detail)
                updateStatus(
                    if (detail == null) getString(R.string.watch_offline)
                    else ConnectionTrouble.humanize(detail)
                )
            }
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

        val clear = PendingIntent.getService(
            this,
            3,
            Intent(this, MaxWatchService::class.java).setAction(ACTION_CLEAR_ALERT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // The pinned line is the same truth as the screen. Collapsed it answers one question
        // -- what is standing right now -- and the rest only appears when expanded, so the
        // shade stays readable at a glance.
        val alert = AlertState.state
        val icon = when {
            alert.active -> R.drawable.ic_alert
            online || !settingsStore.load().useDirectConnection -> R.drawable.ic_duty
            else -> R.drawable.ic_offline
        }

        val title = if (alert.active) alert.level.title else getString(R.string.duty_title)
        val summary = if (alert.active) {
            "${alert.chat}: ${alert.text}".take(120)
        } else {
            text
        }
        val details = buildString {
            if (alert.active) {
                append(alert.text.take(400))
                append("\n\n")
                append("Чат: ${alert.chat.ifBlank { "неизвестен" }}")
                append("\nОбъявлен: ${TIME_FORMAT.format(Date(alert.since))}")
                if (alert.silenced) append("\nЗвук выключен, состояние держится")
                append("\nСвязь: $text")
            } else {
                append(text)
                append("\nИсточники: ")
                append(if (settingsStore.load().useDirectConnection) "уведомления МАКСа + своё подключение" else "уведомления МАКСа")
                if (session.lastOnlineAt > 0 && !online) {
                    append("\nПоследний контакт: ${TIME_FORMAT.format(Date(session.lastOnlineAt))}")
                }
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setLargeIcon(NotificationArt.badge(alert.level, online))
            .setColor(alert.level.colorArgb.toInt())
            .setColorized(alert.active)
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText(if (alert.active) "Оповещение о тревоге" else null)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setOngoing(true)
            // A standing alert shows when it was declared; ordinary duty has no moment worth
            // stamping, and a ticking clock there would be noise.
            .setShowWhen(alert.active)
            .setWhen(if (alert.active) alert.since else System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(if (alert.active) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)

        if (alert.active) {
            builder.addAction(R.drawable.ic_duty, "Снять", clear)
        }
        builder.addAction(R.drawable.ic_offline, getString(R.string.duty_stand_down), standDown)

        return builder.build()
    }

    companion object {
        private const val TAG = "MaxAlert"
        private const val CHANNEL_ID = "max-alert-watch"
        private const val NOTIFICATION_ID = 4241
        private const val MAX_BACKOFF_MS = 300_000L
        private const val NEEDS_LOGIN_RETRY_MS = 60_000L
        /** How often the duty loop checks that its socket is still alive. */
        private const val CONNECTION_WATCH_MS = 2_000L
        private const val IDLE_POLL_MS = 30_000L
        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

        const val ACTION_STOP = "ru.maxalert.notifier.STOP_WATCH"
        const val ACTION_STAND_DOWN = "ru.maxalert.notifier.STAND_DOWN"
        const val ACTION_CLEAR_ALERT = "ru.maxalert.notifier.CLEAR_ALERT"

        var status by mutableStateOf("выключено")
            private set

        /** The raw network wording behind [status], when there is one worth reporting. */
        var statusDetail by mutableStateOf<String?>(null)
            private set
        var running by mutableStateOf(false)
            private set
        var online by mutableStateOf(false)
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
