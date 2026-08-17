package ru.maxalert.notifier

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The closing line has to be true and short. It is read once, right after something that woke
 * the household, so it states facts and does not congratulate anyone.
 */
private fun summary(
    level: AlertLevel = AlertLevel.RED,
    durationMs: Long = 22 * 60_000L,
    from: String = "03:19",
    to: String = "03:41",
    chat: String = "Диспетчерская",
) = AlertSummary.describe(level, durationMs, from, to, chat)

class AlertSummaryNegativeCasesTest {

    @Test
    fun `a very short alert does not claim zero minutes`() {
        val text = summary(durationMs = 12_000L)

        assertTrue(text, text.contains("меньше минуты"))
    }

    @Test
    fun `a negative duration cannot happen but must not print a negative`() {
        val text = summary(durationMs = -5_000L)

        assertTrue(text, text.contains("меньше минуты"))
    }

    @Test
    fun `no chat name leaves no empty quotes behind`() {
        val text = summary(chat = "")

        assertTrue(text, !text.contains("«"))
        assertTrue(text, text.startsWith("Отбой."))
    }
}

class AlertSummaryPositiveCasesTest {

    @Test
    fun `it names the level, the length and both times`() {
        val text = summary()

        assertTrue(text, text.contains("КОД КРАСНЫЙ"))
        assertTrue(text, text.contains("22 мин"))
        assertTrue(text, text.contains("03:19"))
        assertTrue(text, text.contains("03:41"))
        assertTrue(text, text.contains("«Диспетчерская»"))
    }

    @Test
    fun `an uneven length keeps its seconds`() {
        assertTrue(summary(durationMs = 90_000L).contains("1 мин 30 с"))
    }

    @Test
    fun `a yellow level closes with its own name`() {
        assertTrue(summary(level = AlertLevel.YELLOW).contains("Код жёлтый"))
    }
}
