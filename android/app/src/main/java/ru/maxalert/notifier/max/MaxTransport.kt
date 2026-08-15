package ru.maxalert.notifier.max

import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A plain TLS socket to MAX, speaking the same framed protocol as [MaxProtocol].
 *
 * Not a websocket, and that is the whole point: the websocket endpoint answers
 * `session init forbidden` to anything but the web client, and only the mobile client is
 * allowed to prove itself with a fingerprint instead of solving a captcha. Verified against
 * the live server: web identity + websocket reaches `captcha.validation-failed` on the SMS
 * request, android identity over the websocket is refused outright.
 */
class MaxTransport {

    private var socket: SSLSocket? = null
    private var input: DataInputStream? = null

    val connected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } == true

    @Throws(IOException::class)
    fun connect(host: String = HOST, port: Int = PORT, timeoutMs: Int = 20_000) {
        close()
        val plain = Socket()
        plain.connect(InetSocketAddress(host, port), timeoutMs)
        plain.tcpNoDelay = true

        val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(plain, host, port, true) as SSLSocket
        secure.soTimeout = 0
        secure.startHandshake()

        socket = secure
        input = DataInputStream(secure.inputStream.buffered())
    }

    @Throws(IOException::class)
    fun send(frame: ByteArray) {
        val stream = socket?.outputStream ?: throw IOException("нет соединения")
        synchronized(this) {
            stream.write(frame)
            stream.flush()
        }
    }

    /** Blocks until a whole frame arrives. Returns null when the peer closed the connection. */
    @Throws(IOException::class)
    fun readFrame(): ByteArray? {
        val stream = input ?: return null
        val header = ByteArray(MaxProtocol.HEADER_SIZE)
        try {
            stream.readFully(header)
        } catch (end: java.io.EOFException) {
            return null
        }

        val length = ((header[7].toInt() and 0xFF) shl 16) or
            ((header[8].toInt() and 0xFF) shl 8) or
            (header[9].toInt() and 0xFF)
        if (length > MAX_FRAME) throw IOException("кадр слишком большой: $length")

        val body = ByteArray(length)
        if (length > 0) stream.readFully(body)
        return header + body
    }

    fun close() {
        runCatching { input?.close() }
        runCatching { socket?.close() }
        input = null
        socket = null
    }

    private companion object {
        const val HOST = "api.oneme.ru"
        const val PORT = 443
        const val MAX_FRAME = 10 * 1024 * 1024
    }
}
