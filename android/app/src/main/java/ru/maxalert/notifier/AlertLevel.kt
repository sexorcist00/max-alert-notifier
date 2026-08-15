package ru.maxalert.notifier

/**
 * How bad it is right now.
 *
 * One scale, not three independent alarms: a chat says "код жёлтый" and later "код красный",
 * and the phone has to show where the situation stands, not a pile of past events. Only the
 * red level makes noise -- the yellows are there to be seen, not to wake anyone.
 */
enum class AlertLevel(
    val id: String,
    val title: String,
    val rings: Boolean,
) {
    NONE("none", "Спокойно", false),
    YELLOW("yellow", "Код жёлтый", false),
    YELLOW_HIGH("yellow_high", "Код жёлтый повышенный", false),
    RED("red", "КОД КРАСНЫЙ", true);

    /** Red for red, amber for both yellows -- the colour has to match the word. */
    val colorArgb: Long
        get() = when (this) {
            RED -> 0xFFB3261E
            YELLOW_HIGH -> 0xFFE65100
            YELLOW -> 0xFFF9A825
            NONE -> 0xFF2E7D32
        }

    companion object {
        fun fromId(id: String?): AlertLevel =
            entries.firstOrNull { level -> level.id == id } ?: NONE
    }
}
