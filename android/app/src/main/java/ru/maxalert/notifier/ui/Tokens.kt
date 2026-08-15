package ru.maxalert.notifier.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Layer 3 — component tokens: spacing rhythm and sizes the components ask for by name. */
object Spacing {
    val hairline: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 48.dp

    /** Android's minimum touch target; anything tappable is at least this. */
    val touchTarget: Dp = 48.dp
    val statusDot: Dp = 14.dp
    val bulletDot: Dp = 8.dp
    val inlineIcon: Dp = 18.dp
    val alarmButtonHeight: Dp = 96.dp
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
