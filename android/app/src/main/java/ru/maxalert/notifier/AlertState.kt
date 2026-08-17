package ru.maxalert.notifier

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The standing alert: a level, not a beep.
 *
 * It latches when a level word arrives and stays -- across a reboot, across hours without
 * network -- until the all-clear is said in the same chat or it is cleared by hand.
 * Silencing the sound does not clear it: the person has been told, the situation has not
 * changed. Anything that shows the state (the screen, the pinned notification) listens
 * here, so no two places can disagree about what colour is standing.
 */
object AlertState {

    data class State(
        val level: AlertLevel = AlertLevel.NONE,
        val chat: String = "",
        val text: String = "",
        val since: Long = 0L,
        /** The sound already rang or was stopped; the state itself is untouched. */
        val silenced: Boolean = false,
    ) {
        val active: Boolean get() = level != AlertLevel.NONE
    }

    private val listeners = mutableListOf<() -> Unit>()

    var state by mutableStateOf(State())
        private set

    fun load(context: Context) {
        val prefs = prefs(context)
        state = State(
            level = AlertLevel.fromId(prefs.getString(KEY_LEVEL, null)),
            chat = prefs.getString(KEY_CHAT, "") ?: "",
            text = prefs.getString(KEY_TEXT, "") ?: "",
            since = prefs.getLong(KEY_SINCE, 0L),
            silenced = prefs.getBoolean(KEY_SILENCED, false),
        )
    }

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners += listener }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners -= listener }
    }

    /**
     * Records the level the chat just declared. Returns true when this is a change worth
     * reacting to -- a new level, or the same level raised again after an all-clear.
     */
    fun raise(context: Context, level: AlertLevel, chat: String, text: String, time: Long): Boolean {
        val previous = state.level
        state = State(level = level, chat = chat, text = text, since = time, silenced = false)
        save(context)
        return previous != level
    }

    fun silence(context: Context) {
        if (!state.active || state.silenced) return
        state = state.copy(silenced = true)
        save(context)
    }

    /**
     * The alert that has just ended, and how long it stood.
     *
     * A cleared alert used to simply vanish, which left the one question anyone asks
     * afterwards -- "how long was that?" -- answerable only by digging through the log. Held
     * in memory rather than in prefs on purpose: it is the closing line of this sitting, not
     * a record to survive a reboot.
     */
    data class Cleared(
        val level: AlertLevel,
        val chat: String,
        val since: Long,
        val until: Long,
    )

    var lastCleared by mutableStateOf<Cleared?>(null)
        private set

    fun clear(context: Context) {
        if (!state.active) return
        lastCleared = Cleared(
            level = state.level,
            chat = state.chat,
            since = state.since,
            until = System.currentTimeMillis(),
        )
        state = State()
        save(context)
    }

    fun dismissCleared() {
        lastCleared = null
    }

    private fun save(context: Context) {
        prefs(context).edit()
            .putString(KEY_LEVEL, state.level.id)
            .putString(KEY_CHAT, state.chat)
            .putString(KEY_TEXT, state.text)
            .putLong(KEY_SINCE, state.since)
            .putBoolean(KEY_SILENCED, state.silenced)
            .apply()
        val current = synchronized(listeners) { listeners.toList() }
        current.forEach { listener -> runCatching { listener() } }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "max-alert-state"
    private const val KEY_LEVEL = "level"
    private const val KEY_CHAT = "chat"
    private const val KEY_TEXT = "text"
    private const val KEY_SINCE = "since"
    private const val KEY_SILENCED = "silenced"
}
