package ru.maxalert.notifier

import android.content.Context

/** Everything the watcher needs to decide, and everything the alarm needs to sound. */
data class AlertSettings(
    val enabled: Boolean = true,
    val sourcePackage: String = MAX_PACKAGE,
    val chatFilter: String = "",
    /** Words that declare a red alert -- the only level that makes noise. */
    val keywords: List<String> = emptyList(),
    val yellowHighKeywords: List<String> = emptyList(),
    val yellowKeywords: List<String> = emptyList(),
    /** Words that lift a standing alert, said in the same chat. */
    val deactivationKeywords: List<String> = emptyList(),
    val vibrate: Boolean = true,
    /** 0..100; scaled to the vibrator's amplitude range where the device supports it. */
    val vibrationStrength: Int = 100,
    /** Strobe the torch on the same cadence as the sound. */
    val flashlight: Boolean = false,
    val patternId: String = AlarmPattern.DEFAULT.id,
    val forceMaxVolume: Boolean = true,
    val loopSeconds: Int = 300,
    val cooldownSeconds: Int = 30,
    val soundUri: String? = DEFAULT_SOUND,
    /** Second, independent source: the app's own connection to MAX. */
    val useDirectConnection: Boolean = true,
) {
    val pattern: AlarmPattern get() = AlarmPattern.fromId(patternId)

    companion object {
        const val MAX_PACKAGE = "ru.oneme.app"

        /**
         * The alarm ships its own sound; the phone's alarm ringtone is never used.
         * The wailing civil-defence siren is the default: it is the sound people already
         * read as "take cover", and it needs no explanation at three in the morning.
         */
        const val DEFAULT_SOUND = "raw:alarm_missile"
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
            yellowHighKeywords = Matcher.parseKeywords(prefs.getString(KEY_YELLOW_HIGH, "") ?: ""),
            yellowKeywords = Matcher.parseKeywords(prefs.getString(KEY_YELLOW, "") ?: ""),
            deactivationKeywords = Matcher.parseKeywords(prefs.getString(KEY_DEACTIVATION, "") ?: ""),
            vibrate = prefs.getBoolean(KEY_VIBRATE, defaults.vibrate),
            vibrationStrength = prefs.getInt(KEY_VIBRATION_STRENGTH, defaults.vibrationStrength),
            flashlight = prefs.getBoolean(KEY_FLASHLIGHT, defaults.flashlight),
            patternId = prefs.getString(KEY_PATTERN, defaults.patternId) ?: defaults.patternId,
            forceMaxVolume = prefs.getBoolean(KEY_MAX_VOLUME, defaults.forceMaxVolume),
            loopSeconds = prefs.getInt(KEY_LOOP, defaults.loopSeconds),
            cooldownSeconds = prefs.getInt(KEY_COOLDOWN, defaults.cooldownSeconds),
            soundUri = prefs.getString(KEY_SOUND, defaults.soundUri) ?: defaults.soundUri,
            useDirectConnection = prefs.getBoolean(KEY_DIRECT, defaults.useDirectConnection),
        )
    }

    fun save(settings: AlertSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_PACKAGE, settings.sourcePackage)
            .putString(KEY_CHAT, settings.chatFilter)
            .putString(KEY_KEYWORDS, settings.keywords.joinToString(", "))
            .putString(KEY_YELLOW_HIGH, settings.yellowHighKeywords.joinToString(", "))
            .putString(KEY_YELLOW, settings.yellowKeywords.joinToString(", "))
            .putString(KEY_DEACTIVATION, settings.deactivationKeywords.joinToString(", "))
            .putBoolean(KEY_VIBRATE, settings.vibrate)
            .putInt(KEY_VIBRATION_STRENGTH, settings.vibrationStrength)
            .putBoolean(KEY_FLASHLIGHT, settings.flashlight)
            .putString(KEY_PATTERN, settings.patternId)
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
        const val KEY_YELLOW_HIGH = "yellow_high_keywords"
        const val KEY_YELLOW = "yellow_keywords"
        const val KEY_DEACTIVATION = "deactivation_keywords"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        const val KEY_FLASHLIGHT = "flashlight"
        const val KEY_PATTERN = "alarm_pattern"
        const val KEY_MAX_VOLUME = "max_volume"
        const val KEY_LOOP = "loop_seconds"
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_SOUND = "sound_uri"
        const val KEY_DIRECT = "direct_connection"
    }
}
