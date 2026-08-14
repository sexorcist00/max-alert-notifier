package ru.maxalert.notifier

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last notifications the watcher saw, and what it decided about each one.
 *
 * This is the setup tool: it shows the exact chat title MAX puts in its notifications,
 * which is what the chat filter has to match.
 */
object EventLog {

    data class Entry(
        val time: Long,
        val chat: String,
        val text: String,
        val fired: Boolean,
        val reason: String,
    )

    private const val PREFS = "max-alert-log"
    private const val KEY = "entries"
    private const val LIMIT = 30

    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    fun load(context: Context) {
        val raw = prefs(context).getString(KEY, null) ?: return
        val parsed = mutableListOf<Entry>()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parsed += Entry(
                time = item.optLong("time"),
                chat = item.optString("chat"),
                text = item.optString("text"),
                fired = item.optBoolean("fired"),
                reason = item.optString("reason"),
            )
        }
        entries = parsed
    }

    fun add(context: Context, entry: Entry) {
        entries = (listOf(entry) + entries).take(LIMIT)
        val array = JSONArray()
        entries.forEach { item ->
            array.put(
                JSONObject()
                    .put("time", item.time)
                    .put("chat", item.chat)
                    .put("text", item.text)
                    .put("fired", item.fired)
                    .put("reason", item.reason)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    fun clear(context: Context) {
        entries = emptyList()
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
