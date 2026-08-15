package ru.maxalert.notifier

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Код красный" is a state, not a beep.
 *
 * It latches when the activation word arrives and stays latched -- across a reboot, across
 * hours without network -- until the deactivation word is said in the same chat or it is
 * cleared by hand. Silencing the sound does not clear it: the person has been told, the
 * situation is still on.
 */
object AlertState {

    data class State(
        val active: Boolean = false,
        val chat: String = "",
        val text: String = "",
        val since: Long = 0L,
        /** The sound already rang (or was stopped); the state itself is untouched. */
        val silenced: Boolean = false,
    )

    var state by mutableStateOf(State())
        private set

    fun load(context: Context) {
        val prefs = prefs(context)
        state = State(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            chat = prefs.getString(KEY_CHAT, "") ?: "",
            text = prefs.getString(KEY_TEXT, "") ?: "",
            since = prefs.getLong(KEY_SINCE, 0L),
            silenced = prefs.getBoolean(KEY_SILENCED, false),
        )
    }

    /** Returns true when this is a new alert, false when one was already standing. */
    fun raise(context: Context, chat: String, text: String, time: Long): Boolean {
        val alreadyActive = state.active
        state = State(active = true, chat = chat, text = text, since = time, silenced = false)
        save(context)
        return !alreadyActive
    }

    fun silence(context: Context) {
        if (!state.active) return
        state = state.copy(silenced = true)
        save(context)
    }

    fun clear(context: Context) {
        state = State()
        save(context)
    }

    private fun save(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .putString(KEY_CHAT, state.chat)
            .putString(KEY_TEXT, state.text)
            .putLong(KEY_SINCE, state.since)
            .putBoolean(KEY_SILENCED, state.silenced)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "max-alert-state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_CHAT = "chat"
    private const val KEY_TEXT = "text"
    private const val KEY_SINCE = "since"
    private const val KEY_SILENCED = "silenced"
}
