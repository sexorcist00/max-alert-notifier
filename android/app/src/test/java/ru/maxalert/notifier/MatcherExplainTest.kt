package ru.maxalert.notifier

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dry run has to explain itself. "Не сработает" alone sends people hunting through
 * settings; the reason is the whole point of testing a phrase before an emergency.
 */
private val RULES = AlertSettings(
    chatFilter = "Диспетчерская",
    keywords = listOf("код красный"),
    yellowKeywords = listOf("код жёлтый"),
    deactivationKeywords = listOf("отбой"),
)

private fun explain(text: String, chat: String = "Диспетчерская смена"): String =
    Matcher.explain(
        Matcher.evaluate(IncomingNotification(AlertSettings.MAX_PACKAGE, chat, text), RULES)
    )

class MatcherExplainNegativeCasesTest {

    @Test
    fun `a phrase from another chat says so, not just no`() {
        val text = explain("код красный", chat = "Курилка")
        assertTrue(text, text.contains("Не сработает") && text.contains("другой чат"))
    }

    @Test
    fun `a phrase without any keyword names the missing rule`() {
        val text = explain("всё спокойно")
        assertTrue(text, text.contains("нет ключевого слова"))
    }
}

class MatcherExplainPositiveCasesTest {

    @Test
    fun `a red phrase says it will ring`() {
        val text = explain("объявлен код красный")
        assertTrue(text, text.contains("КОД КРАСНЫЙ") && text.contains("со звуком"))
    }

    @Test
    fun `a yellow phrase says it stays silent`() {
        val text = explain("код жёлтый по периметру")
        assertTrue(text, text.contains("Код жёлтый") && text.contains("без звука"))
    }

    @Test
    fun `an all-clear phrase says it lifts the alert`() {
        val text = explain("отбой")
        assertTrue(text, text.contains("Снимет тревогу"))
    }
}
