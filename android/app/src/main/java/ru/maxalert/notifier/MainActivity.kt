package ru.maxalert.notifier

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventLog.load(this)
        AlertState.load(this)
        requestNotificationPermission()
        if (SettingsStore(this).load().enabled) MaxWatchService.start(this)
        setContent { MaxAlertTheme { HomeScreen() } }
    }

    override fun onPause() {
        super.onPause()
        AlarmPreview.stop(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
