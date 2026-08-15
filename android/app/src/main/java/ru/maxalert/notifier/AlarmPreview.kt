package ru.maxalert.notifier

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Short previews for the settings screen, so a choice can be heard where it is made
 * instead of being tested by staging a real alarm.
 *
 * Deliberately not the alarm: it does not touch the volume, does not latch any state and
 * stops itself after a few seconds.
 */
object AlarmPreview {

    private const val PREVIEW_MS = 4000L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    var playing by mutableStateOf<String?>(null)
        private set

    fun toggleSound(context: Context, soundUri: String?) {
        if (playing == soundUri) {
            stop(context)
            return
        }
        stop(context)

        val uri = resolve(context, soundUri) ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
            playing = soundUri
            handler.postDelayed({ stop(context) }, PREVIEW_MS)
        }
    }

    fun vibrate(context: Context, settings: AlertSettings) {
        val timings = longArrayOf(0) + settings.pattern.timings
        val strength = settings.vibrationStrength.coerceIn(1, 100) * 255 / 100
        val amplitudes = IntArray(timings.size) { index -> if (index % 2 == 0) 0 else strength }
        val device = vibrator(context)
        runCatching {
            val effect = if (device.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            device.vibrate(effect)
        }
    }

    fun flash(context: Context, settings: AlertSettings) {
        Torch.start(context, settings.pattern)
        handler.postDelayed({ Torch.stop(context) }, PREVIEW_MS)
    }

    fun stop(context: Context) {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = null
        runCatching { vibrator(context).cancel() }
        Torch.stop(context)
    }

    private fun resolve(context: Context, soundUri: String?): Uri? {
        val configured = soundUri ?: AlertSettings.DEFAULT_SOUND
        if (configured.startsWith(AlarmController.BUNDLED_PREFIX)) {
            val name = configured.removePrefix(AlarmController.BUNDLED_PREFIX)
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            return if (id == 0) null else Uri.parse("android.resource://${context.packageName}/$id")
        }
        return runCatching { Uri.parse(configured) }.getOrNull()
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}
