package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line this exists for, taken from a real screenshot of the duty card:
 *
 *     Состояние: Read error: ssl=0x7446552348: I/O error during system call,
 *     Software caused connection abort
 *
 * True, and useless: it does not say whether the phone is still watching. What matters is
 * that a person reads a state and an action, and that our own Russian wording is never
 * mangled by a translator that was written for OpenSSL.
 */
private const val REAL_SSL_ERROR =
    "Read error: ssl=0x7446552348: I/O error during system call, Software caused connection abort"

class ConnectionTroubleNegativeCasesTest {

    @Test
    fun `the ssl read error becomes a sentence about reconnecting`() {
        val text = ConnectionTrouble.humanize(REAL_SSL_ERROR)

        assertEquals("Соединение оборвалось — переподключаюсь", text)
        assertTrue("Технический текст не должен попадать в основную строку", !text.contains("ssl"))
    }

    @Test
    fun `an unknown english error still produces a usable sentence`() {
        val text = ConnectionTrouble.humanize("EPROTO: weird transport failure 42")

        assertEquals("Связь потеряна — переподключаюсь", text)
    }

    @Test
    fun `a null or blank message does not render as empty`() {
        assertEquals("Связи нет — переподключаюсь", ConnectionTrouble.humanize(null))
        assertEquals("Связи нет — переподключаюсь", ConnectionTrouble.humanize("   "))
    }

    @Test
    fun `no dns is named as no internet, not as a server fault`() {
        val text = ConnectionTrouble.humanize("Unable to resolve host \"api.oneme.ru\"")

        assertTrue(text, text.contains("Нет интернета"))
    }

    @Test
    fun `a timeout and a refusal are told apart`() {
        assertTrue(ConnectionTrouble.humanize("connect timed out").contains("не ответил"))
        assertTrue(ConnectionTrouble.humanize("ECONNREFUSED (Connection refused)").contains("не отвечает"))
    }
}

class ConnectionTroublePositiveCasesTest {

    @Test
    fun `our own wording is passed through untouched`() {
        val ours = "сессия отклонена, нужен повторный вход"

        assertEquals(ours, ConnectionTrouble.humanize(ours))
        assertTrue(ConnectionTrouble.isOurs(ours))
    }

    @Test
    fun `the detail line carries the original text only when it adds something`() {
        assertEquals(REAL_SSL_ERROR, ConnectionTrouble.detail(REAL_SSL_ERROR))
        assertNull(ConnectionTrouble.detail("нет соединения"))
        assertNull(ConnectionTrouble.detail(null))
        assertNull(ConnectionTrouble.detail(""))
    }

    @Test
    fun `a handshake failure is separated from a plain drop`() {
        val text = ConnectionTrouble.humanize("SSLHandshakeException: chain validation failed")

        assertTrue(text, text.contains("Защищённое соединение"))
    }
}
