package com.orienteer.app.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Flat modern location puck — solid forest disc, white ring, quiet accuracy ring.
 * No gloss / gradients (avoids early-2000s “shiny button” look).
 */
object YouAreHereMarkerIcon {

    private const val CACHE_VERSION = 4
    private const val SIZE_DP = 34f
    private const val SUPERSAMPLE = 3
    private const val FOREST = 0xFF1B5E20.toInt()

    @Volatile
    private var cache: Triple<Int, Float, Drawable>? = null

    fun create(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        cache?.let { (ver, d, icon) ->
            if (ver == CACHE_VERSION && d == density) return icon
        }

        val displayPx = (SIZE_DP * density).toInt().coerceAtLeast(26)
        val hi = displayPx * SUPERSAMPLE
        val raw = Bitmap.createBitmap(hi, hi, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(raw)
        val c = hi / 2f
        val s = SUPERSAMPLE.toFloat()

        val accuracyR = c * 0.96f
        val puckR = c * 0.48f
        val coreR = puckR * 0.70f
        val whiteStroke = (2.6f * density * s).coerceAtLeast(3.5f)
        val accuracyStroke = (1.4f * density * s).coerceAtLeast(2f)

        // Quiet accuracy ring (stroke only — no muddy fill)
        val accuracy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = accuracyStroke
            color = Color.argb(70, 0x1B, 0x5E, 0x20)
        }
        canvas.drawCircle(c, c, accuracyR - accuracyStroke * 0.5f, accuracy)

        // Soft drop for lift (single soft disc, not a plastic bevel)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(40, 15, 26, 19)
        }
        canvas.drawCircle(c, c + 1.4f * density * s, puckR * 0.92f, shadow)

        // White collar
        val collar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(c, c, puckR, collar)

        // Flat forest core
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = FOREST
        }
        canvas.drawCircle(c, c, coreR, core)

        // Hairline between white and forest for crispness on bright tiles
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = whiteStroke * 0.18f
            color = Color.argb(28, 15, 26, 19)
        }
        canvas.drawCircle(c, c, coreR + whiteStroke * 0.04f, seam)

        val scaled = Bitmap.createScaledBitmap(raw, displayPx, displayPx, true)
        if (scaled !== raw) raw.recycle()

        val drawable = BitmapDrawable(context.resources, scaled).apply {
            setBounds(0, 0, displayPx, displayPx)
        }
        cache = Triple(CACHE_VERSION, density, drawable)
        return drawable
    }
}
