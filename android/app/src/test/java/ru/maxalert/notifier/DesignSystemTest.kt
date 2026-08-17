package ru.maxalert.notifier

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxalert.notifier.ui.Radii
import ru.maxalert.notifier.ui.Spacing
import ru.maxalert.notifier.ui.TextAlpha
import ru.maxalert.notifier.ui.TypeScale
import java.io.File
import kotlin.math.pow

/**
 * The design rules that a reviewer would otherwise have to hold in their head.
 *
 * Every one of these was broken at some point in this app's own history: spacing invented per
 * screen, a sixth font size added because something needed emphasis, supporting text faded to
 * the point of being unreadable. A rule nothing checks is a rule that lasts one session.
 */
private fun grid(value: Dp) = (value.value.toInt() % 4) == 0

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

/** Near-black text at [alpha] over white, as the eye actually receives it. */
private fun fadedInkOnWhite(alpha: Float): Long {
    val ink = 0x1A
    val blended = (alpha * ink + (1 - alpha) * 0xFF).toInt().toLong()
    return (0xFF shl 24).toLong() or (blended shl 16) or (blended shl 8) or blended
}

private fun contrastWithWhite(argb: Long): Double =
    (1.0 + 0.05) / (luminance(argb) + 0.05)

private fun spacingValues(): List<Pair<String, Dp>> = listOf(
    "xs" to Spacing.xs,
    "sm" to Spacing.sm,
    "md" to Spacing.md,
    "lg" to Spacing.lg,
    "xl" to Spacing.xl,
    "xxl" to Spacing.xxl,
    "xxxl" to Spacing.xxxl,
    "touchTarget" to Spacing.touchTarget,
    "statusDot" to Spacing.statusDot,
    "bulletDot" to Spacing.bulletDot,
    "inlineIcon" to Spacing.inlineIcon,
    "alarmButtonHeight" to Spacing.alarmButtonHeight,
)

class DesignSystemNegativeCasesTest {

    @Test
    fun `no spacing token is off the 4dp grid`() {
        val offenders = spacingValues().filterNot { (_, value) -> grid(value) }.map { it.first }

        assertTrue("Не по сетке 4dp: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no corner radius is off the grid either`() {
        val offenders = listOf("card" to Radii.card, "control" to Radii.control, "pill" to Radii.pill)
            .filterNot { (_, value) -> grid(value) }
            .map { it.first }

        assertTrue("Радиус не по сетке: $offenders", offenders.isEmpty())
    }

    @Test
    fun `secondary text stays above the WCAG threshold for body text`() {
        // 0.6 was the first attempt and measures about 4.4:1 -- under the line, which is how
        // supporting text ends up decorative.
        assertTrue(
            "0.6 должен проваливать порог, иначе проверка бесполезна",
            contrastWithWhite(fadedInkOnWhite(0.55f)) < 4.5,
        )
    }

    @Test
    fun `a touch target below Android's minimum would fail`() {
        assertTrue(Spacing.touchTarget.value >= 48f)
    }
}

class DesignSystemPositiveCasesTest {

    @Test
    fun `the type scale has four reading sizes plus one for the alarm screen`() {
        val reading: List<TextUnit> =
            listOf(TypeScale.display, TypeScale.title, TypeScale.body, TypeScale.caption)

        assertEquals(4, reading.distinct().size)
        assertTrue("Шкала должна убывать: $reading", reading.zipWithNext().all { (a, b) -> a > b })
        assertTrue("Экран тревоги крупнее всего остального", TypeScale.hero > TypeScale.display)
    }

    @Test
    fun `both faded text levels are readable on white`() {
        assertTrue(contrastWithWhite(fadedInkOnWhite(TextAlpha.BODY)) >= 4.5)
        assertTrue(contrastWithWhite(fadedInkOnWhite(TextAlpha.SECONDARY)) >= 4.5)
    }

    @Test
    fun `the alpha ladder actually descends`() {
        assertTrue(TextAlpha.PRIMARY > TextAlpha.BODY)
        assertTrue(TextAlpha.BODY > TextAlpha.SECONDARY)
    }

    @Test
    fun `screens ask for text styles by name instead of setting a size`() {
        // A raw sp inside a screen is how the fifth and sixth font sizes arrived last time.
        val allowed = setOf("ui/Tokens.kt", "Theme.kt")
        val offenders = File("src/main/java/ru/maxalert/notifier")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file -> allowed.any { file.path.endsWith(it) } }
            .filter { file -> Regex("""\d+\.sp""").containsMatchIn(file.readText()) }
            .map { it.path }
            .toList()

        assertTrue("Размер шрифта задан в экране: $offenders", offenders.isEmpty())
    }
}
