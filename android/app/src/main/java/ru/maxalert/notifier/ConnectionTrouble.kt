package ru.maxalert.notifier

/**
 * Turns what the network layer says into what a person needs to know.
 *
 * The screen showed this, verbatim, as the state of the watch:
 *
 *     Read error: ssl=0x7446552348: I/O error during system call,
 *     Software caused connection abort
 *
 * Every word of that is true and none of it answers "is my phone still watching". A duty
 * screen has to say what happened and what is being done about it; the original text is kept
 * as a second line for when something has to be reported to someone who can read it.
 *
 * Pure, so the wording is covered by tests rather than judged in a screenshot.
 */
object ConnectionTrouble {

    private val CYRILLIC = Regex("[а-яА-ЯёЁ]")

    /** True when [raw] is our own wording already -- those messages are written for the user. */
    fun isOurs(raw: String?): Boolean = raw != null && CYRILLIC.containsMatchIn(raw)

    fun humanize(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return "Связи нет — переподключаюсь"
        if (isOurs(text)) return text

        val lower = text.lowercase()
        return when {
            listOf("unable to resolve host", "no address associated", "nodename nor servname")
                .any { lower.contains(it) } -> "Нет интернета — сервер MAX не найден"

            listOf("timed out", "timeout").any { lower.contains(it) } ->
                "Сервер MAX не ответил вовремя — переподключаюсь"

            listOf("econnrefused", "connection refused", "failed to connect")
                .any { lower.contains(it) } -> "Сервер MAX не отвечает — переподключаюсь"

            listOf("connection abort", "connection reset", "broken pipe", "epipe", "econnreset")
                .any { lower.contains(it) } -> "Соединение оборвалось — переподключаюсь"

            listOf("ssl", "handshake", "certificate").any { lower.contains(it) } ->
                "Защищённое соединение прервалось — переподключаюсь"

            listOf("network is unreachable", "enetunreach", "ehostunreach")
                .any { lower.contains(it) } -> "Сеть недоступна — жду сеть"

            else -> "Связь потеряна — переподключаюсь"
        }
    }

    /**
     * The original text, or null when it would only repeat [humanize].
     *
     * Shown small and dim: useless to most readers, and the only thing worth having when a
     * report has to go to someone who can act on it.
     */
    fun detail(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || isOurs(text)) return null
        return text
    }
}
