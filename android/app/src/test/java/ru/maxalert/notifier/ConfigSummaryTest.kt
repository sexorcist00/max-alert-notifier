package ru.maxalert.notifier

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summary has to be true before it is pretty: a sentence that says the phone is watching
 * one chat while it is really watching every chat is worse than no sentence at all.
 */
private fun summary(settings: AlertSettings, sound: String = "Ракетная опасность") =
    ConfigSummary.describe(settings, sound)

class ConfigSummaryNegativeCasesTest {

    @Test
    fun `an empty chat filter is stated as every chat, not hidden`() {
        val text = summary(AlertSettings(chatFilter = "", keywords = listOf("тревога")))
        assertTrue(text, text.contains("ЛЮБЫМ чатом"))
    }

    @Test
    fun `no keywords at all is stated as every message being red`() {
        val text = summary(AlertSettings(chatFilter = "Смена"))
        assertTrue(text, text.contains("любое сообщение поднимает КРАСНЫЙ"))
    }

    @Test
    fun `a missing all-clear says the alert can only be lifted by hand`() {
        val text = summary(AlertSettings(chatFilter = "Смена", keywords = listOf("тревога")))
        assertTrue(text, text.contains("снимать вручную"))
    }
}

class ConfigSummaryPositiveCasesTest {

    @Test
    fun `a full configuration reads back every level`() {
        val text = summary(
            AlertSettings(
                chatFilter = "Диспетчерская",
                keywords = listOf("код красный"),
                yellowHighKeywords = listOf("жёлтый повышенный"),
                yellowKeywords = listOf("код жёлтый"),
                deactivationKeywords = listOf("отбой"),
            )
        )
        assertTrue(text, text.contains("«Диспетчерская»"))
        assertTrue(text, text.contains("красный — «код красный»"))
        assertTrue(text, text.contains("жёлтый повышенный — «жёлтый повышенный»"))
        assertTrue(text, text.contains("отбой — «отбой»"))
    }

    @Test
    fun `the alarm sentence names sound, rhythm and limit`() {
        val text = summary(AlertSettings(loopSeconds = 300, vibrate = true, flashlight = true))
        assertTrue(text, text.contains("«Ракетная опасность»"))
        assertTrue(text, text.contains("5 мин"))
        assertTrue(text, text.contains("вибрацией"))
        assertTrue(text, text.contains("фонариком"))
    }

    @Test
    fun `an odd limit is spelled out rather than rounded away`() {
        val text = summary(AlertSettings(loopSeconds = 90))
        assertTrue(text, text.contains("1 мин 30 с"))
    }
}
