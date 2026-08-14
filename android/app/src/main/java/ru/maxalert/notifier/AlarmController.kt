package ru.maxalert.notifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

/**
 * The alarm: sound on the ALARM stream, vibration, and a full-screen notification that
 * wakes the screen even on the lock screen.
 *
 * The ALARM stream is the point -- "Do not disturb" leaves alarms audible, which is why
 * the sound does not go through the notification channel.
 */
object AlarmController {

    private const val TAG = "MaxAlert"
    private const val CHANNEL_ID = "max-alert-alarm"
    private const val NOTIFICATION_ID = 4242

    var ringing by mutableStateOf(false)
        private set
    var chat by mutableStateOf("")
        private set
    var message by mutableStateOf("")
        private set

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousVolume: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { appContext?.let { stop(it) } }
    private var appContext: Context? = null

    fun trigger(context: Context, settings: AlertSettings, chatTitle: String, text: String) {
        val app = context.applicationContext
        appContext = app
        if (ringing) {
            Log.i(TAG, "already ringing, ignoring trigger")
            return
        }

        ringing = true
        chat = chatTitle
        message = text

        ensureChannel(app)
        notify(app, chatTitle, text)
        holdWakeLock(app, settings.loopSeconds)
        raiseVolume(app, settings)
        startSound(app, settings)
        startVibration(app, settings)

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, settings.loopSeconds * 1000L)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        handler.removeCallbacks(autoStop)

        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null

        runCatching { vibrator(app).cancel() }

        previousVolume?.let { level ->
            runCatching {
                audioManager(app).setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
            }
        }
        previousVolume = null

        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null

        notificationManager(app).cancel(NOTIFICATION_ID)
        ringing = false
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_description)
            setSound(null, null) // the sound is played by MediaPlayer on the alarm stream
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        notificationManager(context).createNotificationChannel(channel)
    }

    private fun notify(context: Context, chatTitle: String, text: String) {
        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, StopReceiver::class.java).setAction(StopReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(context.getString(R.string.alarm_title, chatTitle))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .addAction(R.drawable.ic_alert, context.getString(R.string.stop), stop)
            .build()

        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    private fun holdWakeLock(context: Context, seconds: Int) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "max-alert:alarm").apply {
            setReferenceCounted(false)
            acquire(seconds * 1000L)
        }
    }

    private fun raiseVolume(context: Context, settings: AlertSettings) {
        if (!settings.forceMaxVolume) return
        val audio = audioManager(context)
        runCatching {
            previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }.onFailure {
            // Happens without "Do not disturb" access; the alarm still sounds at the current level.
            Log.w(TAG, "cannot raise the alarm volume: ${it.message}")
            previousVolume = null
        }
    }

    private fun startSound(context: Context, settings: AlertSettings) {
        val uri = alarmSound(settings)
        if (uri == null) {
            Log.w(TAG, "no alarm sound available on this device")
            return
        }
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
        }.onFailure { Log.e(TAG, "cannot play the alarm sound", it) }
    }

    private fun alarmSound(settings: AlertSettings): Uri? =
        settings.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun startVibration(context: Context, settings: AlertSettings) {
        if (!settings.vibrate) return
        val pattern = longArrayOf(0, 1000, 600)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            vibrator(context).vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
        }.onFailure { Log.w(TAG, "cannot vibrate: ${it.message}") }
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun audioManager(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
