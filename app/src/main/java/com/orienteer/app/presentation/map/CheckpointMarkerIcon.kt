package com.orienteer.app.presentation.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * Creates a drawable icon for a checkpoint marker: circle badge with contrasting stroke
 * and centered label, so checkpoints stand out on the map.
 */
object CheckpointMarkerIcon {

    private const val CIRCLE_DP = 42f  // 56 * 0.75
    private const val STROKE_DP = 3f  // 4 * 0.75
    private const val TEXT_SIZE_SP = 16.5f  // 22 * 0.75
    private const val START_TEXT_SIZE_SP = 13.5f  // 18 * 0.75

    /**
     * @param context Android context (for density and resources)
     * @param label "Start" or "1", "2", …
     * @param isStart true for start/finish checkpoint
     * @param primaryColor ARGB fill for numbered checkpoints
     * @param startColor ARGB fill for start/finish (e.g. tertiary)
     */
    fun create(
        context: android.content.Context,
        label: String,
        isStart: Boolean,
        primaryColor: Int,
        startColor: Int
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (CIRCLE_DP * density).toInt()
        val strokePx = (STROKE_DP * density).coerceAtLeast(2f)
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            if (isStart) START_TEXT_SIZE_SP else TEXT_SIZE_SP,
            context.resources.displayMetrics
        )

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        val radius = center - strokePx

        // Fill circle
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = if (isStart) startColor else primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, fillPaint)

        // Contrasting stroke so badge pops on any map
        val strokePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        canvas.drawCircle(center, center, radius - strokePx / 2f, strokePaint)

        // Centered label
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
        }
        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val textY = center + (bounds.height() / 2f) - bounds.bottom
        canvas.drawText(label, center, textY, textPaint)

        return BitmapDrawable(context.resources, bitmap).apply {
            setBounds(0, 0, sizePx, sizePx)
        }
    }
}
