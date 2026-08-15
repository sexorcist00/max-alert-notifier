package ru.maxalert.notifier

import android.content.Context
import android.util.Log

/**
 * One decision path for both sources -- MAX's notifications and our own connection.
 *
 * Whoever saw the message first calls this; the gate makes sure the second one does not act
 * on the same message twice.
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
                val standing = AlertState.state.level
                AlarmController.stop(context)
                AlertState.clear(context)
                log(
                    context, chat, text, time, false,
                    if (standing.active()) "отбой «${verdict.keyword}» снял ${standing.title} · $source"
                    else "слово отбоя «${verdict.keyword}», тревоги не было · $source",
                )
            }

            is Verdict.Match -> {
                if (!gate.allow(key, settings.cooldownSeconds)) {
                    log(context, chat, text, time, false, "повтор того же сообщения · $source")
                    return
                }
                val reason = verdict.keyword?.let { "слово «$it»" } ?: "любое сообщение в чате"
                val changed = AlertState.raise(context, verdict.level, chat, text, time)
                log(context, chat, text, time, verdict.level.rings, "${verdict.level.title}: $reason · $source")
                Log.i("MaxAlert", "${verdict.level.id}: $chat / $reason")

                // Only red makes noise; the yellows are a state to be seen, not heard.
                if (verdict.level.rings && changed && ring) {
                    AlarmController.trigger(context, settings, chat, text)
                }
            }
        }
    }

    /**
     * Replays what was said while the phone was offline, oldest first, and rings only if the
     * run ends with a red alert still standing -- an alarm called off in the meantime must
     * not wake anyone.
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

        val state = AlertState.state
        if (state.level.rings && !state.silenced) {
            AlarmController.trigger(context, settings, state.chat, state.text)
        }
    }

    private fun AlertLevel.active(): Boolean = this != AlertLevel.NONE

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
