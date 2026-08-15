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

    companion object {
        fun fromId(id: String?): AlertLevel =
            entries.firstOrNull { level -> level.id == id } ?: NONE
    }
}
