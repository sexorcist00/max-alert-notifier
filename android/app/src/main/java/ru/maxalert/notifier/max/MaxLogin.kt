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
