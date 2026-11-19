package com.orienteer.app.presentation.map

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * O-map / Purple Pen style: later air legs break where they cross earlier ones,
 * and any leg breaks where it would run through a control / start / finish symbol.
 * Gap faces follow the cutting geometry (earlier leg bearing, or circle tangent).
 */
object AirLegCrossingGaps {

    /**
     * Half-width of the clear strip beside a continuous cutting leg (metres).
     * Sized so gaps stay visible when zoomed out (stroke width would otherwise bridge them).
     */
    const val GAP_HALF_M = 14.0

    /**
     * Extra clearance past a control / start / finish perimeter (metres).
     * Same order as [GAP_HALF_M] so mid-leg symbol gaps match crossing gaps on screen.
     */
    const val SYMBOL_PAD_M = 14.0

    private const val END_MARGIN_FRAC = 0.08
    private const val MIN_ALONG_HALF_M = 8.0
    private const val MAX_ALONG_HALF_M = 48.0

    /**
     * One drawable piece of a leg. Optional cut bearings (degrees, 0=N) mean that end
     * should be beveled parallel to the line that created the gap.
     */
    data class BeveledPiece(
        val from: GeoPoint,
        val to: GeoPoint,
        val startCutBearingDeg: Double? = null,
        val endCutBearingDeg: Double? = null
    )

    /**
     * Symbol footprint that legs must not draw through.
     * @param triangleFacingDeg when set, treat as equilateral start triangle (apex toward this bearing).
     */
    data class SymbolObstacle(
        val center: GeoPoint,
        val radiusM: Double,
        val triangleFacingDeg: Double? = null
    )

    /** Flattened pieces for drawing (earlier legs whole; later legs split + beveled). */
    fun gappedPieces(
        legs: List<Pair<GeoPoint, GeoPoint>>,
        gapHalfM: Double = GAP_HALF_M,
        symbols: List<SymbolObstacle> = emptyList()
    ): List<BeveledPiece> {
        if (legs.isEmpty()) return emptyList()
        val out = ArrayList<BeveledPiece>(legs.size)
        for (i in legs.indices) {
            val (from, to) = legs[i]
            val gaps = ArrayList<GapSpec>()
            val legLen = GeoUtils.distanceMeters(from, to)
            if (legLen < 1.0) continue
            val brokenBearing = GeoUtils.bearingDegrees(from, to)
            for (j in 0 until i) {
                val (oFrom, oTo) = legs[j]
                if (shareEndpoint(from, to, oFrom, oTo)) continue
                val hit = segmentIntersection(from, to, oFrom, oTo) ?: continue
                val along = GeoUtils.distanceMeters(from, hit)
                val margin = legLen * END_MARGIN_FRAC
                if (along < margin || along > legLen - margin) continue
                val cutBearing = GeoUtils.bearingDegrees(oFrom, oTo)
                val alongHalf = alongHalfForCrossing(brokenBearing, cutBearing, gapHalfM)
                gaps.add(GapSpec(along, alongHalf, cutBearing, cutBearing))
            }
            for (symbol in symbols) {
                gaps.addAll(symbolGapsOnLeg(from, to, legLen, brokenBearing, symbol))
            }
            out.addAll(splitLegAroundGaps(from, to, gaps))
        }
        return out
    }

    /** Back-compat: plain segments without bevel metadata. */
    fun gappedSegments(
        legs: List<Pair<GeoPoint, GeoPoint>>,
        gapHalfM: Double = GAP_HALF_M,
        symbols: List<SymbolObstacle> = emptyList()
    ): List<Pair<GeoPoint, GeoPoint>> =
        gappedPieces(legs, gapHalfM, symbols).map { it.from to it.to }

    /**
     * Along-leg half-gap so the clear strip width perpendicular to the cutting line is [gapHalfM].
     */
    fun alongHalfForCrossing(
        brokenBearingDeg: Double,
        cuttingBearingDeg: Double,
        gapHalfM: Double = GAP_HALF_M
    ): Double {
        val phi = acuteAngleDeg(brokenBearingDeg, cuttingBearingDeg)
        val sinPhi = sin(Math.toRadians(phi)).coerceAtLeast(0.18)
        return (gapHalfM / sinPhi).coerceIn(MIN_ALONG_HALF_M, MAX_ALONG_HALF_M)
    }

    /**
     * Gap(s) where [leg] would pass through [symbol]. Skips symbols that are this leg's
     * own endpoints (already trimmed). Cut faces follow the cutting edge (triangle side
     * or circle tangent), not a 90° chop across the broken leg.
     */
    fun symbolGapsOnLeg(
        from: GeoPoint,
        to: GeoPoint,
        legLen: Double = GeoUtils.distanceMeters(from, to),
        legBearingDeg: Double = GeoUtils.bearingDegrees(from, to),
        symbol: SymbolObstacle
    ): List<GapSpec> {
        val r = symbol.radiusM + SYMBOL_PAD_M
        if (r <= 0.0 || legLen < 1.0) return emptyList()
        // Own endpoint symbols sit ~radius behind the shortened ends — skip those.
        if (GeoUtils.distanceMeters(from, symbol.center) <= r + 3.0) return emptyList()
        if (GeoUtils.distanceMeters(to, symbol.center) <= r + 3.0) return emptyList()

        if (symbol.triangleFacingDeg != null) {
            return triangleGapsOnLeg(from, to, legLen, symbol)
        }
        return circleGapsOnLeg(from, to, legLen, legBearingDeg, symbol, r)
    }

    private fun circleGapsOnLeg(
        from: GeoPoint,
        to: GeoPoint,
        legLen: Double,
        legBearingDeg: Double,
        symbol: SymbolObstacle,
        r: Double
    ): List<GapSpec> {
        val approach = closestApproach(from, to, legLen, symbol.center) ?: return emptyList()
        if (approach.perpDistM >= r) return emptyList()

        val halfChord = sqrt(r * r - approach.perpDistM * approach.perpDistM)
        val alongHalf = halfChord.coerceIn(MIN_ALONG_HALF_M, max(r, MAX_ALONG_HALF_M))
        val margin = legLen * END_MARGIN_FRAC
        if (approach.alongM < margin || approach.alongM > legLen - margin) return emptyList()

        val entry = GeoUtils.offsetPoint(
            from,
            (approach.alongM - alongHalf).coerceAtLeast(0.0),
            legBearingDeg
        )
        val exit = GeoUtils.offsetPoint(
            from,
            (approach.alongM + alongHalf).coerceAtMost(legLen),
            legBearingDeg
        )
        // Tangent at rim (parallel to circle edge at the cut).
        val cutEntry = (GeoUtils.bearingDegrees(symbol.center, entry) + 90.0) % 360.0
        val cutExit = (GeoUtils.bearingDegrees(symbol.center, exit) + 90.0) % 360.0
        return listOf(GapSpec(approach.alongM, alongHalf, cutEntry, cutExit))
    }

    /** Break where the leg crosses triangle sides; faces follow those sides. */
    private fun triangleGapsOnLeg(
        from: GeoPoint,
        to: GeoPoint,
        legLen: Double,
        symbol: SymbolObstacle
    ): List<GapSpec> {
        val facing = symbol.triangleFacingDeg ?: return emptyList()
        val r = symbol.radiusM
        val v0 = GeoUtils.offsetPoint(symbol.center, r, facing)
        val v1 = GeoUtils.offsetPoint(symbol.center, r, facing + 120.0)
        val v2 = GeoUtils.offsetPoint(symbol.center, r, facing - 120.0)
        data class Hit(val alongM: Double, val edgeBearingDeg: Double)
        val hits = ArrayList<Hit>(3)
        listOf(v0 to v1, v1 to v2, v2 to v0).forEach { (a, b) ->
            val hit = segmentIntersection(from, to, a, b) ?: return@forEach
            hits.add(
                Hit(
                    alongM = GeoUtils.distanceMeters(from, hit),
                    edgeBearingDeg = GeoUtils.bearingDegrees(a, b)
                )
            )
        }
        if (hits.size < 2) {
            // Grazing / near-miss: fall back to circumcircle clearance.
            return circleGapsOnLeg(
                from, to, legLen,
                GeoUtils.bearingDegrees(from, to),
                symbol,
                r + SYMBOL_PAD_M
            )
        }
        hits.sortBy { it.alongM }
        val first = hits.first()
        val last = hits.last()
        val margin = legLen * END_MARGIN_FRAC
        val mid = (first.alongM + last.alongM) * 0.5
        if (mid < margin || mid > legLen - margin) return emptyList()
        val alongHalf = ((last.alongM - first.alongM) * 0.5 + SYMBOL_PAD_M)
            .coerceIn(MIN_ALONG_HALF_M, max(r, MAX_ALONG_HALF_M))
        return listOf(GapSpec(mid, alongHalf, first.edgeBearingDeg, last.edgeBearingDeg))
    }

    private fun acuteAngleDeg(a: Double, b: Double): Double {
        var d = abs(a - b) % 180.0
        if (d > 90.0) d = 180.0 - d
        return d.coerceAtLeast(1.0)
    }

    data class GapSpec(
        val alongM: Double,
        val alongHalfM: Double,
        val cutAtStart: Double,
        val cutAtEnd: Double
    )

    private data class Approach(
        val alongM: Double,
        val perpDistM: Double
    )

    private fun closestApproach(
        from: GeoPoint,
        to: GeoPoint,
        legLen: Double,
        point: GeoPoint
    ): Approach? {
        val origin = from
        val cosLat = cos(Math.toRadians(origin.latitude)).coerceAtLeast(1e-6)
        fun toXy(p: GeoPoint): Pair<Double, Double> {
            val x = (p.longitude - origin.longitude) * 111_320.0 * cosLat
            val y = (p.latitude - origin.latitude) * 110_540.0
            return x to y
        }
        val (ax, ay) = toXy(from)
        val (bx, by) = toXy(to)
        val (px, py) = toXy(point)
        val abx = bx - ax
        val aby = by - ay
        val abLen2 = abx * abx + aby * aby
        if (abLen2 < 1e-6) return null
        val t = ((px - ax) * abx + (py - ay) * aby) / abLen2
        if (t <= 0.02 || t >= 0.98) return null
        val cx = ax + t * abx
        val cy = ay + t * aby
        val perp = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
        return Approach(alongM = t * legLen, perpDistM = perp)
    }

    private fun splitLegAroundGaps(
        from: GeoPoint,
        to: GeoPoint,
        gaps: List<GapSpec>
    ): List<BeveledPiece> {
        val legLen = GeoUtils.distanceMeters(from, to)
        if (legLen < 1.0) return emptyList()
        if (gaps.isEmpty()) return listOf(BeveledPiece(from, to))

        val bearing = GeoUtils.bearingDegrees(from, to)
        data class Interval(
            val start: Double,
            val end: Double,
            val cutAtStart: Double,
            val cutAtEnd: Double
        )
        val intervals = gaps.map {
            Interval(
                start = (it.alongM - it.alongHalfM).coerceAtLeast(0.0),
                end = (it.alongM + it.alongHalfM).coerceAtMost(legLen),
                cutAtStart = it.cutAtStart,
                cutAtEnd = it.cutAtEnd
            )
        }.sortedBy { it.start }

        val merged = ArrayList<Interval>()
        for (g in intervals) {
            if (merged.isEmpty() || g.start > merged.last().end) {
                merged.add(g)
            } else {
                val last = merged.removeAt(merged.lastIndex)
                merged.add(
                    Interval(
                        start = last.start,
                        end = max(last.end, g.end),
                        cutAtStart = last.cutAtStart,
                        cutAtEnd = g.cutAtEnd
                    )
                )
            }
        }

        val pieces = ArrayList<BeveledPiece>()
        var cursor = 0.0
        var cursorStartCut: Double? = null
        for (g in merged) {
            if (g.start - cursor >= 2.0) {
                pieces.add(
                    BeveledPiece(
                        from = GeoUtils.offsetPoint(from, cursor, bearing),
                        to = GeoUtils.offsetPoint(from, g.start, bearing),
                        startCutBearingDeg = cursorStartCut,
                        endCutBearingDeg = g.cutAtStart
                    )
                )
            }
            cursor = g.end
            cursorStartCut = g.cutAtEnd
        }
        if (legLen - cursor >= 2.0) {
            pieces.add(
                BeveledPiece(
                    from = GeoUtils.offsetPoint(from, cursor, bearing),
                    to = to,
                    startCutBearingDeg = cursorStartCut,
                    endCutBearingDeg = null
                )
            )
        }
        return pieces
    }

    private fun shareEndpoint(
        a1: GeoPoint,
        a2: GeoPoint,
        b1: GeoPoint,
        b2: GeoPoint
    ): Boolean {
        val epsM = 1.5
        return near(a1, b1, epsM) || near(a1, b2, epsM) ||
            near(a2, b1, epsM) || near(a2, b2, epsM)
    }

    private fun near(a: GeoPoint, b: GeoPoint, epsM: Double): Boolean =
        GeoUtils.distanceMeters(a, b) <= epsM

    fun segmentIntersection(
        a1: GeoPoint,
        a2: GeoPoint,
        b1: GeoPoint,
        b2: GeoPoint
    ): GeoPoint? {
        val origin = a1
        val cosLat = cos(Math.toRadians(origin.latitude)).coerceAtLeast(1e-6)
        fun toXy(p: GeoPoint): Pair<Double, Double> {
            val x = (p.longitude - origin.longitude) * 111_320.0 * cosLat
            val y = (p.latitude - origin.latitude) * 110_540.0
            return x to y
        }
        val (ax, ay) = toXy(a1)
        val (bx, by) = toXy(a2)
        val (cx, cy) = toXy(b1)
        val (dx, dy) = toXy(b2)

        val rX = bx - ax
        val rY = by - ay
        val sX = dx - cx
        val sY = dy - cy
        val denom = rX * sY - rY * sX
        if (abs(denom) < 1e-9) return null

        val t = ((cx - ax) * sY - (cy - ay) * sX) / denom
        val u = ((cx - ax) * rY - (cy - ay) * rX) / denom
        if (t <= 0.02 || t >= 0.98 || u <= 0.02 || u >= 0.98) return null

        return GeoPoint(
            a1.latitude + t * (a2.latitude - a1.latitude),
            a1.longitude + t * (a2.longitude - a1.longitude)
        )
    }
}
