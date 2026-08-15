package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun settings(
    red: List<String> = listOf("код красный"),
    yellowHigh: List<String> = listOf("жёлтый повышенный"),
    yellow: List<String> = listOf("код жёлтый"),
    off: List<String> = listOf("отбой"),
) = AlertSettings(
    chatFilter = "Диспетчерская",
    keywords = red,
    yellowHighKeywords = yellowHigh,
    yellowKeywords = yellow,
    deactivationKeywords = off,
)

private fun message(text: String) =
    IncomingNotification(AlertSettings.MAX_PACKAGE, "Диспетчерская смена", text)

private fun level(text: String, rules: AlertSettings = settings()): AlertLevel? =
    (Matcher.evaluate(message(text), rules) as? Verdict.Match)?.level

class AlertLevelNegativeCasesTest {

    @Test
    fun `yellow levels never make noise`() {
        assertFalse(AlertLevel.YELLOW.rings)
        assertFalse(AlertLevel.YELLOW_HIGH.rings)
        assertFalse(AlertLevel.NONE.rings)
    }

    @Test
    fun `the all-clear beats every level in the same message`() {
        val verdict = Matcher.evaluate(message("код красный, отбой"), settings())
        assertTrue(verdict is Verdict.Deactivate)
    }

    @Test
    fun `a message with no configured word raises nothing when other levels are configured`() {
        assertEquals(null, level("обычная болтовня"))
    }

    @Test
    fun `an unknown stored level reads as nothing standing`() {
        assertEquals(AlertLevel.NONE, AlertLevel.fromId("orange"))
        assertEquals(AlertLevel.NONE, AlertLevel.fromId(null))
    }
}

class AlertLevelPositiveCasesTest {

    @Test
    fun `only red rings`() {
        assertTrue(AlertLevel.RED.rings)
    }

    @Test
    fun `each level is recognised by its own words`() {
        assertEquals(AlertLevel.RED, level("объявлен код красный"))
        assertEquals(AlertLevel.YELLOW_HIGH, level("жёлтый повышенный по третьему сектору"))
        assertEquals(AlertLevel.YELLOW, level("код жёлтый"))
    }

    @Test
    fun `a message naming two colours takes the worse one`() {
        assertEquals(AlertLevel.RED, level("был код жёлтый, теперь код красный"))
        assertEquals(AlertLevel.YELLOW_HIGH, level("код жёлтый, точнее жёлтый повышенный"))
    }

    @Test
    fun `with no words at all every message is a red alert`() {
        val rules = settings(red = emptyList(), yellowHigh = emptyList(), yellow = emptyList())
        assertEquals(AlertLevel.RED, level("что угодно", rules))
    }

    @Test
    fun `levels survive a round trip through their stored id`() {
        AlertLevel.entries.forEach { level ->
            assertEquals(level, AlertLevel.fromId(level.id))
        }
    }
}

class UpdateVersionTest {

    @Test
    fun `an older or equal release is not offered`() {
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.1.9", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("сломанный тег", "1.2.0"))
    }

    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.99.99"))
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2"))
    }
}
