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
     * @param selected soft outer ring for picker selection
     */
    fun create(
        context: android.content.Context,
        label: String,
        isStart: Boolean,
        primaryColor: Int,
        startColor: Int,
        scale: Float = 1f,
        selected: Boolean = false
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val s = scale.coerceIn(0.25f, 1.5f)
        val padDp = if (selected) 5f else 0f
        val sizePx = ((CIRCLE_DP + padDp * 2f) * density * s).toInt().coerceAtLeast(8)
        val strokePx = (STROKE_DP * density * s).coerceAtLeast(1.5f)
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            (if (isStart) START_TEXT_SIZE_SP else TEXT_SIZE_SP) * s,
            context.resources.displayMetrics
        )

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        val padPx = padDp * density * s
        val radius = center - strokePx - padPx

        if (selected) {
            val selectRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (2.5f * density * s).coerceAtLeast(2f)
                color = Color.WHITE
            }
            val selectHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (4.5f * density * s).coerceAtLeast(3f)
                color = Color.argb(90, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
            }
            canvas.drawCircle(center, center, radius + padPx * 0.55f, selectHalo)
            canvas.drawCircle(center, center, radius + padPx * 0.35f, selectRing)
        }

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
