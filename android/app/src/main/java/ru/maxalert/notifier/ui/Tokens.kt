package ru.maxalert.notifier.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens in three layers: primitive → semantic → component.
 *
 * The rule the layers exist to enforce: no component ever writes a raw colour. Before this
 * file the alert colours lived as hex literals inside the screens, so "red" meant one thing
 * in the card, another in the notification, and nothing at all in the dark theme. A unit
 * test now fails the build if a raw Color(0xFF…) appears outside this file.
 */

/** Layer 1 — primitives. Raw values, named after what they are, never used directly by UI. */
internal object Primitives {
    const val RED_700 = 0xFFB3261E
    const val ORANGE_800 = 0xFFE65100
    const val AMBER_600 = 0xFFF9A825
    const val GREEN_800 = 0xFF2E7D32
    const val RED_300 = 0xFFFF6B60

    // The interface itself is graphite: red is spent only on an actual alert, so the alert
    // still reads as one. An app painted in its own alarm colour has no alarm colour left.
    const val GRAPHITE_800 = 0xFF2F3437
    const val GRAPHITE_300 = 0xFFC8CDD0
    const val GRAPHITE_600 = 0xFF5A6266

    const val NEUTRAL_0 = 0xFFFFFFFF
    const val NEUTRAL_900 = 0xFF1A1A1A
    const val NEUTRAL_950 = 0xFF121212
    const val NEUTRAL_850 = 0xFF1E1E1E
    const val NEUTRAL_100 = 0xFFF5F5F5
}

/**
 * Layer 2 — semantic. What a colour is *for*, so light and dark can differ without any
 * screen knowing about it.
 */
data class AlertPalette(
    val statusOk: Color,
    val statusWarn: Color,
    val statusFail: Color,
    val onStatus: Color,
)

/**
 * Layer 3 — component tokens: the spacing rhythm and the sizes components ask for by name.
 *
 * Every value is a multiple of 4 -- the 8-point grid -- and a test enforces it. Random spacing
 * is the fastest way for one screen to stop looking like the rest of the app; related things
 * sit one step apart, unrelated things two.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Android's minimum touch target; anything tappable is at least this. */
    val touchTarget: Dp = 48.dp
    val statusDot: Dp = 16.dp
    val bulletDot: Dp = 8.dp
    val inlineIcon: Dp = 20.dp
    val alarmButtonHeight: Dp = 96.dp
}

/** Drawn line weights. Not spacing: a hairline is a stroke, and it stays off the grid. */
object Strokes {
    val hairline: Dp = 2.dp
}

/** Corner radii: one value for cards, one for anything tappable, one for pills. */
object Radii {
    val card: Dp = 20.dp
    val control: Dp = 16.dp
    val pill: Dp = 32.dp
}

/**
 * Four sizes, two weights -- for the whole app.
 *
 * More than that reads as several apps stitched together, and the way this one drifted was by
 * reaching for one more Material slot every time something needed emphasis. Hierarchy comes
 * from these four plus [TextAlpha], never from a fifth size.
 */
object TypeScale {
    val display: TextUnit = 28.sp
    val title: TextUnit = 18.sp
    val body: TextUnit = 15.sp
    val caption: TextUnit = 13.sp

    /**
     * The one exception, and only on the full-screen alarm.
     *
     * That screen is read across a dark room by someone who has just been woken; 28sp is a
     * reading size, not a signal. It lives here rather than as a raw 44.sp inside the screen
     * so it stays one decision instead of a habit.
     */
    val hero: TextUnit = 44.sp
    val heroLineHeight: TextUnit = 52.sp

    val displayLineHeight: TextUnit = 34.sp
    val titleLineHeight: TextUnit = 24.sp
    val bodyLineHeight: TextUnit = 22.sp
    val captionLineHeight: TextUnit = 18.sp
}

/**
 * Text hierarchy by opacity rather than by another colour.
 *
 * [secondary] is deliberately not lower than 0.7: near-black at 0.7 on white still measures
 * above the 4.5:1 WCAG asks of body text, and a contrast test holds it there. Supporting text
 * nobody can read is not supporting anything.
 */
object TextAlpha {
    const val PRIMARY = 1.0f
    const val BODY = 0.85f
    const val SECONDARY = 0.7f
}

val LightAlertPalette = AlertPalette(
    statusOk = Color(Primitives.GREEN_800),
    statusWarn = Color(Primitives.AMBER_600),
    statusFail = Color(Primitives.RED_700),
    onStatus = Color(Primitives.NEUTRAL_0),
)

val DarkAlertPalette = AlertPalette(
    // Dark mode uses lighter tonal variants rather than the same saturated hues.
    statusOk = Color(0xFF66BB6A),
    statusWarn = Color(0xFFFFCA28),
    statusFail = Color(Primitives.RED_300),
    onStatus = Color(Primitives.NEUTRAL_900),
)

val LocalAlertPalette = staticCompositionLocalOf { LightAlertPalette }
