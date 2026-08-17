package ru.maxalert.notifier

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.maxalert.notifier.max.MaxLogin

/**
 * A single clock for everything the screen claims about the connection.
 *
 * Any composable that reads [tick] is re-drawn when it moves, so permission state, login
 * state and connection state are re-read together instead of each one showing whatever it
 * happened to capture when it was first composed.
 */
object AppRefresh {

    var tick by mutableIntStateOf(0)
        private set

    fun bump() {
        tick++
    }
}

/**
 * The result of actually asking the server whether the session works.
 *
 * Kept in one place, with the moment it was measured, so no two parts of the screen can
 * disagree and nothing can present an old answer as the current one.
 */
object ConnectionProbe {

    var busy by mutableStateOf(false)
        private set
    var ok by mutableStateOf<Boolean?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var checkedAt by mutableStateOf(0L)
        private set

    /**
     * Asks the server whether the session works -- but only when nothing else is using it.
     *
     * A second login with the same account and device id makes MAX drop the first one, and the
     * first one is the duty connection. That is what produced a screen reading "Сессия жива,
     * чатов видно: 7" directly above "Read error … Software caused connection abort": pressing
     * check was itself what broke the watch, and the reconnect that followed looked like a
     * network fault. While the duty connection is up it *is* the answer, so no second one is
     * opened.
     */
    suspend fun run(context: Context) {
        if (busy) return
        busy = true
        message = null

        if (MaxWatchService.online) {
            ok = true
            message = "Сессия жива: дежурное подключение на связи"
        } else {
            runCatching { MaxLogin.checkSession(context) }
                .onSuccess { chats ->
                    ok = true
                    message = "Сессия жива, чатов видно: $chats"
                }
                .onFailure { error ->
                    ok = false
                    message = "Сессия недоступна: ${ConnectionTrouble.humanize(error.message)}"
                }
        }

        checkedAt = System.currentTimeMillis()
        busy = false
        AppRefresh.bump()
    }

    /** Forgets the verdict when the account changes -- an old "жива" would be a lie. */
    fun reset() {
        ok = null
        message = null
        checkedAt = 0L
        AppRefresh.bump()
    }

    fun ageText(now: Long): String? {
        if (checkedAt == 0L) return null
        val seconds = ((now - checkedAt).coerceAtLeast(0L)) / 1000
        return when {
            seconds < 60 -> "проверено только что"
            seconds < 3600 -> "проверено ${seconds / 60} мин назад"
            else -> "проверено ${seconds / 3600} ч назад"
        }
    }
}
