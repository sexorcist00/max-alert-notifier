package ru.maxalert.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.maxalert.notifier.max.MaxSession

/** Brings the watcher back after a reboot -- the phone restarting must not silence it. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsStore(context).load().enabled) return
        MaxWatchService.start(context)
    }
}
