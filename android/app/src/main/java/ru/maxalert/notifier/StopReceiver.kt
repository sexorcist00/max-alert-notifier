package ru.maxalert.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The STOP button on the alarm notification. */
class StopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            AlarmController.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP = "ru.maxalert.notifier.STOP"
    }
}
