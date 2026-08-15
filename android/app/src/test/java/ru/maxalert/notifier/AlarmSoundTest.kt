package ru.maxalert.notifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alarm must never inherit the phone's alarm ringtone: the signal has to sound the same
 * on every device, and the standard cadences only carry their meaning with the shipped tone.
 */
class AlarmSoundNegativeCasesTest {

    @Test
    fun `no built-in choice points at a system ringtone`() {
        val settings = AlertSettings()
        assertFalse(settings.soundUri.isNullOrBlank())
        assertFalse(settings.soundUri!!.startsWith("content://"))
    }
}

class AlarmSoundPositiveCasesTest {

    @Test
    fun `the default sound ships with the app`() {
        assertTrue(AlertSettings.DEFAULT_SOUND.startsWith(AlarmController.BUNDLED_PREFIX))
        assertEquals("alarm_t3", AlertSettings.DEFAULT_SOUND.removePrefix(AlarmController.BUNDLED_PREFIX))
    }

    @Test
    fun `the default cadence and the default tone are the same standard`() {
        // T-3 tone with the T-3 pattern: sound, vibration and torch tell one story.
        assertEquals(AlarmPattern.TEMPORAL_3, AlertSettings().pattern)
        assertTrue(AlertSettings.DEFAULT_SOUND.endsWith("t3"))
    }

    private fun assertEquals(expected: Any?, actual: Any?) =
        org.junit.Assert.assertEquals(expected, actual)
}
