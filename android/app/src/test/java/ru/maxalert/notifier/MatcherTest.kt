package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAT = "Диспетчерская смена"

private fun settings(
    enabled: Boolean = true,
    chatFilter: String = "Диспетчерская",
    keywords: List<String> = listOf("тревога"),
) = AlertSettings(enabled = enabled, chatFilter = chatFilter, keywords = keywords)

private fun notification(
    text: String,
    chat: String = CHAT,
    packageName: String = AlertSettings.MAX_PACKAGE,
) = IncomingNotification(packageName, chat, text)

class MatcherNegativeCasesTest {

    @Test
    fun `skips everything while the watcher is off`() {
        val verdict = Matcher.evaluate(notification("тревога"), settings(enabled = false))
        assertEquals("сторож выключен", (verdict as Verdict.Skip).reason)
    }

    @Test
    fun `skips notifications from another app`() {
        val verdict = Matcher.evaluate(notification("тревога", packageName = "com.whatsapp"), settings())
        assertEquals("другое приложение", (verdict as Verdict.Skip).reason)
    }

    @Test
    fun `skips another chat`() {
        val verdict = Matcher.evaluate(notification("тревога", chat = "Курилка"), settings())
        assertEquals("другой чат", (verdict as Verdict.Skip).reason)
    }

    @Test
    fun `skips an empty message`() {
        val verdict = Matcher.evaluate(notification("   "), settings())
        assertEquals("пустой текст", (verdict as Verdict.Skip).reason)
    }

    @Test
    fun `skips a message without any keyword`() {
        val verdict = Matcher.evaluate(notification("всё спокойно"), settings())
        assertEquals("нет ключевого слова", (verdict as Verdict.Skip).reason)
    }

    @Test
    fun `gate blocks the same notification twice`() {
        val gate = TriggerGate { 0L }
        assertTrue(gate.allow("key-1", 0))
        assertFalse(gate.allow("key-1", 0))
    }

    @Test
    fun `gate blocks a new notification inside the cooldown`() {
        var now = 0L
        val gate = TriggerGate { now }
        assertTrue(gate.allow("key-1", 30))
        now = 29_000
        assertFalse(gate.allow("key-2", 30))
    }
}

class MatcherPositiveCasesTest {

    @Test
    fun `matches a keyword regardless of case`() {
        val verdict = Matcher.evaluate(notification("ТРЕВОГА в третьем секторе"), settings())
        assertEquals("тревога", (verdict as Verdict.Match).keyword)
    }

    @Test
    fun `matches any of several keywords`() {
        val rules = settings(keywords = listOf("тревога", "выезд"))
        val verdict = Matcher.evaluate(notification("общий выезд"), rules)
        assertEquals("выезд", (verdict as Verdict.Match).keyword)
    }

    @Test
    fun `matches any message when no keyword is configured`() {
        val verdict = Matcher.evaluate(notification("что угодно"), settings(keywords = emptyList()))
        assertEquals(null, (verdict as Verdict.Match).keyword)
    }

    @Test
    fun `matches in any chat when no chat filter is configured`() {
        val verdict = Matcher.evaluate(notification("тревога", chat = "Курилка"), settings(chatFilter = ""))
        assertTrue(verdict is Verdict.Match)
    }

    @Test
    fun `matches the chat filter ignoring case`() {
        val verdict = Matcher.evaluate(notification("тревога"), settings(chatFilter = "диспетчерская"))
        assertTrue(verdict is Verdict.Match)
    }

    @Test
    fun `parses keywords from commas and newlines`() {
        assertEquals(listOf("тревога", "выезд", "код 17"), Matcher.parseKeywords(" тревога, выезд \n код 17 "))
    }

    @Test
    fun `gate allows a new notification after the cooldown`() {
        var now = 0L
        val gate = TriggerGate { now }
        assertTrue(gate.allow("key-1", 30))
        now = 30_000
        assertTrue(gate.allow("key-2", 30))
    }
}
