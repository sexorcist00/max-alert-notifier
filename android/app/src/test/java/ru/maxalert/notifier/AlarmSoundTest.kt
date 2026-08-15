package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alarm must never inherit the phone's alarm ringtone: the signal has to sound the same
 * on every device, and a borrowed tone changes meaning when someone edits an unrelated setting.
 */
class AlarmSoundNegativeCasesTest {

    @Test
    fun `the default sound is not a system ringtone`() {
        val settings = AlertSettings()
        assertFalse(settings.soundUri.isNullOrBlank())
        assertFalse(settings.soundUri!!.startsWith("content://"))
    }
}

class AlarmSoundPositiveCasesTest {

    @Test
    fun `the default sound ships with the app`() {
        assertTrue(AlertSettings.DEFAULT_SOUND.startsWith(AlarmController.BUNDLED_PREFIX))
        assertEquals(
            "alarm_missile",
            AlertSettings.DEFAULT_SOUND.removePrefix(AlarmController.BUNDLED_PREFIX),
        )
    }

    @Test
    fun `the default cadence does not chop up the default wail`() {
        // A civil-defence wail carries its own rise and fall; pulsing it would break the shape.
        assertEquals(AlarmPattern.CONTINUOUS, AlertSettings().pattern)
    }
}
