package ru.maxalert.notifier.max

/**
 * What MAX answered to a login step.
 *
 * The same shape comes back from the SMS code and from the password check, so both are read
 * by one function -- and it is a pure one, because this is where a wrong guess about the
 * protocol costs the user an SMS and a locked-out account rather than a compile error.
 */
sealed interface AuthOutcome {

    /** The account is open: [token] is the long-lived login token. */
    data class LoggedIn(val token: String) : AuthOutcome

    /**
     * The account has a login password (MAX's own 2FA). [trackId] identifies this attempt and
     * has to come back with the password; [hint] is the reminder the user set, if any.
     */
    data class PasswordRequired(val trackId: String, val hint: String?) : AuthOutcome

    /** The phone has no MAX account yet -- registering one is not this app's job. */
    data object RegistrationRequired : AuthOutcome

    data class Refused(val reason: String) : AuthOutcome
}

/**
 * What went wrong, in words for the person at the screen -- or null when nothing did.
 *
 * A password challenge is not a failure and gets no message here: the password field appears
 * and says what it wants, which is more useful than a line of red text above it.
 */
fun AuthOutcome.explain(): String? = when (this) {
    is AuthOutcome.LoggedIn -> null
    is AuthOutcome.PasswordRequired -> null
    AuthOutcome.RegistrationRequired ->
        "У этого номера нет аккаунта MAX — сначала заведите его в самом МАКСе"
    is AuthOutcome.Refused -> reason
}

/**
 * Reads the payload of AUTH (opcode 18) and AUTH_LOGIN_CHECK_PASSWORD (opcode 115).
 *
 * Field names follow the protocol as implemented by PyMax (MIT): `tokenAttrs.LOGIN.token`,
 * `passwordChallenge.trackId`, `passwordChallenge.hint`, and a plain `error` string that the
 * password step returns instead of an error frame when the password is simply wrong.
 */
object AuthResponse {

    fun read(payload: Map<String, Any?>): AuthOutcome {
        (payload["error"] as? String)?.takeIf { it.isNotBlank() }?.let { error ->
            return AuthOutcome.Refused(error)
        }

        val attrs = payload["tokenAttrs"] as? Map<*, *>
        val login = (attrs?.get("LOGIN") as? Map<*, *>)?.get("token") as? String
        if (!login.isNullOrEmpty()) return AuthOutcome.LoggedIn(login)

        val challenge = payload["passwordChallenge"] as? Map<*, *>
        val trackId = challenge?.get("trackId") as? String
        if (!trackId.isNullOrEmpty()) {
            return AuthOutcome.PasswordRequired(
                trackId = trackId,
                hint = (challenge["hint"] as? String)?.takeIf { it.isNotBlank() },
            )
        }

        val register = (attrs?.get("REGISTER") as? Map<*, *>)?.get("token") as? String
        if (!register.isNullOrEmpty()) return AuthOutcome.RegistrationRequired

        return AuthOutcome.Refused("MAX не вернул токен входа")
    }
}
