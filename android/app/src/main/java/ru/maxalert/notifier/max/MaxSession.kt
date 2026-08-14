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

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }

    var phone: String?
        get() = prefs.getString(KEY_PHONE, null)
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    /** Short-lived token that ties "code requested" to "code submitted". */
    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var loginToken: String?
        get() = prefs.getString(KEY_LOGIN_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_LOGIN_TOKEN, value).apply()

    val loggedIn: Boolean
        get() = !loginToken.isNullOrEmpty()

    fun clear() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_LOGIN_TOKEN)
            .remove(KEY_PHONE)
            .apply()
    }

    private companion object {
        const val PREFS = "max-session"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_PHONE = "phone"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_LOGIN_TOKEN = "login_token"
    }
}
