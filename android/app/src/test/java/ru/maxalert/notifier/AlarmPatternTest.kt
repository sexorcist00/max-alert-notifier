package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cadences are quoted from standards, so the numbers are the specification -- if someone
 * "tidies" them, the alarm stops being the signal people were trained to recognise.
 */
class AlarmPatternNegativeCasesTest {

    @Test
    fun `an unknown id falls back to the evacuation pattern`() {
        assertEquals(AlarmPattern.TEMPORAL_3, AlarmPattern.fromId("does-not-exist"))
        assertEquals(AlarmPattern.TEMPORAL_3, AlarmPattern.fromId(null))
    }

    @Test
    fun `no pattern flashes the torch faster than three times a second`() {
        AlarmPattern.entries.forEach { pattern ->
            val clamped = pattern.timings.map { it.coerceAtLeast(AlarmPattern.MIN_FLASH_INTERVAL_MS) }
            assertTrue(
                "${pattern.id} would flash faster than the safe threshold",
                clamped.all { duration -> duration >= AlarmPattern.MIN_FLASH_INTERVAL_MS },
            )
        }
    }

    @Test
    fun `the flash threshold is three per second`() {
        // 1000 ms / 3 flashes, rounded up: anything shorter is above the WCAG 2.3.1 threshold.
        assertTrue(AlarmPattern.MIN_FLASH_INTERVAL_MS >= 333)
    }
}

class AlarmPatternPositiveCasesTest {

    @Test
    fun `T-3 is three half-second pulses and a one-and-a-half second gap`() {
        // ISO 8201 / ANSI-ASA S3.41 temporal pattern 3.
        assertEquals(
            listOf(500L, 500L, 500L, 500L, 500L, 1500L),
            AlarmPattern.TEMPORAL_3.timings.toList(),
        )
        assertEquals(4000L, AlarmPattern.TEMPORAL_3.cycleMs)
    }

    @Test
    fun `T-4 is four short pulses and a five second gap`() {
        val timings = AlarmPattern.TEMPORAL_4.timings.toList()
        assertEquals(4, timings.count { it == 100L } / 2 + 1) // four pulses, three inner gaps
        assertEquals(5000L, timings.last())
    }

    @Test
    fun `every pattern is selectable by its stored id`() {
        AlarmPattern.entries.forEach { pattern ->
            assertEquals(pattern, AlarmPattern.fromId(pattern.id))
        }
    }

    @Test
    fun `every pattern names the standard it comes from`() {
        AlarmPattern.entries.forEach { pattern ->
            assertTrue(pattern.standard.isNotBlank())
            assertTrue(pattern.label.isNotBlank())
        }
    }
}
