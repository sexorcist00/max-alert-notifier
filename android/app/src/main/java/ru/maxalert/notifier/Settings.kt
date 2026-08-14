package ru.maxalert.notifier

import android.content.Context

/** Everything the watcher needs to decide, and everything the alarm needs to sound. */
data class AlertSettings(
    val enabled: Boolean = true,
    val sourcePackage: String = MAX_PACKAGE,
    val chatFilter: String = "",
    val keywords: List<String> = emptyList(),
    val vibrate: Boolean = true,
    val forceMaxVolume: Boolean = true,
    val loopSeconds: Int = 300,
    val cooldownSeconds: Int = 30,
    val soundUri: String? = null,
    /** Second, independent source: the app's own connection to MAX. */
    val useDirectConnection: Boolean = true,
) {
    companion object {
        const val MAX_PACKAGE = "ru.oneme.app"
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AlertSettings {
        val defaults = AlertSettings()
        return AlertSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            sourcePackage = prefs.getString(KEY_PACKAGE, defaults.sourcePackage) ?: defaults.sourcePackage,
            chatFilter = prefs.getString(KEY_CHAT, defaults.chatFilter) ?: defaults.chatFilter,
            keywords = Matcher.parseKeywords(prefs.getString(KEY_KEYWORDS, "") ?: ""),
            vibrate = prefs.getBoolean(KEY_VIBRATE, defaults.vibrate),
            forceMaxVolume = prefs.getBoolean(KEY_MAX_VOLUME, defaults.forceMaxVolume),
            loopSeconds = prefs.getInt(KEY_LOOP, defaults.loopSeconds),
            cooldownSeconds = prefs.getInt(KEY_COOLDOWN, defaults.cooldownSeconds),
            soundUri = prefs.getString(KEY_SOUND, null),
            useDirectConnection = prefs.getBoolean(KEY_DIRECT, defaults.useDirectConnection),
        )
    }

    fun save(settings: AlertSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_PACKAGE, settings.sourcePackage)
            .putString(KEY_CHAT, settings.chatFilter)
            .putString(KEY_KEYWORDS, settings.keywords.joinToString(", "))
            .putBoolean(KEY_VIBRATE, settings.vibrate)
            .putBoolean(KEY_MAX_VOLUME, settings.forceMaxVolume)
            .putInt(KEY_LOOP, settings.loopSeconds)
            .putInt(KEY_COOLDOWN, settings.cooldownSeconds)
            .putString(KEY_SOUND, settings.soundUri)
            .putBoolean(KEY_DIRECT, settings.useDirectConnection)
            .apply()
    }

    private companion object {
        const val PREFS = "max-alert"
        const val KEY_ENABLED = "enabled"
        const val KEY_PACKAGE = "source_package"
        const val KEY_CHAT = "chat_filter"
        const val KEY_KEYWORDS = "keywords"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_MAX_VOLUME = "max_volume"
        const val KEY_LOOP = "loop_seconds"
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_SOUND = "sound_uri"
        const val KEY_DIRECT = "direct_connection"
    }
}
