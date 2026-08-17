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

    /**
     * Recordings first, generated patterns after.
     *
     * The sirens are real recordings (public domain / CC0): a siren is a machine with a rotor
     * and a horn, and a synthesised sweep sounds like a synthesised sweep -- which is what the
     * first version of this list was told, in those words. The patterns below them are still
     * generated, because standards define them as a cadence at a frequency, so a recording of
     * somebody's smoke alarm would be less faithful rather than more. Provenance and licences
     * live in docs/sounds.md, and a test fails if that file and this list disagree.
     */
    val CATALOGUE: List<Choice> = listOf(
        Choice(
            uri = bundled("alarm_missile"),
            label = "Ракетная опасность",
            note = "запись сирены ГО, проверка оповещения",
        ),
        Choice(
            uri = bundled("alarm_air_raid"),
            label = "Воздушная тревога",
            note = "запись сигнала воздушной тревоги",
        ),
        Choice(
            uri = bundled("alarm_siren"),
            label = "Постоянный тон",
            note = "запись сирены: сигнал «внимание»",
        ),
        Choice(
            uri = bundled("alarm_klaxon"),
            label = "Низкий горн",
            note = "запись механического горна",
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
        Choice(uri = bundled("alarm_pulse"), label = "Резкие писки"),
    )

    fun contains(uri: String?): Boolean = CATALOGUE.any { choice -> choice.uri == uri }

    private fun bundled(name: String) = "${AlarmController.BUNDLED_PREFIX}$name"
}
