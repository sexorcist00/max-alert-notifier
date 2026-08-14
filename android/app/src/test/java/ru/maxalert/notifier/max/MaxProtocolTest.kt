package ru.maxalert.notifier.max

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vector below is a real answer from api.oneme.ru, captured while PyMax performed its
 * handshake: cmd=1, seq=0, opcode=6, flags=1 (LZ4 block), 202 bytes of payload. If the
 * framing, the LZ4 port or the msgpack decoding drifts, this test fails instead of the phone.
 */
private const val HANDSHAKE_RESPONSE_HEX =
    "0a0100000006010000caf0b985ad7765622d7077612d70726f6d6fc3b270686f6e652d617574682d656e61626c65" +
        "64c3a86c6f636174696f6ea25553a46c616e67c3b07265672d636f756e7472792d636f6465dc002aa2415aa2414d" +
        "a24b5aa24b47a24d44a2544aa2555aa24745a25448a25452a2544da24145a24c41a24d59a24944a24355a24b48a2" +
        "564ea24146a2424fa24344a24347a2434fa24744a2474da2494ea24951a24b4ea24b57a24c42a24d4da24e49a250" +
        "4ba25057a25141a25341a25645a2545aa24547a2434ea25a41a24252"

private fun hex(value: String): ByteArray =
    ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

class MaxProtocolNegativeCasesTest {

    @Test
    fun `refuses a frame shorter than the header`() {
        assertNull(MaxProtocol.decode(hex("0a010000")))
    }

    @Test
    fun `refuses a frame whose payload is truncated`() {
        // Header promises 202 bytes, only a few are present.
        assertNull(MaxProtocol.decode(hex("0a0100000006010000ca8182")))
    }
}

class MaxProtocolPositiveCasesTest {

    @Test
    fun `decodes a real LZ4 compressed answer from the server`() {
        val frame = requireNotNull(MaxProtocol.decode(hex(HANDSHAKE_RESPONSE_HEX)))

        assertEquals(MaxProtocol.CMD_RESPONSE, frame.cmd)
        assertEquals(0, frame.seq)
        assertEquals(MaxProtocol.OP_SESSION_INIT, frame.opcode)
        assertEquals(
            setOf("lang", "location", "phone-auth-enabled", "reg-country-code", "web-pwa-promo"),
            frame.payload.keys,
        )
        assertEquals("US", frame.payload["location"])
        assertTrue(frame.payload["reg-country-code"] is List<*>)
    }

    @Test
    fun `writes the header exactly as the server expects`() {
        val payload = linkedMapOf<String, Any?>("interactive" to true)
        val frame = MaxProtocol.encode(MaxProtocol.OP_PING, seq = 2, payload = payload)

        assertEquals(MaxProtocol.VERSION.toByte(), frame[0])
        assertEquals(MaxProtocol.CMD_REQUEST.toByte(), frame[1])
        assertEquals(0, frame[2].toInt())
        assertEquals(2, frame[3].toInt()) // seq, big endian
        assertEquals(0, frame[4].toInt())
        assertEquals(MaxProtocol.OP_PING, frame[5].toInt()) // opcode, big endian
        assertEquals(0, frame[6].toInt()) // outbound frames are never compressed
        assertEquals(frame.size - MaxProtocol.HEADER_SIZE, frame[9].toInt())
    }

    @Test
    fun `matches the ping frame PyMax puts on the wire`() {
        // Captured: 0a00000200010000000e81ab696e746572616374697665c3
        val frame = MaxProtocol.encode(MaxProtocol.OP_PING, seq = 2, payload = linkedMapOf("interactive" to true))
        assertEquals("0a00000200010000000e81ab696e746572616374697665c3", frame.joinToString("") { byte ->
            "%02x".format(byte)
        })
    }

    @Test
    fun `round trips a handshake payload through its own decoder`() {
        val payload = linkedMapOf<String, Any?>(
            "userAgent" to MaxProtocol.webUserAgent("ru", "Europe/Moscow"),
            "deviceId" to "0c6a5f9e-1f3d-4a7c-9b2e-8f1d6c3a5b47",
        )
        val encoded = MaxProtocol.encode(MaxProtocol.OP_SESSION_INIT, seq = 0, payload = payload)

        // Re-read it as if it came back as an uncompressed answer.
        val asAnswer = encoded.copyOf().also { it[1] = MaxProtocol.CMD_RESPONSE.toByte() }
        val frame = requireNotNull(MaxProtocol.decode(asAnswer))

        assertEquals("0c6a5f9e-1f3d-4a7c-9b2e-8f1d6c3a5b47", frame.payload["deviceId"])
        @Suppress("UNCHECKED_CAST")
        val userAgent = frame.payload["userAgent"] as Map<String, Any?>
        assertEquals("WEB", userAgent["deviceType"])
        assertEquals(MaxProtocol.APP_VERSION, userAgent["appVersion"])
    }
}
