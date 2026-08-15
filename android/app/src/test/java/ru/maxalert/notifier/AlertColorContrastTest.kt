package ru.maxalert.notifier

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The level colours have to be readable, not just recognisable.
 *
 * WCAG asks 4.5:1 for normal text; white on the amber level measured about 2:1, which is how
 * a "yellow code" card ends up looking fine to whoever picked it and unreadable outside.
 */
private fun luminance(argb: Long): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    val r = channel(((argb shr 16) and 0xFF).toInt())
    val g = channel(((argb shr 8) and 0xFF).toInt())
    val b = channel((argb and 0xFF).toInt())
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun contrast(first: Long, second: Long): Double {
    val a = luminance(first)
    val b = luminance(second)
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}

class AlertColorContrastNegativeCasesTest {

    @Test
    fun `white on the amber level is the pairing this test exists to prevent`() {
        assertTrue(
            "Белый на жёлтом должен проваливать порог — иначе проверка ничего не ловит",
            contrast(AlertLevel.YELLOW.colorArgb, 0xFFFFFFFF) < 4.5,
        )
    }
}

class AlertColorContrastPositiveCasesTest {

    @Test
    fun `every level reads at the normal-text threshold`() {
        AlertLevel.entries.forEach { level ->
            val ratio = contrast(level.colorArgb, level.onColorArgb)
            assertTrue(
                "${level.id}: контраст ${"%.2f".format(ratio)}:1, нужно не меньше 4.5:1",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `the levels are distinguishable from each other, not only from their text`() {
        val red = AlertLevel.RED.colorArgb
        val yellowHigh = AlertLevel.YELLOW_HIGH.colorArgb
        val yellow = AlertLevel.YELLOW.colorArgb
        assertTrue(contrast(red, yellow) > 1.8)
        assertTrue(contrast(yellowHigh, yellow) > 1.5)
    }
}
