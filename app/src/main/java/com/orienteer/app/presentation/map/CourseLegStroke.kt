package com.orienteer.app.presentation.map

import android.graphics.Path
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.osmdroid.views.Projection
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/**
 * Filled stroke outline for a course leg piece. Optional beveled ends have a face
 * **parallel to the cutting line** (O-map crossing gap), not a 90° butt across the leg.
 *
 * Cut directions are **screen-space** unit vectors so map rotation / projection stay correct.
 */
object CourseLegStroke {

    fun beveledOutline(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        halfWidth: Float,
        startCutDir: Pair<Float, Float>? = null,
        endCutDir: Pair<Float, Float>? = null
    ): Path {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val path = Path()
        if (len < 0.5f || halfWidth <= 0f) return path

        val ux = dx / len
        val uy = dy / len
        val px = -uy * halfWidth
        val py = ux * halfWidth
        // Allow long miters for shallow cuts (was ±4px and fell back to 90° butts).
        val maxAlong = (halfWidth * 48f).coerceAtLeast(24f)

        val startL = corner(x0, y0, ux, uy, px, py, startCutDir, maxAlong)
        val startR = corner(x0, y0, ux, uy, -px, -py, startCutDir, maxAlong)
        val endL = corner(x1, y1, ux, uy, px, py, endCutDir, maxAlong)
        val endR = corner(x1, y1, ux, uy, -px, -py, endCutDir, maxAlong)

        path.moveTo(startL.first, startL.second)
        path.lineTo(endL.first, endL.second)
        path.lineTo(endR.first, endR.second)
        path.lineTo(startR.first, startR.second)
        path.close()
        return path
    }

    /**
     * Geographic bearing (0=N) → screen unit direction using the live map projection
     * (handles rotation and Web Mercator).
     */
    fun screenDirFromBearing(
        projection: Projection,
        at: GeoPoint,
        bearingDeg: Double
    ): Pair<Float, Float> {
        val a = GeoUtils.offsetPoint(at, 0.0, bearingDeg)
        val b = GeoUtils.offsetPoint(at, 80.0, bearingDeg)
        val pa = android.graphics.Point()
        val pb = android.graphics.Point()
        projection.toPixels(OsmGeoPoint(a.latitude, a.longitude), pa)
        projection.toPixels(OsmGeoPoint(b.latitude, b.longitude), pb)
        val sx = (pb.x - pa.x).toFloat()
        val sy = (pb.y - pa.y).toFloat()
        val sl = hypot(sx.toDouble(), sy.toDouble()).toFloat()
        if (sl < 1e-3f) {
            // Fallback: unrotated map, 0° = -Y.
            val rad = Math.toRadians(bearingDeg)
            return sin(rad).toFloat() to (-cos(rad)).toFloat()
        }
        return sx / sl to sy / sl
    }

    /** Same idea for PDF / off-map projectors: sample two points along the bearing. */
    fun screenDirFromBearing(
        at: GeoPoint,
        bearingDeg: Double,
        project: (GeoPoint) -> Pair<Float, Float>?
    ): Pair<Float, Float>? {
        val a = GeoUtils.offsetPoint(at, 0.0, bearingDeg)
        val b = GeoUtils.offsetPoint(at, 80.0, bearingDeg)
        val pa = project(a) ?: return null
        val pb = project(b) ?: return null
        val sx = pb.first - pa.first
        val sy = pb.second - pa.second
        val sl = hypot(sx.toDouble(), sy.toDouble()).toFloat()
        if (sl < 1e-3f) return null
        return sx / sl to sy / sl
    }

    /**
     * Corner where the long edge (through endpoint±perp, along the leg) meets the end face.
     * End face is either perpendicular to the leg (butt) or parallel to [cutDir].
     */
    private fun corner(
        x: Float,
        y: Float,
        ux: Float,
        uy: Float,
        ox: Float,
        oy: Float,
        cutDir: Pair<Float, Float>?,
        maxAlong: Float
    ): Pair<Float, Float> {
        val edgeX = x + ox
        val edgeY = y + oy
        if (cutDir == null) {
            return edgeX to edgeY
        }
        val cx = cutDir.first
        val cy = cutDir.second
        val cross = ux * cy - uy * cx
        // Nearly parallel to the leg → face undefined; keep butt.
        if (kotlin.math.abs(cross) < 0.04f) {
            return edgeX to edgeY
        }
        return lineIntersect(edgeX, edgeY, ux, uy, x, y, cx, cy, maxAlong)
            ?: (edgeX to edgeY)
    }

    private fun lineIntersect(
        p0x: Float,
        p0y: Float,
        d0x: Float,
        d0y: Float,
        p1x: Float,
        p1y: Float,
        d1x: Float,
        d1y: Float,
        maxAlong: Float
    ): Pair<Float, Float>? {
        val det = d0x * d1y - d0y * d1x
        if (kotlin.math.abs(det) < 1e-5f) return null
        val t = ((p1x - p0x) * d1y - (p1y - p0y) * d1x) / det
        if (t < -maxAlong || t > maxAlong) return null
        return p0x + t * d0x to p0y + t * d0y
    }
}
