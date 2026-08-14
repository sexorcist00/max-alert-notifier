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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maxalert.notifier.max.IncomingMaxMessage
import ru.maxalert.notifier.max.MaxClient
import ru.maxalert.notifier.max.MaxSession
import kotlinx.coroutines.Dispatchers

/**
 * Holds the app's own connection to MAX.
 *
 * A foreground service, because this is the source that must keep working when MAX itself
 * sends no notification -- and a foreground service with a visible notification is the only
 * thing EMUI reliably leaves alone.
 */
class MaxWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = TriggerGate()
    private lateinit var settingsStore: SettingsStore
    private lateinit var session: MaxSession
    private var client: MaxClient? = null
    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        session = MaxSession(this)
        EventLog.load(this)
        startForeground(NOTIFICATION_ID, statusNotification(getString(R.string.watch_connecting)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) loop = scope.launch { runForever() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.close()
        scope.cancel()
        status = "выключено"
        running = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runForever() {
        running = true
        var backoff = 5_000L
        while (true) {
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
                updateStatus(getString(R.string.watch_online))
                backoff = 5_000L
                // The socket stays open; the client reports messages through onMaxMessage.
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
        // The matcher filters by source package; messages from our own connection are, by
        // definition, from MAX, so they carry the configured package.
        val incoming = IncomingNotification(
            packageName = settings.sourcePackage,
            chat = message.chatTitle,
            text = message.text,
        )

        when (val verdict = Matcher.evaluate(incoming, settings)) {
            is Verdict.Skip -> EventLog.add(
                this,
                EventLog.Entry(System.currentTimeMillis(), message.chatTitle, message.text, false, "${verdict.reason} (своё подключение)"),
            )

            is Verdict.Match -> {
                if (!gate.allow("max:${message.chatId}:${message.messageId}", settings.cooldownSeconds)) return
                val reason = verdict.keyword?.let { "совпало слово «$it»" } ?: "любое сообщение в чате"
                EventLog.add(
                    this,
                    EventLog.Entry(System.currentTimeMillis(), message.chatTitle, message.text, true, "$reason (своё подключение)"),
                )
                AlarmController.trigger(this, settings, message.chatTitle, message.text)
            }
        }
    }

    private fun updateStatus(text: String) {
        status = text
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, statusNotification(text))
    }

    private fun statusNotification(text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_watch), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "MaxAlert"
        private const val CHANNEL_ID = "max-alert-watch"
        private const val NOTIFICATION_ID = 4241
        private const val MAX_BACKOFF_MS = 300_000L
        private const val NEEDS_LOGIN_RETRY_MS = 60_000L
        const val ACTION_STOP = "ru.maxalert.notifier.STOP_WATCH"

        var status by mutableStateOf("выключено")
            private set
        var running by mutableStateOf(false)
            private set

        fun start(context: Context) {
            val intent = Intent(context, MaxWatchService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MaxWatchService::class.java).setAction(ACTION_STOP))
        }
    }
}
