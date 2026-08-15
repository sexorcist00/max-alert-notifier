package ru.maxalert.notifier

/**
 * Every sound the app ships, in one place.
 *
 * It lives here rather than in the screen because the catalogue and the default used to sit
 * in different files: the default was set to a tone that was never listed, so the settings
 * screen showed "свой файл" for a sound the app itself had chosen. A test now checks the
 * default is in this list.
 */
object AlarmSounds {

    data class Choice(val uri: String, val label: String, val note: String? = null)

    val CATALOGUE: List<Choice> = listOf(
        Choice(
            uri = bundled("alarm_missile"),
            label = "Ракетная опасность",
            note = "медленный вой сирены, цикл ~5 с",
        ),
        Choice(
            uri = bundled("alarm_air_raid"),
            label = "Воздушная тревога",
            note = "быстрый вой, цикл ~2 с",
        ),
        Choice(
            uri = bundled("alarm_t3"),
            label = "Эвакуация T-3",
            note = "ISO 8201 · ANSI/ASA S3.41",
        ),
        Choice(
            uri = bundled("alarm_t4"),
            label = "Угарный газ T-4",
            note = "ANSI/ASA S3.41",
        ),
        Choice(
            uri = bundled("alarm_wea"),
            label = "Экстренное оповещение",
            note = "EAS / WEA, 853 + 960 Гц",
        ),
        Choice(uri = bundled("alarm_two_tone"), label = "Двухтональный сигнал"),
        Choice(uri = bundled("alarm_siren"), label = "Сирена"),
        Choice(uri = bundled("alarm_pulse"), label = "Резкие писки"),
        Choice(uri = bundled("alarm_klaxon"), label = "Низкий клаксон"),
    )

    fun contains(uri: String?): Boolean = CATALOGUE.any { choice -> choice.uri == uri }

    private fun bundled(name: String) = "${AlarmController.BUNDLED_PREFIX}$name"
}
