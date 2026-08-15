package ru.maxalert.notifier

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer build and hands the APK to the system installer.
 *
 * Asked once when the app opens, never in the background: an update is an offer, and a
 * watcher that reinstalls itself unattended is a watcher that can be off when it matters.
 */
object UpdateChecker {

    private const val TAG = "MaxAlert"
    private const val RELEASES_URL =
        "https://api.github.com/repos/sexorcist00/max-alert-notifier/releases/latest"

    data class Release(val version: String, val notes: String, val apkUrl: String)

    var available by mutableStateOf<Release?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    suspend fun check(context: Context, quiet: Boolean = true) {
        if (busy) return
        busy = true
        if (!quiet) message = "Проверяю…"

        val result = runCatching { withContext(Dispatchers.IO) { fetch() } }
        busy = false

        result.onSuccess { release ->
            when {
                release == null -> message = if (quiet) null else "Релизов пока нет"
                isNewer(release.version, BuildConfig.VERSION_NAME) -> {
                    available = release
                    message = null
                }

                else -> {
                    available = null
                    message = if (quiet) null else "Установлена последняя версия (${BuildConfig.VERSION_NAME})"
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "update check failed: ${error.message}")
            // A failed check is not an alarm failure; say it only when asked.
            message = if (quiet) null else "Не удалось проверить: ${error.message}"
        }
    }

    suspend fun download(context: Context, release: Release): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "kod-krasny-${release.version}.apk",
            )
            URL(release.apkUrl).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.onFailure { Log.w(TAG, "download failed: ${it.message}") }.getOrNull()
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { message = "Не удалось открыть установщик: ${it.message}" }
    }

    fun dismiss() {
        available = null
    }

    private fun fetch(): Release? {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode == 404) return null
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub ответил ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    return Release(
                        version = version,
                        notes = json.optString("body").take(300),
                        apkUrl = asset.optString("browser_download_url"),
                    )
                }
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    /** Compares dotted versions numerically, so 1.10.0 is newer than 1.9.0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.split('.').mapNotNull { it.trim().toIntOrNull() }
        val right = current.split('.').mapNotNull { it.trim().toIntOrNull() }
        if (left.isEmpty()) return false
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
