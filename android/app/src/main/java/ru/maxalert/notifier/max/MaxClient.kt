package ru.maxalert.notifier.max

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's own connection to MAX -- the half that does not depend on MAX delivering a
 * notification. Logs in as the user (phone + SMS code), holds the socket, and reports every
 * incoming message.
 */
class MaxClient(
    private val session: MaxSession,
    private val scope: CoroutineScope,
    private val onMessage: (IncomingMaxMessage) -> Unit,
    private val onState: (State, String?) -> Unit,
) {

    enum class State { OFFLINE, CONNECTING, NEEDS_LOGIN, ONLINE }

    /**
     * [fromServer] separates "MAX said no" from "the network broke".
     *
     * The difference decides whether the login token survives: a dropped socket, a VPN
     * switch or a dead cell must never cost the user another SMS, while an actual rejection
     * has to be believed.
     */
    class MaxError(message: String, val fromServer: Boolean = false) : Exception(message)

    private val transport = MaxTransport()

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Map<String, Any?>>>()
    private val sequence = AtomicInteger(0)
    private val chatTitles = ConcurrentHashMap<Long, String>()

    private var pingJob: Job? = null
    private var readerJob: Job? = null
    private var callsSeed: Long? = null

    val chats: Map<Long, String> get() = chatTitles

    suspend fun connect() {
        close()
        onState(State.CONNECTING, null)

        withContext(Dispatchers.IO) { transport.connect() }
        readerJob = scope.launch(Dispatchers.IO) { readLoop() }

        val handshake = invoke(
            MaxProtocol.OP_SESSION_INIT,
            linkedMapOf(
                "mt_instanceid" to session.instanceId,
                "userAgent" to MaxProtocol.androidUserAgent(LOCALE, TimeZone.getDefault().id),
                "clientSessionId" to session.clientSessionId,
                "deviceId" to session.deviceId,
            ),
        )
        callsSeed = handshake["callsSeed"] as? Long

        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                runCatching { invoke(MaxProtocol.OP_PING, linkedMapOf("interactive" to true)) }
                    .onFailure { Log.w(TAG, "ping failed: ${it.message}") }
            }
        }
    }

    private suspend fun readLoop() {
        while (scope.isActive) {
            val frame = try {
                transport.readFrame()
            } catch (error: IOException) {
                fail(error.message ?: "обрыв связи")
                return
            }
            if (frame == null) {
                fail("соединение закрыто сервером")
                return
            }
            runCatching { handleFrame(frame) }
                .onFailure { Log.w(TAG, "bad frame: ${it.message}") }
        }
    }

    private fun fail(reason: String) {
        pending.values.forEach { it.completeExceptionally(MaxError(reason)) }
        pending.clear()
        onState(State.OFFLINE, reason)
    }

    /** Asks MAX to send an SMS code. Returns how many digits the code has. */
    suspend fun requestCode(phone: String): Int {
        val seed = callsSeed
            ?: throw MaxError("Сервер MAX не прислал seed рукопожатия — попробуйте ещё раз")

        val response = invoke(
            MaxProtocol.OP_AUTH_REQUEST,
            linkedMapOf(
                "phone" to phone,
                "type" to "START_AUTH",
                // Without this the server answers captcha.validation-failed and sends nothing.
                "mode" to MaxFingerprint.generate(seed, session.deviceId),
            ),
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
            if (error.fromServer) {
                // MAX itself refused the token: it is spent, and only a new SMS will help.
                Log.w(TAG, "login rejected by server: ${error.message}")
                session.loginToken = null
                onState(State.NEEDS_LOGIN, error.message)
            } else {
                // Network trouble -- keep the session and try again later.
                Log.w(TAG, "login failed on transport: ${error.message}")
                onState(State.OFFLINE, error.message)
            }
            return false
        }

        rememberChats(response["chats"])
        onState(State.ONLINE, null)
        return true
    }

    /**
     * Messages posted to [chatId] after [sinceTime], oldest first.
     *
     * This is what makes the alarm a state rather than an event: after the phone was offline
     * we replay what was said and decide from the whole run, not from one notification.
     */
    suspend fun history(chatId: Long, sinceTime: Long, limit: Int = 200): List<IncomingMaxMessage> {
        val response = invoke(
            MaxProtocol.OP_CHAT_HISTORY,
            linkedMapOf(
                "chatId" to chatId,
                "from" to sinceTime,
                "forward" to limit,
                "backward" to 0,
                "getMessages" to true,
                "getChat" to false,
                "interactive" to false,
            ),
        )

        val messages = response["messages"] as? List<*> ?: return emptyList()
        return messages.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val body = item as? Map<String, Any?> ?: return@mapNotNull null
            val time = (body["time"] as? Long) ?: return@mapNotNull null
            if (time <= sinceTime) return@mapNotNull null
            IncomingMaxMessage(
                chatId = chatId,
                chatTitle = chatTitles[chatId] ?: "",
                senderId = (body["sender"] as? Long) ?: 0L,
                messageId = (body["id"] as? String) ?: (body["id"] as? Long)?.toString() ?: time.toString(),
                text = body["text"] as? String ?: "",
                time = time,
            )
        }.sortedBy { message -> message.time }
    }

    fun close() {
        pingJob?.cancel()
        pingJob = null
        readerJob?.cancel()
        readerJob = null
        transport.close()
        pending.values.forEach { it.completeExceptionally(MaxError("соединение закрыто")) }
        pending.clear()
    }

    private suspend fun invoke(opcode: Int, payload: Map<String, Any?>): Map<String, Any?> {
        if (!transport.connected) throw MaxError("нет соединения")
        val seq = sequence.getAndIncrement() % 0x10000
        val answer = CompletableDeferred<Map<String, Any?>>()
        pending[seq] = answer

        try {
            withContext(Dispatchers.IO) { transport.send(MaxProtocol.encode(opcode, seq, payload)) }
        } catch (error: IOException) {
            pending.remove(seq)
            throw MaxError(error.message ?: "не удалось отправить запрос")
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
                waiting.completeExceptionally(MaxError(message, fromServer = true))
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
