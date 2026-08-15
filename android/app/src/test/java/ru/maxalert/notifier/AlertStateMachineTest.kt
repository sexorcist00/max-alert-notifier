package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Код красный" is a state: the all-clear has to beat the alarm word, and a run of missed
 * messages has to be judged by how it ends, not by what it contains.
 */
private fun settings(
    keywords: List<String> = listOf("тревога"),
    deactivation: List<String> = listOf("отбой"),
) = AlertSettings(chatFilter = "Диспетчерская", keywords = keywords, deactivationKeywords = deactivation)

private fun message(text: String) =
    IncomingNotification(AlertSettings.MAX_PACKAGE, "Диспетчерская смена", text)

/** What the pipeline would do with a run of messages, without touching Android storage. */
private fun replayVerdicts(texts: List<String>, rules: AlertSettings): Boolean {
    var active = false
    texts.forEach { text ->
        when (Matcher.evaluate(message(text), rules)) {
            is Verdict.Match -> active = true
            is Verdict.Deactivate -> active = false
            is Verdict.Skip -> Unit
        }
    }
    return active
}

class AlertStateNegativeCasesTest {

    @Test
    fun `an all-clear alone leaves nothing standing`() {
        assertEquals(false, replayVerdicts(listOf("отбой"), settings()))
    }

    @Test
    fun `an alarm called off during the outage must not ring`() {
        val texts = listOf("тревога, третий сектор", "работаем", "отбой, всё в порядке")
        assertEquals(false, replayVerdicts(texts, settings()))
    }

    @Test
    fun `the all-clear wins inside one message`() {
        val verdict = Matcher.evaluate(message("тревога отменена, отбой"), settings())
        assertTrue(verdict is Verdict.Deactivate)
    }

    @Test
    fun `an all-clear from another chat is ignored`() {
        val verdict = Matcher.evaluate(
            IncomingNotification(AlertSettings.MAX_PACKAGE, "Курилка", "отбой"),
            settings(),
        )
        assertEquals("другой чат", (verdict as Verdict.Skip).reason)
    }
}

class AlertStatePositiveCasesTest {

    @Test
    fun `an alarm without an all-clear is still standing after the outage`() {
        val texts = listOf("всё тихо", "тревога, третий сектор", "выезжаем")
        assertEquals(true, replayVerdicts(texts, settings()))
    }

    @Test
    fun `a new alarm after an all-clear stands again`() {
        val texts = listOf("тревога", "отбой", "тревога снова")
        assertEquals(true, replayVerdicts(texts, settings()))
    }

    @Test
    fun `the all-clear is matched regardless of case`() {
        val verdict = Matcher.evaluate(message("ОТБОЙ"), settings())
        assertEquals("отбой", (verdict as Verdict.Deactivate).keyword)
    }

    @Test
    fun `without all-clear words configured nothing lifts the alert by itself`() {
        val rules = settings(deactivation = emptyList())
        assertEquals(true, replayVerdicts(listOf("тревога", "отбой"), rules))
    }
}
