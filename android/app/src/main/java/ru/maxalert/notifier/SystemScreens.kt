package ru.maxalert.notifier

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens EMUI's own "Запуск приложений" screen when it exists.
 *
 * On Huawei that screen -- not the standard battery page -- is the one that decides whether
 * the watcher survives the night, and it cannot be reached from the generic settings intent.
 * Falls back to the app's details page everywhere else.
 */
internal fun Context.openBatterySettings() {
    val huawei = Intent().setClassName(
        "com.huawei.systemmanager",
        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (runCatching { startActivity(huawei) }.isFailure) {
        runCatching { startActivity(fallback) }
    }
}

/** Asks Android to stop restricting the app in the background; a no-op if already allowed. */
internal fun Context.requestBatteryFreedom() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(intent) }.isFailure) openBatterySettings()
}

internal fun Context.openNotificationAccess() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun Context.openDndAccess() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
