package ru.maxalert.notifier

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import ru.maxalert.notifier.ui.DarkAlertPalette
import ru.maxalert.notifier.ui.LightAlertPalette
import ru.maxalert.notifier.ui.LocalAlertPalette
import ru.maxalert.notifier.ui.Primitives

/**
 * The semantic layer of the token system.
 *
 * Both themes are defined together rather than one being derived from the other: dark mode
 * needs lighter, less saturated variants to keep text contrast, and inverting light values
 * produces surfaces that look right in a screenshot and wash out on a phone at night.
 */
private val LightColors = lightColorScheme(
    primary = Color(Primitives.RED_700),
    onPrimary = Color(Primitives.NEUTRAL_0),
    error = Color(Primitives.RED_700),
    onError = Color(Primitives.NEUTRAL_0),
    background = Color(Primitives.NEUTRAL_0),
    onBackground = Color(Primitives.NEUTRAL_900),
    surface = Color(Primitives.NEUTRAL_0),
    onSurface = Color(Primitives.NEUTRAL_900),
    surfaceVariant = Color(Primitives.NEUTRAL_100),
    onSurfaceVariant = Color(Primitives.NEUTRAL_900),
)

private val DarkColors = darkColorScheme(
    primary = Color(Primitives.RED_300),
    onPrimary = Color(Primitives.NEUTRAL_900),
    error = Color(Primitives.RED_300),
    onError = Color(Primitives.NEUTRAL_900),
    background = Color(Primitives.NEUTRAL_950),
    onBackground = Color(Primitives.NEUTRAL_0),
    surface = Color(Primitives.NEUTRAL_950),
    onSurface = Color(Primitives.NEUTRAL_0),
    surfaceVariant = Color(Primitives.NEUTRAL_850),
    onSurfaceVariant = Color(Primitives.NEUTRAL_0),
)

@Composable
fun MaxAlertTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalAlertPalette provides if (dark) DarkAlertPalette else LightAlertPalette,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}
