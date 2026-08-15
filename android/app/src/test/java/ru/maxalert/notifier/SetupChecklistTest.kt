package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun steps(
    settings: AlertSettings,
    notificationAccess: Boolean = true,
    batteryFree: Boolean = true,
) = SetupChecklist.steps(settings, notificationAccess, batteryFree)

/** A fully configured phone: every later test starts from this and breaks one thing. */
private val READY = AlertSettings(
    enabled = true,
    chatFilter = "Диспетчерская",
    keywords = listOf("код красный"),
    deactivationKeywords = listOf("отбой"),
)

class SetupChecklistNegativeCasesTest {

    @Test
    fun `a fresh install lists every step, blocking ones first`() {
        val list = steps(AlertSettings(enabled = false), notificationAccess = false, batteryFree = false)

        assertEquals(
            listOf(
                SetupAction.ENABLE_DUTY,
                SetupAction.NOTIFICATION_ACCESS,
                SetupAction.BATTERY,
                SetupAction.CHAT,
                SetupAction.KEYWORDS,
                SetupAction.ALL_CLEAR,
            ),
            list.map { it.action },
        )
        assertEquals(3, list.count { it.blocking })
    }

    @Test
    fun `duty off is a blocking step even when everything else is set`() {
        val list = steps(READY.copy(enabled = false))

        assertEquals(listOf(SetupAction.ENABLE_DUTY), list.map { it.action })
        assertTrue(list.single().blocking)
    }

    @Test
    fun `missing notification access is blocking`() {
        val list = steps(READY, notificationAccess = false)

        assertEquals(listOf(SetupAction.NOTIFICATION_ACCESS), list.map { it.action })
        assertTrue(list.single().blocking)
    }

    @Test
    fun `battery restriction is blocking because the service dies silently`() {
        val list = steps(READY, batteryFree = false)

        assertEquals(listOf(SetupAction.BATTERY), list.map { it.action })
        assertTrue(list.single().blocking)
    }

    @Test
    fun `an empty chat filter is a step, but not a blocking one`() {
        val list = steps(READY.copy(chatFilter = ""))

        assertEquals(listOf(SetupAction.CHAT), list.map { it.action })
        assertTrue(!list.single().blocking)
    }

    @Test
    fun `yellow words alone are enough to keep the keyword step away`() {
        val list = steps(READY.copy(keywords = emptyList(), yellowKeywords = listOf("код жёлтый")))

        assertTrue(list.map { it.action }.toString(), list.none { it.action == SetupAction.KEYWORDS })
    }

    @Test
    fun `no words of any level asks for keywords`() {
        val list = steps(READY.copy(keywords = emptyList()))

        assertEquals(listOf(SetupAction.KEYWORDS), list.map { it.action })
    }

    @Test
    fun `no all-clear words is the last step and never blocking`() {
        val list = steps(READY.copy(deactivationKeywords = emptyList()))

        assertEquals(listOf(SetupAction.ALL_CLEAR), list.map { it.action })
        assertTrue(!list.single().blocking)
    }
}

class SetupChecklistPositiveCasesTest {

    @Test
    fun `a configured phone has nothing left to do`() {
        assertEquals(emptyList<SetupStep>(), steps(READY))
    }

    @Test
    fun `every step carries a button label`() {
        val list = steps(AlertSettings(enabled = false), notificationAccess = false, batteryFree = false)

        assertTrue(list.all { it.button.isNotBlank() && it.title.isNotBlank() })
    }
}

class DurationsNegativeCasesTest {

    @Test
    fun `zero is spelled as no pause, not as 0 seconds`() {
        assertEquals("без паузы", Durations.label(0))
    }

    @Test
    fun `a value that is not a preset is offered in its own place in the row`() {
        val options = Durations.options(Durations.ALARM_LIMIT, 90)

        assertEquals(listOf(60, 90, 180, 300, 600, 1800), options)
    }
}

class DurationsPositiveCasesTest {

    @Test
    fun `whole minutes lose the seconds`() {
        assertEquals("5 мин", Durations.label(300))
    }

    @Test
    fun `an odd value keeps both parts`() {
        assertEquals("1 мин 30 с", Durations.label(90))
    }

    @Test
    fun `a preset value leaves the row as it is`() {
        assertEquals(Durations.COOLDOWN, Durations.options(Durations.COOLDOWN, 30))
    }
}
