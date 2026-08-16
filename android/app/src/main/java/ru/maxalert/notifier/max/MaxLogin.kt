package ru.maxalert.notifier.max

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The login handshake, run beside the watching service so a half-finished login never
 * disturbs it.
 *
 * The connection is held open across the steps on purpose: MAX ties the SMS code and the
 * password to one attempt (a `trackId`), and the honest way to answer the second step is on
 * the socket that was told about the first. A dropped connection is not fatal -- the next
 * step opens a new one and tries the stored track id -- but that is the fallback, not the plan.
 */
object MaxLogin {

    private val lock = Mutex()
    private var scope: CoroutineScope? = null
    private var client: MaxClient? = null

    suspend fun requestCode(context: Context, phone: String): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            // A new code starts a new attempt: whatever was half-done is void.
            release()
            MaxSession(context).let { session ->
                session.passwordTrackId = null
                session.passwordHint = null
            }
            attempt(context).requestCode(phone)
        }
    }

    suspend fun submitCode(context: Context, code: String): AuthOutcome =
        step(context) { client -> client.submitCode(code) }

    suspend fun submitPassword(context: Context, password: String): AuthOutcome =
        step(context) { client -> client.submitPassword(password) }

    /** Drops a half-finished login -- used when the user logs out or starts over. */
    fun cancel() {
        release()
    }

    /**
     * Asks the server, right now, whether the stored session is still good for anything.
     *
     * A token can be revoked from another device or refused after a network change, and the
     * only honest way to know is to use it. Returns the number of chats the login answered
     * with; throws with the server's own wording when the session is gone.
     */
    suspend fun checkSession(context: Context): Int = withContext(Dispatchers.IO) {
        val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = MaxSession(context)
        val probe = MaxClient(session, probeScope, onMessage = {}, onState = { _, _ -> })
        try {
            probe.connect()
            if (!probe.login()) throw MaxClient.MaxError("сессия отклонена, нужен повторный вход")
            session.lastOnlineAt = System.currentTimeMillis()
            probe.chats.size
        } finally {
            probe.close()
            probeScope.cancel()
        }
    }

    private suspend fun step(
        context: Context,
        action: suspend (MaxClient) -> AuthOutcome,
    ): AuthOutcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val outcome = try {
                action(attempt(context))
            } catch (error: Throwable) {
                // The socket is the first suspect on any failure here; the next step reconnects.
                release()
                throw error
            }
            // A wrong password may be tried again on the same attempt, so the connection stays
            // for that; anything final has no use for it.
            if (outcome !is AuthOutcome.PasswordRequired && outcome !is AuthOutcome.Refused) {
                release()
            }
            outcome
        }
    }

    private suspend fun attempt(context: Context): MaxClient {
        client?.let { existing -> if (existing.connected) return existing }
        release()

        val created = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val fresh = MaxClient(MaxSession(context), created, onMessage = {}, onState = { _, _ -> })
        fresh.connect()
        scope = created
        client = fresh
        return fresh
    }

    private fun release() {
        client?.close()
        client = null
        scope?.cancel()
        scope = null
    }
}
