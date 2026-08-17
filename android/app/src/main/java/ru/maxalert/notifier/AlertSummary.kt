package ru.maxalert.notifier

/**
 * The closing line of an alert: what it was, where, and how long it stood.
 *
 * An alarm app has a peak and an end, and the end used to be nothing -- the card disappeared
 * and the screen looked as if nothing had happened. This is deliberately not a celebration:
 * nobody wants confetti for an air-raid warning. It states the facts, which is what someone
 * asks for afterwards.
 *
 * Pure, so the wording is covered by tests; the caller formats the clock times, because that
 * needs the phone's own locale.
 */
object AlertSummary {

    fun describe(level: AlertLevel, durationMs: Long, from: String, to: String, chat: String): String {
        val duration = Durations.label((durationMs.coerceAtLeast(0L) / 1000L).toInt())
        val held = if (durationMs < 60_000L) "меньше минуты" else duration
        val where = if (chat.isBlank()) "" else " в «${chat.trim()}»"
        return "Отбой$where. ${level.title} держался $held — с $from до $to."
    }
}
