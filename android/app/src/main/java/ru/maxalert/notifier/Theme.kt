package ru.maxalert.notifier

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Red = Color(0xFFB3261E)
private val RedDark = Color(0xFFFF6B60)

private val LightColors = lightColorScheme(primary = Red, error = Red)
private val DarkColors = darkColorScheme(primary = RedDark, error = RedDark)

@Composable
fun MaxAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
