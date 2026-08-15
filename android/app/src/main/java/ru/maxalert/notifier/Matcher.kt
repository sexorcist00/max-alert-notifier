package ru.maxalert.notifier

import java.util.ArrayDeque

/**
 * Which level, if any, does this message declare?
 *
 * Deliberately free of Android imports: this is the part that decides, and the part unit
 * tests can run on the JVM without a device.
 */
data class IncomingNotification(
    val packageName: String,
    val chat: String,
    val text: String,
)

sealed interface Verdict {
    /** [keyword] is null when the settings say "any message in this chat". */
    data class Match(val level: AlertLevel, val keyword: String?) : Verdict

    /** The all-clear: this message lifts whatever is standing. */
    data class Deactivate(val keyword: String) : Verdict

    data class Skip(val reason: String) : Verdict
}

object Matcher {

    fun evaluate(notification: IncomingNotification, settings: AlertSettings): Verdict {
        if (!settings.enabled) return Verdict.Skip("сторож выключен")
        if (notification.packageName != settings.sourcePackage) return Verdict.Skip("другое приложение")

        val chatFilter = settings.chatFilter.trim()
        if (chatFilter.isNotEmpty() && !notification.chat.contains(chatFilter, ignoreCase = true)) {
            return Verdict.Skip("другой чат")
        }

        val text = notification.text
        if (text.isBlank()) return Verdict.Skip("пустой текст")

        // The all-clear wins over every level: "тревога отменена" must not raise anything.
        settings.deactivationKeywords
            .firstOrNull { keyword -> text.contains(keyword, ignoreCase = true) }
            ?.let { keyword -> return Verdict.Deactivate(keyword) }

        // Worst level first: a message naming both colours means the worse one.
        settings.keywords
            .firstOrNull { keyword -> text.contains(keyword, ignoreCase = true) }
            ?.let { keyword -> return Verdict.Match(AlertLevel.RED, keyword) }

        settings.yellowHighKeywords
            .firstOrNull { keyword -> text.contains(keyword, ignoreCase = true) }
            ?.let { keyword -> return Verdict.Match(AlertLevel.YELLOW_HIGH, keyword) }

        settings.yellowKeywords
            .firstOrNull { keyword -> text.contains(keyword, ignoreCase = true) }
            ?.let { keyword -> return Verdict.Match(AlertLevel.YELLOW, keyword) }

        // No words configured at all: every message in the watched chat is a red alert.
        if (settings.keywords.isEmpty() &&
            settings.yellowHighKeywords.isEmpty() &&
            settings.yellowKeywords.isEmpty()
        ) {
            return Verdict.Match(AlertLevel.RED, null)
        }

        return Verdict.Skip("нет ключевого слова")
    }

    fun parseKeywords(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Stops one incident from ringing twice: the same message is never acted on again, and a
 * successful trigger silences the next [cooldownSeconds].
 */
class TriggerGate(private val clock: () -> Long = System::currentTimeMillis) {

    private val seen = ArrayDeque<String>()
    private val seenKeys = HashSet<String>()
    private var lastFired: Long? = null

    fun allow(key: String, cooldownSeconds: Int): Boolean {
        if (!seenKeys.add(key)) return false
        seen.addLast(key)
        if (seen.size > REMEMBER) {
            seenKeys.remove(seen.removeFirst())
        }

        val now = clock()
        val previous = lastFired
        if (previous != null && now - previous < cooldownSeconds * 1000L) return false

        lastFired = now
        return true
    }

    private companion object {
        const val REMEMBER = 256
    }
}
