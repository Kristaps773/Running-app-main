package com.orienteer.app.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Modern “you are here” location puck (Apple Maps / Strava style).
 * Soft accuracy wash + lifted white ring + glass-forest core.
 * Checkpoints use IOF purple — forest keeps this aligned with brand CTAs.
 */
object YouAreHereMarkerIcon {

    /** On-screen size of the full glyph (halo included). */
    private const val SIZE_DP = 40f
    private const val SUPERSAMPLE = 3

    /** Alpine Trail forest primary + lighter lid highlight. */
    private const val FOREST = 0xFF1B5E20.toInt()
    private const val FOREST_LIGHT = 0xFF43A047.toInt()
    private const val FOREST_DEEP = 0xFF144618.toInt()
    private const val PINE = 0xFF0F1A13.toInt()

    @Volatile
    private var cache: Pair<Float, Drawable>? = null

    fun create(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        cache?.let { (d, icon) -> if (d == density) return icon }

        val displayPx = (SIZE_DP * density).toInt().coerceAtLeast(28)
        val hi = displayPx * SUPERSAMPLE
        val raw = Bitmap.createBitmap(hi, hi, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(raw)
        val c = hi / 2f
        val s = SUPERSAMPLE.toFloat()

        val haloR = c * 0.96f
        val puckR = c * 0.42f
        val coreR = puckR * 0.72f
        val ringW = (2.75f * density * s).coerceAtLeast(4f)

        // Soft accuracy wash
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                c, c, haloR,
                intArrayOf(
                    Color.argb(55, 0x1B, 0x5E, 0x20),
                    Color.argb(22, 0x1B, 0x5E, 0x20),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(c, c, haloR, wash)

        // Contact shadow
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                c, c + 1.2f * density * s, puckR * 1.15f,
                intArrayOf(
                    Color.argb(55, Color.red(PINE), Color.green(PINE), Color.blue(PINE)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(c, c + 1.2f * density * s, puckR * 1.05f, shadow)

        // White ring disc
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(c, c, puckR, white)

        // Glass forest core
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                c - coreR * 0.28f,
                c - coreR * 0.32f,
                coreR * 1.35f,
                intArrayOf(FOREST_LIGHT, FOREST, FOREST_DEEP),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(c, c, coreR, core)

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringW * 0.22f
            color = Color.argb(40, 15, 26, 19)
        }
        canvas.drawCircle(c, c, coreR + ringW * 0.05f, rim)

        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(160, 255, 255, 255)
        }
        canvas.drawCircle(
            c - coreR * 0.28f,
            c - coreR * 0.32f,
            coreR * 0.18f,
            glint
        )

        val scaled = Bitmap.createScaledBitmap(raw, displayPx, displayPx, true)
        if (scaled !== raw) raw.recycle()

        val drawable = BitmapDrawable(context.resources, scaled).apply {
            setBounds(0, 0, displayPx, displayPx)
        }
        cache = density to drawable
        return drawable
    }
}
