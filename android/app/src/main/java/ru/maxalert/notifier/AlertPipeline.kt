package ru.maxalert.notifier

import android.content.Context
import android.util.Log

/**
 * One decision path for both sources -- MAX's notifications and our own connection.
 *
 * Whoever saw the message first calls this; the gate makes sure the second one does not
 * ring the same alert twice.
 */
object AlertPipeline {

    private val gate = TriggerGate()

    fun handle(
        context: Context,
        settings: AlertSettings,
        chat: String,
        text: String,
        time: Long,
        key: String,
        source: String,
        ring: Boolean = true,
    ) {
        val incoming = IncomingNotification(settings.sourcePackage, chat, text)

        when (val verdict = Matcher.evaluate(incoming, settings)) {
            is Verdict.Skip -> log(context, chat, text, time, false, "${verdict.reason} · $source")

            is Verdict.Deactivate -> {
                val wasActive = AlertState.state.active
                AlarmController.stop(context)
                AlertState.clear(context)
                log(
                    context, chat, text, time, false,
                    if (wasActive) "отбой по слову «${verdict.keyword}» · $source"
                    else "слово отбоя «${verdict.keyword}», тревоги не было · $source",
                )
            }

            is Verdict.Match -> {
                if (!gate.allow(key, settings.cooldownSeconds)) {
                    log(context, chat, text, time, false, "повтор того же сообщения · $source")
                    return
                }
                val reason = verdict.keyword?.let { "слово «$it»" } ?: "любое сообщение в чате"
                val isNew = AlertState.raise(context, chat, text, time)
                log(context, chat, text, time, true, "$reason · $source")
                Log.i("MaxAlert", "code red: $chat / $reason")
                if (isNew && ring) AlarmController.trigger(context, settings, chat, text)
            }
        }
    }

    /**
     * Replays what was said while the phone was offline, oldest first, and rings only if the
     * run ends with the alert still standing -- an alarm that was called off in the meantime
     * must not wake anyone.
     */
    fun replay(
        context: Context,
        settings: AlertSettings,
        messages: List<Triple<String, String, Long>>,
        source: String,
    ) {
        messages.forEach { (chat, text, time) ->
            handle(context, settings, chat, text, time, "replay:$chat:$time", source, ring = false)
        }

        // Ring once, at the end, and only if the run leaves the alert standing.
        val state = AlertState.state
        if (state.active && !state.silenced) {
            AlarmController.trigger(context, settings, state.chat, state.text)
        }
    }

    private fun log(
        context: Context,
        chat: String,
        text: String,
        time: Long,
        fired: Boolean,
        reason: String,
    ) {
        EventLog.add(context, EventLog.Entry(time, chat, text, fired, reason))
    }
}
