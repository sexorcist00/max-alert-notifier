package ru.maxalert.notifier.max

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's own connection to MAX -- the half that does not depend on MAX delivering a
 * notification. Logs in as the user (phone + SMS code), holds the websocket, and reports
 * every incoming message.
 */
class MaxClient(
    private val session: MaxSession,
    private val scope: CoroutineScope,
    private val onMessage: (IncomingMaxMessage) -> Unit,
    private val onState: (State, String?) -> Unit,
) {

    enum class State { OFFLINE, CONNECTING, NEEDS_LOGIN, ONLINE }

    class MaxError(message: String) : Exception(message)

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Map<String, Any?>>>()
    private val sequence = AtomicInteger(0)
    private val chatTitles = ConcurrentHashMap<Long, String>()

    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private var connected = CompletableDeferred<Unit>()

    val chats: Map<Long, String> get() = chatTitles

    suspend fun connect() {
        close()
        onState(State.CONNECTING, null)
        connected = CompletableDeferred()

        val request = Request.Builder()
            .url(MaxProtocol.WEBSOCKET_URL)
            .header("Origin", MaxProtocol.ORIGIN)
            .header("User-Agent", MaxProtocol.HEADER_USER_AGENT)
            .build()

        socket = http.newWebSocket(request, Listener())
        withTimeout(REQUEST_TIMEOUT_MS) { connected.await() }

        invoke(
            MaxProtocol.OP_SESSION_INIT,
            linkedMapOf(
                "userAgent" to MaxProtocol.webUserAgent(LOCALE, TimeZone.getDefault().id),
                "deviceId" to session.deviceId,
            ),
        )

        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                runCatching { invoke(MaxProtocol.OP_PING, linkedMapOf("interactive" to true)) }
                    .onFailure { Log.w(TAG, "ping failed: ${it.message}") }
            }
        }
    }

    /** Asks MAX to send an SMS code. Returns how many digits the code has. */
    suspend fun requestCode(phone: String): Int {
        val response = invoke(
            MaxProtocol.OP_AUTH_REQUEST,
            linkedMapOf("phone" to phone, "type" to "START_AUTH"),
        )
        val token = response["token"] as? String
            ?: throw MaxError("MAX не вернул токен запроса кода")
        session.phone = phone
        session.authToken = token
        return (response["codeLength"] as? Long)?.toInt() ?: 6
    }

    /** Submits the SMS code and stores the login token on success. */
    suspend fun submitCode(code: String) {
        val token = session.authToken ?: throw MaxError("Сначала запросите код")
        val response = invoke(
            MaxProtocol.OP_AUTH,
            linkedMapOf(
                "token" to token,
                "verifyCode" to code,
                "authTokenType" to "CHECK_CODE",
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val attrs = response["tokenAttrs"] as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val login = (attrs?.get("LOGIN") as? Map<String, Any?>)?.get("token") as? String

        if (login.isNullOrEmpty()) {
            if (response.containsKey("passwordChallenge")) {
                throw MaxError("На аккаунте включён пароль (2FA) — вход по одному коду не проходит")
            }
            throw MaxError("MAX не вернул токен входа")
        }
        session.loginToken = login
        session.authToken = null
    }

    /** Logs in with the stored token and remembers the chat list. */
    suspend fun login(): Boolean {
        val token = session.loginToken
        if (token.isNullOrEmpty()) {
            onState(State.NEEDS_LOGIN, null)
            return false
        }

        val response = try {
            invoke(
                MaxProtocol.OP_LOGIN,
                linkedMapOf(
                    "token" to token,
                    "chatsCount" to 40,
                    "interactive" to true,
                    "chatsSync" to -1,
                    "contactsSync" to -1,
                    "presenceSync" to -1,
                    "draftsSync" to -1,
                ),
            )
        } catch (error: MaxError) {
            Log.w(TAG, "login rejected: ${error.message}")
            session.loginToken = null
            onState(State.NEEDS_LOGIN, error.message)
            return false
        }

        rememberChats(response["chats"])
        onState(State.ONLINE, null)
        return true
    }

    fun close() {
        pingJob?.cancel()
        pingJob = null
        runCatching { socket?.close(1000, null) }
        socket = null
        pending.values.forEach { it.completeExceptionally(MaxError("соединение закрыто")) }
        pending.clear()
    }

    private suspend fun invoke(opcode: Int, payload: Map<String, Any?>): Map<String, Any?> {
        val active = socket ?: throw MaxError("нет соединения")
        val seq = sequence.getAndIncrement() % 0x10000
        val answer = CompletableDeferred<Map<String, Any?>>()
        pending[seq] = answer

        val frame = MaxProtocol.encode(opcode, seq, payload).toByteString()
        if (!active.send(frame)) {
            pending.remove(seq)
            throw MaxError("не удалось отправить запрос")
        }

        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { answer.await() }
        } finally {
            pending.remove(seq)
        }
    }

    private fun rememberChats(chats: Any?) {
        val list = chats as? List<*> ?: return
        list.forEach { item ->
            val chat = item as? Map<*, *> ?: return@forEach
            val id = (chat["id"] as? Long) ?: return@forEach
            val title = chat["title"] as? String ?: return@forEach
            if (title.isNotEmpty()) chatTitles[id] = title
        }
    }

    private fun handleFrame(raw: ByteArray) {
        val frame = MaxProtocol.decode(raw) ?: return

        val waiting = pending.remove(frame.seq)
        if (waiting != null && frame.cmd != MaxProtocol.CMD_EVENT) {
            if (frame.cmd == MaxProtocol.CMD_ERROR) {
                val message = (frame.payload["localizedMessage"] as? String)
                    ?: (frame.payload["message"] as? String)
                    ?: (frame.payload["error"] as? String)
                    ?: "ошибка MAX"
                waiting.completeExceptionally(MaxError(message))
            } else {
                waiting.complete(frame.payload)
            }
            return
        }

        if (frame.opcode == MaxProtocol.OP_NOTIF_MESSAGE) {
            parseMessage(frame.payload)?.let(onMessage)
        }
    }

    /**
     * The event carries the message either inline or nested under "message"; both shapes are
     * accepted rather than guessed at.
     */
    private fun parseMessage(payload: Map<String, Any?>): IncomingMaxMessage? {
        @Suppress("UNCHECKED_CAST")
        val nested = payload["message"] as? Map<String, Any?>
        val body = nested ?: payload
        val chatId = (payload["chatId"] as? Long) ?: (body["chatId"] as? Long) ?: return null
        val text = body["text"] as? String ?: ""

        return IncomingMaxMessage(
            chatId = chatId,
            chatTitle = chatTitles[chatId] ?: "",
            senderId = (body["sender"] as? Long) ?: 0L,
            messageId = (body["id"] as? String)
                ?: (body["id"] as? Long)?.toString()
                ?: (body["time"] as? Long)?.toString()
                ?: "",
            text = text,
            time = (body["time"] as? Long) ?: System.currentTimeMillis(),
        )
    }

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleFrame(bytes.toByteArray())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text.toByteArray())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "websocket failed: ${t.message}")
            if (!connected.isCompleted) connected.completeExceptionally(MaxError(t.message ?: "нет сети"))
            pending.values.forEach { it.completeExceptionally(MaxError(t.message ?: "обрыв связи")) }
            pending.clear()
            onState(State.OFFLINE, t.message)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onState(State.OFFLINE, reason.ifEmpty { null })
        }
    }

    private companion object {
        const val TAG = "MaxAlert"
        const val LOCALE = "ru"
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val PING_INTERVAL_MS = 30_000L
    }
}

data class IncomingMaxMessage(
    val chatId: Long,
    val chatTitle: String,
    val senderId: Long,
    val messageId: String,
    val text: String,
    val time: Long,
)
