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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The screen carries the level's own colour, so the answer is readable across a
            // dark room before a single word is.
            .background(Color(AlertState.state.level.colorArgb))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = AlertState.state.level.takeIf { it != AlertLevel.NONE }?.title ?: "ТРЕВОГА",
            color = Color.White,
            fontSize = 44.sp,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.height(24.dp)) {}
        Text(
            text = chat.ifBlank { "MAX" },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.height(12.dp)) {}
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.height(48.dp)) {}
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(AlertState.state.level.colorArgb),
            ),
        ) {
            Text(text = "СТОП", fontSize = 32.sp)
        }
    }
}
