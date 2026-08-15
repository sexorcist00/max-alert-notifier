package ru.maxalert.notifier

/**
 * How the alarm behaves in time -- one cadence driving the sound, the vibration and the
 * torch together, so the phone speaks with one voice instead of three.
 *
 * These are not invented rhythms. T-3 is the international evacuation signal
 * (ISO 8201, ANSI/ASA S3.41) -- the one every smoke alarm on the planet uses. T-4 is the
 * carbon-monoxide pattern from the same standard. The emergency-alert cadence follows the
 * EAS / Wireless Emergency Alerts attention signal. Using a standard rhythm means people
 * who have heard a fire panel already know, without being told, that this is not a message.
 */
enum class AlarmPattern(
    val id: String,
    val label: String,
    val standard: String,
    /** Alternating on/off milliseconds; the whole array repeats while the alarm rings. */
    val timings: LongArray,
) {
    CONTINUOUS(
        id = "continuous",
        label = "Непрерывный",
        standard = "без пауз, максимально настойчивый",
        timings = longArrayOf(1000, 0),
    ),

    TEMPORAL_3(
        id = "t3",
        label = "Эвакуация (T-3)",
        standard = "ISO 8201 · ANSI/ASA S3.41 — международный сигнал эвакуации",
        timings = longArrayOf(500, 500, 500, 500, 500, 1500),
    ),

    TEMPORAL_4(
        id = "t4",
        label = "Угарный газ (T-4)",
        standard = "ANSI/ASA S3.41 — четыре импульса и длинная пауза",
        timings = longArrayOf(100, 100, 100, 100, 100, 100, 100, 5000),
    ),

    EMERGENCY(
        id = "wea",
        label = "Экстренное оповещение",
        standard = "EAS / WEA — сигнал привлечения внимания",
        timings = longArrayOf(2000, 1000),
    );

    /** Total length of one cycle, used to keep the torch inside the safe flash rate. */
    val cycleMs: Long get() = timings.sum()

    companion object {
        /** The default sound is a continuous wail, so the cadence does not chop it up. */
        val DEFAULT = CONTINUOUS

        fun fromId(id: String?): AlarmPattern =
            entries.firstOrNull { pattern -> pattern.id == id } ?: DEFAULT

        /**
         * Anything faster than three flashes per second can trigger a photosensitive seizure
         * (WCAG 2.3.1 general flash threshold), so the torch never strobes above it.
         */
        const val MIN_FLASH_INTERVAL_MS = 334L
    }
}
