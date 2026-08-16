package ru.maxalert.notifier.max

import org.msgpack.core.MessagePack
import org.msgpack.core.MessageTypeException
import org.msgpack.value.Value
import org.msgpack.value.ValueType
import java.io.ByteArrayOutputStream

/**
 * MAX's own client protocol.
 *
 * Not JSON: every frame is a 10-byte header followed by a msgpack payload. Ported from
 * PyMax (MIT) and verified byte-for-byte against a live capture of its handshake --
 * the header layout below is the reason a hand-written JSON frame gets the socket closed.
 *
 *   ver      u8    protocol version, 10
 *   cmd      u8    0 request, 1 response, 2 event, 3 error
 *   seq      u16   big endian, echoed back on the answer
 *   opcode   u16   big endian
 *   flags    u8    top byte of the length word: 0 raw, 1..0x7F LZ4 block, 0xFF zstd
 *   length   u24   payload length, big endian
 */
object MaxProtocol {

    const val WEBSOCKET_URL = "wss://api.oneme.ru/websocket"
    const val ORIGIN = "https://web.max.ru"
    const val VERSION = 10
    const val HEADER_SIZE = 10

    const val CMD_REQUEST = 0
    const val CMD_RESPONSE = 1
    const val CMD_EVENT = 2
    const val CMD_ERROR = 3

    const val OP_PING = 1
    const val OP_SESSION_INIT = 6
    const val OP_AUTH_REQUEST = 17
    const val OP_AUTH = 18
    const val OP_LOGIN = 19
    /** Second factor: the login password MAX now asks some accounts to set. */
    const val OP_AUTH_CHECK_PASSWORD = 115
    const val OP_CHAT_HISTORY = 49
    const val OP_NOTIF_MESSAGE = 128

    const val APP_VERSION = "26.7.15"
    const val SCREEN = "1080x1920 1.0x"
    const val HEADER_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/147.0.0.0 Safari/537.36"

    private const val EXT_WRAPPED_VALUE = 1

    data class Frame(val cmd: Int, val seq: Int, val opcode: Int, val payload: Map<String, Any?>)

    fun encode(opcode: Int, seq: Int, payload: Map<String, Any?>): ByteArray {
        val body = packMap(payload)
        val header = ByteArray(HEADER_SIZE)
        header[0] = VERSION.toByte()
        header[1] = CMD_REQUEST.toByte()
        header[2] = (seq ushr 8).toByte()
        header[3] = seq.toByte()
        header[4] = (opcode ushr 8).toByte()
        header[5] = opcode.toByte()
        header[6] = 0 // flags: outbound payloads are never compressed
        header[7] = (body.size ushr 16).toByte()
        header[8] = (body.size ushr 8).toByte()
        header[9] = body.size.toByte()
        return header + body
    }

    fun decode(raw: ByteArray): Frame? {
        if (raw.size < HEADER_SIZE) return null
        val cmd = raw[1].toInt() and 0xFF
        val seq = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val opcode = ((raw[4].toInt() and 0xFF) shl 8) or (raw[5].toInt() and 0xFF)
        val flags = raw[6].toInt() and 0xFF
        val length = ((raw[7].toInt() and 0xFF) shl 16) or
            ((raw[8].toInt() and 0xFF) shl 8) or
            (raw[9].toInt() and 0xFF)
        if (raw.size < HEADER_SIZE + length) return null

        val body = raw.copyOfRange(HEADER_SIZE, HEADER_SIZE + length)
        val plain = when {
            body.isEmpty() -> body
            flags == 0 -> body
            flags == 0xFF -> Zstd.decompress(body)
            flags <= 0x7F -> Lz4Block.decompress(body)
            else -> return null
        }

        @Suppress("UNCHECKED_CAST")
        val payload = unpack(plain) as? Map<String, Any?> ?: emptyMap()
        return Frame(cmd, seq, opcode, payload)
    }

    /**
     * The client introduces itself as the Android app: only that path is allowed to send the
     * fingerprint instead of solving a captcha (see [MaxFingerprint]).
     */
    fun androidUserAgent(locale: String, timezone: String): Map<String, Any?> = linkedMapOf(
        "deviceType" to "ANDROID",
        "appVersion" to MaxFingerprint.APP_VERSION,
        "osVersion" to "Android 13",
        "timezone" to timezone,
        "screen" to "405dpi 405dpi 1080x2400",
        "pushDeviceType" to "GCM",
        "arch" to MaxFingerprint.ARCH,
        "locale" to locale,
        "buildNumber" to MaxFingerprint.BUILD_NUMBER,
        "deviceName" to "Samsung SM-A525F",
        "deviceLocale" to locale,
    )

    fun webUserAgent(locale: String, timezone: String): Map<String, Any?> = linkedMapOf(
        "deviceType" to "WEB",
        "locale" to locale,
        "deviceLocale" to locale,
        "osVersion" to "Linux",
        "deviceName" to "Chrome",
        "headerUserAgent" to HEADER_USER_AGENT,
        "appVersion" to APP_VERSION,
        "screen" to SCREEN,
        "timezone" to timezone,
    )

    private fun packMap(payload: Map<String, Any?>): ByteArray {
        val packer = MessagePack.newDefaultBufferPacker()
        packValue(packer, payload)
        packer.close()
        return packer.toByteArray()
    }

    private fun packValue(packer: org.msgpack.core.MessagePacker, value: Any?) {
        when (value) {
            null -> packer.packNil()
            is String -> packer.packString(value)
            is Boolean -> packer.packBoolean(value)
            is Int -> packer.packInt(value)
            is Long -> packer.packLong(value)
            is Double -> packer.packDouble(value)
            is ByteArray -> {
                packer.packBinaryHeader(value.size)
                packer.writePayload(value)
            }

            is Map<*, *> -> {
                packer.packMapHeader(value.size)
                value.forEach { (key, item) ->
                    packer.packString(key.toString())
                    packValue(packer, item)
                }
            }

            is List<*> -> {
                packer.packArrayHeader(value.size)
                value.forEach { item -> packValue(packer, item) }
            }

            else -> packer.packString(value.toString())
        }
    }

    private fun unpack(bytes: ByteArray): Any? {
        if (bytes.isEmpty()) return emptyMap<String, Any?>()
        return try {
            MessagePack.newDefaultUnpacker(bytes).use { unpacker ->
                if (!unpacker.hasNext()) emptyMap<String, Any?>() else convert(unpacker.unpackValue())
            }
        } catch (error: MessageTypeException) {
            null
        } catch (error: java.io.IOException) {
            null
        }
    }

    private fun convert(value: Value): Any? = when (value.valueType) {
        ValueType.NIL -> null
        ValueType.BOOLEAN -> value.asBooleanValue().boolean
        ValueType.INTEGER -> value.asIntegerValue().toLong()
        ValueType.FLOAT -> value.asFloatValue().toDouble()
        ValueType.STRING -> value.asStringValue().asString()
        ValueType.BINARY -> String(value.asBinaryValue().asByteArray())
        ValueType.ARRAY -> value.asArrayValue().map(::convert)
        ValueType.MAP -> value.asMapValue().map().entries.associate { (key, item) ->
            keyOf(key) to convert(item)
        }

        ValueType.EXTENSION -> {
            val extension = value.asExtensionValue()
            // Code 1 wraps another msgpack document inside the payload.
            if (extension.type.toInt() == EXT_WRAPPED_VALUE) unpack(extension.data) else null
        }
    }

    private fun keyOf(key: Value): String = when (key.valueType) {
        ValueType.STRING -> key.asStringValue().asString()
        ValueType.INTEGER -> key.asIntegerValue().toLong().toString()
        ValueType.BINARY -> String(key.asBinaryValue().asByteArray())
        else -> key.toString()
    }
}

/**
 * LZ4 block decoder, ported from PyMax's own implementation so the app carries no native
 * decompression library. Only decoding is needed: outbound frames go out uncompressed.
 */
object Lz4Block {

    private const val MAX_OUTPUT = 5 * 1024 * 1024

    fun decompress(source: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(source.size * 3)
        val destination = ArrayList<Byte>(source.size * 3)
        var position = 0

        while (position < source.size) {
            val token = source[position].toInt() and 0xFF
            position++

            var literals = token shr 4
            if (literals == 15) {
                while (position < source.size) {
                    val extra = source[position].toInt() and 0xFF
                    position++
                    literals += extra
                    if (extra != 255) break
                }
            }

            if (literals > 0) {
                if (position + literals > source.size) throw IllegalStateException("LZ4: literals out of bounds")
                for (index in 0 until literals) destination.add(source[position + index])
                position += literals
                if (destination.size > MAX_OUTPUT) throw IllegalStateException("LZ4: output too large")
            }

            if (position >= source.size) break
            if (position + 1 >= source.size) throw IllegalStateException("LZ4: incomplete offset")

            val offset = (source[position].toInt() and 0xFF) or ((source[position + 1].toInt() and 0xFF) shl 8)
            position += 2
            if (offset == 0) throw IllegalStateException("LZ4: zero offset")

            var matchLength = (token and 0x0F) + 4
            if ((token and 0x0F) == 0x0F) {
                while (position < source.size) {
                    val extra = source[position].toInt() and 0xFF
                    position++
                    matchLength += extra
                    if (extra != 255) break
                }
            }

            val start = destination.size - offset
            if (start < 0) throw IllegalStateException("LZ4: match out of bounds")
            for (index in 0 until matchLength) destination.add(destination[start + (index % offset)])
            if (destination.size > MAX_OUTPUT) throw IllegalStateException("LZ4: output too large")
        }

        destination.forEach { byte -> out.write(byte.toInt()) }
        return out.toByteArray()
    }
}
