package ru.maxalert.notifier

/**
 * The whole configuration in plain sentences.
 *
 * Reading a form to work out what the phone will do is the wrong way round: the settings
 * screen should be able to say it out loud. Pure, so the wording is covered by tests rather
 * than checked by eye.
 */
object ConfigSummary {

    fun describe(settings: AlertSettings, soundLabel: String): String {
        val parts = mutableListOf<String>()

        parts += if (settings.chatFilter.isBlank()) {
            "Слежу за ЛЮБЫМ чатом в МАКСе"
        } else {
            "Слежу за чатом «${settings.chatFilter.trim()}»"
        }

        val levels = buildList {
            if (settings.keywords.isNotEmpty()) add("красный — ${quote(settings.keywords)}")
            if (settings.yellowHighKeywords.isNotEmpty()) {
                add("жёлтый повышенный — ${quote(settings.yellowHighKeywords)}")
            }
            if (settings.yellowKeywords.isNotEmpty()) add("жёлтый — ${quote(settings.yellowKeywords)}")
        }
        parts += if (levels.isEmpty()) {
            "любое сообщение поднимает КРАСНЫЙ"
        } else {
            levels.joinToString("; ")
        }

        parts += if (settings.deactivationKeywords.isEmpty()) {
            "отбоя нет — снимать вручную"
        } else {
            "отбой — ${quote(settings.deactivationKeywords)}"
        }

        val alarm = buildString {
            append("Звоню «$soundLabel»")
            append(", ритм «${settings.pattern.label}»")
            append(", не дольше ${minutes(settings.loopSeconds)}")
            if (settings.vibrate) append(", с вибрацией")
            if (settings.flashlight) append(" и фонариком")
        }

        return parts.joinToString(". ") + ". " + alarm + "."
    }

    private fun quote(words: List<String>): String = words.joinToString(", ") { "«$it»" }

    private fun minutes(seconds: Int): String = when {
        seconds < 60 -> "$seconds с"
        seconds % 60 == 0 -> "${seconds / 60} мин"
        else -> "${seconds / 60} мин ${seconds % 60} с"
    }
}
