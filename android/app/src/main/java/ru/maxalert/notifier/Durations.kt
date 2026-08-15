package ru.maxalert.notifier

/**
 * The durations a person actually picks, and how they are spelled.
 *
 * Typed into a number field, a duration costs a keyboard, a guess about the unit and a real
 * chance of leaving "3" where "300" was meant -- an alarm that stops after three seconds looks
 * exactly like an alarm that works. Presets remove all three. A value set on an older build is
 * still offered as its own option rather than rounded to the nearest preset.
 */
object Durations {

    /** How long the alarm may keep sounding. */
    val ALARM_LIMIT: List<Int> = listOf(60, 180, 300, 600, 1800)

    /** How long one incident stays quiet before the same words may ring again. */
    val COOLDOWN: List<Int> = listOf(0, 15, 30, 60, 300)

    fun label(seconds: Int): String = when {
        seconds <= 0 -> "без паузы"
        seconds < 60 -> "$seconds с"
        seconds % 60 == 0 -> "${seconds / 60} мин"
        else -> "${seconds / 60} мин ${seconds % 60} с"
    }

    fun options(presets: List<Int>, current: Int): List<Int> =
        if (current in presets) presets else (presets + current).sorted()
}
