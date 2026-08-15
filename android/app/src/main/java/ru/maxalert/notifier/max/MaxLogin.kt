package ru.maxalert.notifier.max

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * The login handshake, run from the settings screen on a throwaway connection so the
 * watching service is never disturbed by a half-finished login.
 */
object MaxLogin {

    suspend fun requestCode(context: Context, phone: String): Int = withContext(Dispatchers.IO) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = MaxSession(context)
        val client = MaxClient(session, scope, onMessage = {}, onState = { _, _ -> })
        try {
            client.connect()
            client.requestCode(phone)
        } finally {
            client.close()
        }
    }

    /**
     * Asks the server, right now, whether the stored session is still good for anything.
     *
     * A token can be revoked from another device or refused after a network change, and the
     * only honest way to know is to use it. Returns the number of chats the login answered
     * with; throws with the server's own wording when the session is gone.
     */
    suspend fun checkSession(context: Context): Int = withContext(Dispatchers.IO) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = MaxSession(context)
        val client = MaxClient(session, scope, onMessage = {}, onState = { _, _ -> })
        try {
            client.connect()
            if (!client.login()) throw MaxClient.MaxError("сессия отклонена, нужен повторный вход")
            session.lastOnlineAt = System.currentTimeMillis()
            client.chats.size
        } finally {
            client.close()
        }
    }

    suspend fun submitCode(context: Context, code: String) = withContext(Dispatchers.IO) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = MaxSession(context)
        val client = MaxClient(session, scope, onMessage = {}, onState = { _, _ -> })
        try {
            client.connect()
            client.submitCode(code)
        } finally {
            client.close()
        }
    }
}
