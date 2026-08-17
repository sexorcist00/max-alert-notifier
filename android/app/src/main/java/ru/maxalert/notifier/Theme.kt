package ru.maxalert.notifier

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import ru.maxalert.notifier.ui.DarkAlertPalette
import ru.maxalert.notifier.ui.LightAlertPalette
import ru.maxalert.notifier.ui.LocalAlertPalette
import ru.maxalert.notifier.ui.Primitives
import ru.maxalert.notifier.ui.Radii
import ru.maxalert.notifier.ui.TypeScale

/**
 * The semantic layer of the token system.
 *
 * Both themes are defined together rather than one being derived from the other: dark mode
 * needs lighter, less saturated variants to keep text contrast, and inverting light values
 * produces surfaces that look right in a screenshot and wash out on a phone at night.
 *
 * The background is a shade off white while cards are white: the 60/30/10 split needs the
 * neutral 60% to be a ground the cards can sit on, and cards on an identical background have
 * to be outlined or shadowed to exist at all.
 */
private val LightColors = lightColorScheme(
    primary = Color(Primitives.GRAPHITE_800),
    onPrimary = Color(Primitives.NEUTRAL_0),
    secondary = Color(Primitives.GRAPHITE_600),
    onSecondary = Color(Primitives.NEUTRAL_0),
    error = Color(Primitives.RED_700),
    onError = Color(Primitives.NEUTRAL_0),
    background = Color(Primitives.NEUTRAL_100),
    onBackground = Color(Primitives.NEUTRAL_900),
    surface = Color(Primitives.NEUTRAL_0),
    onSurface = Color(Primitives.NEUTRAL_900),
    surfaceVariant = Color(Primitives.NEUTRAL_100),
    onSurfaceVariant = Color(Primitives.NEUTRAL_900),
)

private val DarkColors = darkColorScheme(
    primary = Color(Primitives.GRAPHITE_300),
    onPrimary = Color(Primitives.NEUTRAL_900),
    secondary = Color(Primitives.GRAPHITE_300),
    onSecondary = Color(Primitives.NEUTRAL_900),
    error = Color(Primitives.RED_300),
    onError = Color(Primitives.NEUTRAL_900),
    background = Color(Primitives.NEUTRAL_950),
    onBackground = Color(Primitives.NEUTRAL_0),
    surface = Color(Primitives.NEUTRAL_850),
    onSurface = Color(Primitives.NEUTRAL_0),
    surfaceVariant = Color(Primitives.NEUTRAL_850),
    onSurfaceVariant = Color(Primitives.NEUTRAL_0),
)

/**
 * Every Material slot the app uses, mapped onto four sizes and two weights.
 *
 * Material ships fifteen text styles, and taking them as they come is exactly how this app
 * ended up with six sizes and emphasis that meant something different on every screen. The
 * slots stay -- components ask for them by name -- but they all resolve to the same small
 * scale, so no screen can invent a new level of emphasis by picking another one.
 */
private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = TypeScale.display,
        lineHeight = TypeScale.displayLineHeight,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = TypeScale.title,
        lineHeight = TypeScale.titleLineHeight,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = TypeScale.title,
        lineHeight = TypeScale.titleLineHeight,
        fontWeight = FontWeight.Bold,
    ),
    bodyLarge = TextStyle(
        fontSize = TypeScale.body,
        lineHeight = TypeScale.bodyLineHeight,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = TypeScale.body,
        lineHeight = TypeScale.bodyLineHeight,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = TypeScale.caption,
        lineHeight = TypeScale.captionLineHeight,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = TypeScale.body,
        lineHeight = TypeScale.bodyLineHeight,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = TextStyle(
        fontSize = TypeScale.caption,
        lineHeight = TypeScale.captionLineHeight,
        fontWeight = FontWeight.Bold,
    ),
    labelSmall = TextStyle(
        fontSize = TypeScale.caption,
        lineHeight = TypeScale.captionLineHeight,
        fontWeight = FontWeight.Normal,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.control),
    small = RoundedCornerShape(Radii.control),
    medium = RoundedCornerShape(Radii.card),
    large = RoundedCornerShape(Radii.card),
    extraLarge = RoundedCornerShape(Radii.card),
)

@Composable
fun MaxAlertTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalAlertPalette provides if (dark) DarkAlertPalette else LightAlertPalette,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
