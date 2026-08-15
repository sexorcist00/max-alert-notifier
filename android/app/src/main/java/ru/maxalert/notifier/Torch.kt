package ru.maxalert.notifier

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Strobes the camera flash on the alarm's cadence -- the channel that still reaches someone
 * in a loud room or with the phone face-down.
 *
 * The rate is clamped to three flashes per second (WCAG 2.3.1 general flash threshold);
 * faster strobes can trigger a photosensitive seizure, and no alarm is worth that.
 */
object Torch {

    private const val TAG = "MaxAlert"

    private val handler = Handler(Looper.getMainLooper())
    private var cameraId: String? = null
    private var running = false
    private var step = 0
    private var timings: LongArray = longArrayOf()

    val available: Boolean get() = cameraId != null

    fun start(context: Context, pattern: AlarmPattern) {
        stop(context)
        cameraId = findTorchCamera(context) ?: return

        timings = safeTimings(pattern.timings)
        running = true
        step = 0
        tick(context)
    }

    fun stop(context: Context) {
        running = false
        handler.removeCallbacksAndMessages(null)
        setTorch(context, false)
    }

    private fun tick(context: Context) {
        if (!running) return
        val on = step % 2 == 0
        setTorch(context, on)
        val delay = timings[step % timings.size]
        step = (step + 1) % timings.size
        handler.postDelayed({ tick(context) }, delay.coerceAtLeast(1))
    }

    /** Merges anything faster than the flash threshold into slower, safe pulses. */
    private fun safeTimings(source: LongArray): LongArray = source
        .map { duration -> duration.coerceAtLeast(AlarmPattern.MIN_FLASH_INTERVAL_MS) }
        .toLongArray()

    private fun setTorch(context: Context, on: Boolean) {
        val id = cameraId ?: return
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        runCatching { manager.setTorchMode(id, on) }
            .onFailure { Log.w(TAG, "torch failed: ${it.message}") }
    }

    private fun findTorchCamera(context: Context): String? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        return runCatching {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
}
