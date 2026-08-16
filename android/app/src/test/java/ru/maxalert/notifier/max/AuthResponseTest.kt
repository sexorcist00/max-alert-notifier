package ru.maxalert.notifier.max

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The login steps read the server's own words. A wrong guess here does not fail to compile --
 * it costs an SMS, and with MAX's password step it can cost the attempt entirely.
 *
 * The shapes below are the protocol as implemented by PyMax (MIT): AUTH answers with
 * `tokenAttrs.LOGIN.token`, or with `passwordChallenge.trackId`, and the password check
 * answers with the same token block or a plain `error` string.
 */
private fun login(token: String) = mapOf<String, Any?>(
    "tokenAttrs" to mapOf("LOGIN" to mapOf("token" to token)),
)

class AuthResponseNegativeCasesTest {

    @Test
    fun `a plain error field is the refusal, not an empty success`() {
        val outcome = AuthResponse.read(mapOf("error" to "wrong.password"))

        assertEquals(AuthOutcome.Refused("wrong.password"), outcome)
    }

    @Test
    fun `an error wins over a token block that came with it`() {
        val outcome = AuthResponse.read(login("t") + mapOf("error" to "attempt.expired"))

        assertEquals(AuthOutcome.Refused("attempt.expired"), outcome)
    }

    @Test
    fun `an empty payload is refused with a readable reason`() {
        val outcome = AuthResponse.read(emptyMap())

        assertEquals(AuthOutcome.Refused("MAX не вернул токен входа"), outcome)
    }

    @Test
    fun `an empty login token counts as no token`() {
        val outcome = AuthResponse.read(login(""))

        assertEquals(AuthOutcome.Refused("MAX не вернул токен входа"), outcome)
    }

    @Test
    fun `a register token means there is no account to log into`() {
        val outcome = AuthResponse.read(
            mapOf("tokenAttrs" to mapOf("REGISTER" to mapOf("token" to "r")))
        )

        assertEquals(AuthOutcome.RegistrationRequired, outcome)
    }
}

class AuthResponsePositiveCasesTest {

    @Test
    fun `a login token opens the account`() {
        assertEquals(AuthOutcome.LoggedIn("abc"), AuthResponse.read(login("abc")))
    }

    @Test
    fun `a password challenge carries the track id the answer is tied to`() {
        val outcome = AuthResponse.read(
            mapOf("passwordChallenge" to mapOf("trackId" to "track-1", "hint" to "девичья фамилия"))
        )

        assertEquals(AuthOutcome.PasswordRequired("track-1", "девичья фамилия"), outcome)
    }

    @Test
    fun `a challenge without a hint is still a challenge`() {
        val outcome = AuthResponse.read(mapOf("passwordChallenge" to mapOf("trackId" to "track-2")))

        assertEquals(AuthOutcome.PasswordRequired("track-2", null), outcome)
    }

    @Test
    fun `a blank hint is dropped rather than shown as an empty line`() {
        val outcome = AuthResponse.read(
            mapOf("passwordChallenge" to mapOf("trackId" to "track-3", "hint" to "  "))
        )

        assertEquals(AuthOutcome.PasswordRequired("track-3", null), outcome)
    }

    @Test
    fun `the password step returns the same token block as the code step`() {
        assertEquals(AuthOutcome.LoggedIn("after-password"), AuthResponse.read(login("after-password")))
    }
}

class AuthOutcomeExplainTest {

    @Test
    fun `negative cases speak, successful ones stay quiet`() {
        assertEquals(null, AuthOutcome.LoggedIn("t").explain())
        // The password field appears and says what it wants; a red line above it adds nothing.
        assertEquals(null, AuthOutcome.PasswordRequired("track", null).explain())
        assertEquals("wrong.password", AuthOutcome.Refused("wrong.password").explain())
        assertEquals(
            "У этого номера нет аккаунта MAX — сначала заведите его в самом МАКСе",
            AuthOutcome.RegistrationRequired.explain(),
        )
    }
}
