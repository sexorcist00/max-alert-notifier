package ru.maxalert.notifier

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * The round badge shown beside the pinned notification.
 *
 * Drawn rather than shipped as an image so it always carries the colour of the level that is
 * actually standing -- a picture file would be one more thing that can disagree with the
 * state. Colour never carries the meaning alone: the same line always spells the level out.
 */
object NotificationArt {

    private const val SIZE = 192
    private val cache = HashMap<String, Bitmap>()

    fun badge(level: AlertLevel, online: Boolean): Bitmap {
        val key = "${level.id}:$online"
        cache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = SIZE / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = level.colorArgb.toInt()
        }
        canvas.drawCircle(radius, radius, radius, fill)

        // A quiet ring when the watcher is offline: the badge stops looking "solid".
        if (!online) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = SIZE * 0.06f
                color = Color.argb(160, 255, 255, 255)
            }
            canvas.drawCircle(radius, radius, radius - ring.strokeWidth, ring)
        }

        val glyph = when (level) {
            AlertLevel.RED -> "!"
            AlertLevel.YELLOW_HIGH -> "!"
            AlertLevel.YELLOW -> "•"
            AlertLevel.NONE -> if (online) "✓" else "?"
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = level.onColorArgb.toInt()
            textSize = SIZE * 0.62f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val baseline = radius - (text.descent() + text.ascent()) / 2
        canvas.drawText(glyph, radius, baseline, text)

        cache[key] = bitmap
        return bitmap
    }
}
