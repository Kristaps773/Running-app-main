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

/** Small pill badge for midpoint air-leg distance on the map. */
object AirLegLabelIcon {

    fun create(context: android.content.Context, label: String): Drawable {
        val density = context.resources.displayMetrics.density
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            context.resources.displayMetrics
        )
        val padH = (6 * density).toInt()
        val padV = (3 * density).toInt()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val width = bounds.width() + padH * 2
        val height = bounds.height() + padV * 2

        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 20, 28, 40)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            6 * density,
            6 * density,
            bgPaint
        )

        val textY = padV + bounds.height()
        canvas.drawText(label, padH.toFloat(), textY.toFloat(), textPaint)

        return BitmapDrawable(context.resources, bitmap).apply {
            setBounds(0, 0, width, height)
        }
    }
}
