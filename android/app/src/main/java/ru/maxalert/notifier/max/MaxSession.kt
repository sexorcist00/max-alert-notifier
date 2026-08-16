package ru.maxalert.notifier.max

import android.content.Context
import java.util.UUID

/**
 * The account's own connection state.
 *
 * [loginToken] is a live key to the MAX account -- it never leaves the device and is
 * never written to the log.
 */
class MaxSession(context: Context) {

    /** Kept so the client can ask the system which network to leave through. */
    val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }

    /** Stable per-install id the Android client sends alongside the device id. */
    val instanceId: String
        get() = prefs.getString(KEY_INSTANCE_ID, null) ?: java.util.UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_INSTANCE_ID, generated).apply()
        }

    val clientSessionId: Int
        get() = prefs.getInt(KEY_CLIENT_SESSION, 0).takeIf { it != 0 }
            ?: (1..70).random().also { generated ->
                prefs.edit().putInt(KEY_CLIENT_SESSION, generated).apply()
            }

    /** Last moment the server actually answered us -- the honest input for the status light. */
    var lastOnlineAt: Long
        get() = prefs.getLong(KEY_LAST_ONLINE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ONLINE, value).apply()

    /** Time of the last message we acted on, so a reconnect can replay only what we missed. */
    var lastSeenTime: Long
        get() = prefs.getLong(KEY_LAST_SEEN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SEEN, value).apply()

    var phone: String?
        get() = prefs.getString(KEY_PHONE, null)
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    /** Short-lived token that ties "code requested" to "code submitted". */
    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    /** Ties "code accepted" to "password accepted" while MAX asks for the second factor. */
    var passwordTrackId: String?
        get() = prefs.getString(KEY_TRACK_ID, null)
        set(value) = prefs.edit().putString(KEY_TRACK_ID, value).apply()

    /** The reminder the user set for their MAX password, when the server sends one. */
    var passwordHint: String?
        get() = prefs.getString(KEY_PASSWORD_HINT, null)
        set(value) = prefs.edit().putString(KEY_PASSWORD_HINT, value).apply()

    var loginToken: String?
        get() = prefs.getString(KEY_LOGIN_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_LOGIN_TOKEN, value).apply()

    val loggedIn: Boolean
        get() = !loginToken.isNullOrEmpty()

    fun clear() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_TRACK_ID)
            .remove(KEY_PASSWORD_HINT)
            .remove(KEY_LOGIN_TOKEN)
            .remove(KEY_PHONE)
            .apply()
    }

    private companion object {
        const val PREFS = "max-session"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_INSTANCE_ID = "instance_id"
        const val KEY_CLIENT_SESSION = "client_session_id"
        const val KEY_LAST_SEEN = "last_seen_time"
        const val KEY_LAST_ONLINE = "last_online_at"
        const val KEY_PHONE = "phone"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_TRACK_ID = "password_track_id"
        const val KEY_PASSWORD_HINT = "password_hint"
        const val KEY_LOGIN_TOKEN = "login_token"
    }
}
