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

class AlarmCatalogueNegativeCasesTest {

    @Test
    fun `the default sound is not missing from the picker`() {
        // This is the bug this test exists for: the default pointed at a tone the list did
        // not contain, so the screen showed "свой файл" for a sound the app itself chose.
        assertTrue(
            "Звук по умолчанию отсутствует в списке выбора",
            AlarmSounds.contains(AlertSettings.DEFAULT_SOUND),
        )
    }

    @Test
    fun `a picked file is not mistaken for a bundled tone`() {
        assertFalse(AlarmSounds.contains("content://media/external/audio/media/42"))
        assertFalse(AlarmSounds.contains(null))
    }

    @Test
    fun `no two entries share a sound or a label`() {
        val uris = AlarmSounds.CATALOGUE.map { it.uri }
        val labels = AlarmSounds.CATALOGUE.map { it.label }
        assertEquals(uris.size, uris.toSet().size)
        assertEquals(labels.size, labels.toSet().size)
    }
}

class AlarmCataloguePositiveCasesTest {

    @Test
    fun `both civil-defence sirens are offered`() {
        val labels = AlarmSounds.CATALOGUE.map { it.label }
        assertTrue(labels.contains("Ракетная опасность"))
        assertTrue(labels.contains("Воздушная тревога"))
    }

    @Test
    fun `every entry is a bundled file, never a system ringtone`() {
        AlarmSounds.CATALOGUE.forEach { choice ->
            assertTrue(choice.uri.startsWith(AlarmController.BUNDLED_PREFIX))
        }
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
