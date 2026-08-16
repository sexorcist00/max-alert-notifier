package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = 60_000L
private const val NOW = 1_000_000_000L

private fun status(
    watching: Boolean = true,
    notificationAccess: Boolean = true,
    directEnabled: Boolean = true,
    loggedIn: Boolean = true,
    online: Boolean = true,
    lastOnlineAt: Long = NOW - MINUTE,
    awaitingPassword: Boolean = false,
) = SessionStatus.evaluate(
    watching = watching,
    notificationAccess = notificationAccess,
    directEnabled = directEnabled,
    loggedIn = loggedIn,
    online = online,
    lastOnlineAt = lastOnlineAt,
    now = NOW,
    awaitingPassword = awaitingPassword,
)

class SessionStatusNegativeCasesTest {

    @Test
    fun `duty off is red whatever else is configured`() {
        assertEquals(SessionStatus.Level.FAIL, status(watching = false).level)
    }

    @Test
    fun `no notification access and no login is red`() {
        val result = status(notificationAccess = false, loggedIn = false)
        assertEquals(SessionStatus.Level.FAIL, result.level)
    }

    @Test
    fun `a long silence with no second source is red`() {
        val result = status(
            notificationAccess = false,
            online = false,
            lastOnlineAt = NOW - 40 * MINUTE,
        )
        assertEquals(SessionStatus.Level.FAIL, result.level)
        assertTrue(result.detail.contains("40"))
    }

    @Test
    fun `a lost connection with notifications still available is only amber`() {
        val result = status(online = false, lastOnlineAt = NOW - 3 * MINUTE)
        assertEquals(SessionStatus.Level.WARN, result.level)
        assertTrue(result.detail.contains("3"))
    }

    @Test
    fun `a missing login is amber, not green`() {
        assertEquals(SessionStatus.Level.WARN, status(loggedIn = false).level)
    }

    @Test
    fun `a login waiting for the MAX password says so instead of "not logged in"`() {
        val result = status(loggedIn = false, awaitingPassword = true)

        assertEquals(SessionStatus.Level.WARN, result.level)
        assertTrue(result.title, result.title.contains("пароль"))
    }
}

class SessionStatusPositiveCasesTest {

    @Test
    fun `both sources alive is green`() {
        val result = status()
        assertEquals(SessionStatus.Level.OK, result.level)
        assertTrue(result.title.contains("оба источника"))
    }

    @Test
    fun `own connection alone is amber and says which source is missing`() {
        val result = status(notificationAccess = false)
        assertEquals(SessionStatus.Level.WARN, result.level)
        assertTrue(result.detail.contains("уведомлени"))
    }

    @Test
    fun `notifications alone is amber and warns what it costs`() {
        val result = status(directEnabled = false, loggedIn = false, online = false)
        assertEquals(SessionStatus.Level.WARN, result.level)
        assertTrue(result.title.contains("уведомления"))
    }

    @Test
    fun `a connection that never happened says so rather than showing a fake age`() {
        val result = status(online = false, lastOnlineAt = 0L)
        assertTrue(result.detail.contains("ещё не устанавливалось"))
    }
}
