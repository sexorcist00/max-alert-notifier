package ru.maxalert.notifier

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The watcher. Android binds this service itself once the user grants notification access,
 * and rebinds it after a reboot or a crash -- which is why the app needs no background
 * service of its own and survives what kills a Termux script.
 */
class AlertListenerService : NotificationListenerService() {

    private lateinit var settingsStore: SettingsStore

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        EventLog.load(this)
        AlertState.load(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings = settingsStore.load()
        if (sbn.packageName != settings.sourcePackage) return
        if (isNoise(sbn)) return

        AlertPipeline.handle(
            context = this,
            settings = settings,
            chat = extractChat(sbn),
            text = extractText(sbn),
            time = sbn.postTime,
            key = "notification:${sbn.key}:${sbn.postTime}",
            source = "уведомление МАКСа",
        )
    }

    /** Group summaries and ongoing notifications repeat what the real message already said. */
    private fun isNoise(sbn: StatusBarNotification): Boolean {
        val flags = sbn.notification.flags
        return flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    private fun extractChat(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        return extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
    }

    private fun extractText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            if (lines.isNotEmpty()) return lines.joinToString("\n") { it.toString() }
        }
        return extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
    }
}
