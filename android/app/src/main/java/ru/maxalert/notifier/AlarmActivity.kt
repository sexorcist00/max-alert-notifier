package ru.maxalert.notifier

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.maxalert.notifier.ui.Spacing
import ru.maxalert.notifier.ui.TypeScale

/** Full-screen alarm: shows over the lock screen, wakes the display, one big STOP button. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            MaxAlertTheme {
                AlarmScreen(
                    chat = AlarmController.chat,
                    message = AlarmController.message,
                    ringing = AlarmController.ringing,
                    onStop = {
                        AlarmController.stop(this)
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) AlarmController.stop(this)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Full brightness: at night the screen is often the first thing that registers,
        // and a dimmed one can be missed entirely.
        window.attributes = window.attributes.apply { screenBrightness = 1.0f }
    }
}

@Composable
private fun AlarmScreen(chat: String, message: String, ringing: Boolean, onStop: () -> Unit) {
    LaunchedEffect(ringing) {
        if (!ringing) onStop()
    }

    val ink = Color(AlertState.state.level.onColorArgb)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The screen carries the level's own colour, so the answer is readable across a
            // dark room before a single word is.
            .background(Color(AlertState.state.level.colorArgb))
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The message takes the room it needs; STOP is pinned to the bottom, in the thumb
        // zone. Centred, it sat mid-screen -- the part of the phone a hand holding it cannot
        // reach, which is a poor place for the one button someone half-awake has to find.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = AlertState.state.level.takeIf { it != AlertLevel.NONE }?.title ?: "ТРЕВОГА",
                color = ink,
                fontSize = TypeScale.hero,
                lineHeight = TypeScale.heroLineHeight,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = chat.ifBlank { "MAX" },
                color = ink,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = message,
                color = ink,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.alarmButtonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = ink,
                contentColor = Color(AlertState.state.level.colorArgb),
            ),
        ) {
            Text(text = "СТОП", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Звук выключится, состояние тревоги останется",
            color = ink,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
