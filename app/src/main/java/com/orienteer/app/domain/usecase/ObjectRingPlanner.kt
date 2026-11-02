package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure geometry helpers for Street-O object-ring seeding and POI assignment.
 * Ring size comes from target air; controls lock onto discrete map objects.
 */
object ObjectRingPlanner {

    /** Ideal loop radius for air perimeter ≈ [targetAirM], shrunk by expected path/air. */
    fun seedRadiusM(targetAirM: Double, maxPathToAirRatio: Double): Double {
        val r0 = targetAirM / (2.0 * PI)
        val expectedPathAir = (1.2).coerceIn(1.15, min(1.35, maxPathToAirRatio * 0.85))
        return (r0 / expectedPathAir).coerceAtLeast(80.0)
    }

    /** Evenly spaced slots on a circle around [origin]. */
    fun ringSlots(origin: GeoPoint, radiusM: Double, count: Int, bearing0Deg: Double): List<GeoPoint> {
        if (count <= 0) return emptyList()
        val step = 360.0 / count
        return List(count) { i ->
            GeoUtils.offsetPoint(origin, radiusM, (bearing0Deg + i * step + 360.0) % 360.0)
        }
    }

    data class RankedPoi(
        val position: GeoPoint,
        val weight: Double,
        val key: String
    )

    /**
     * Assign one unused POI per ring slot (nearest in sector, then by weight).
     * Returns null if any slot cannot be filled.
     */
    fun assignObjectsToRing(
        origin: GeoPoint,
        slots: List<GeoPoint>,
        pool: List<RankedPoi>,
        minSpacingM: Double,
        radialBandFraction: Double = 0.35
    ): List<RankedPoi>? {
        if (slots.isEmpty()) return emptyList()
        if (pool.size < slots.size) return null
        val remaining = pool.toMutableList()
        val chosen = mutableListOf<RankedPoi>()
        val sectorHalf = 180.0 / slots.size

        for (slot in slots) {
            val slotBearing = GeoUtils.bearingDegrees(origin, slot)
            val slotR = GeoUtils.distanceMeters(origin, slot)
            val band = max(slotR * radialBandFraction, 80.0)

            fun angularOk(p: RankedPoi): Boolean {
                val b = GeoUtils.bearingDegrees(origin, p.position)
                return angularDistanceDeg(slotBearing, b) <= sectorHalf * 1.25
            }

            var candidates = remaining.filter { poi ->
                angularOk(poi) &&
                    abs(GeoUtils.distanceMeters(origin, poi.position) - slotR) <= band &&
                    chosen.none { GeoUtils.distanceMeters(it.position, poi.position) < minSpacingM }
            }
            if (candidates.isEmpty()) {
                // Widen: same sector, ignore radial band.
                candidates = remaining.filter { poi ->
                    angularOk(poi) &&
                        chosen.none { GeoUtils.distanceMeters(it.position, poi.position) < minSpacingM }
                }
            }
            if (candidates.isEmpty()) {
                // Last resort: nearest unused with spacing.
                candidates = remaining.filter { poi ->
                    chosen.none { GeoUtils.distanceMeters(it.position, poi.position) < minSpacingM }
                }
            }
            val best = candidates.minWithOrNull(
                compareBy<RankedPoi> { GeoUtils.distanceMeters(slot, it.position) }
                    .thenByDescending { it.weight }
            ) ?: return null
            chosen += best
            remaining.removeAll { it.key == best.key }
        }
        return chosen
    }

    /**
     * Replace the control that most hurts air-over-max / path projection with a nearby unused POI.
     */
    fun swapWorstControl(
        origin: GeoPoint,
        current: List<RankedPoi>,
        unused: List<RankedPoi>,
        targetAirM: Double,
        airMaxM: Double,
        minSpacingM: Double,
        rng: Random
    ): List<RankedPoi>? {
        if (current.isEmpty() || unused.isEmpty()) return null
        val airNow = loopAirM(origin, current.map { it.position })
        val positions = current.map { it.position }
        val worstIdx = current.indices.maxByOrNull { idx ->
            // Prefer swapping the point farthest from ideal ring, with dogleg / over-max bias.
            val distFromIdeal = abs(
                GeoUtils.distanceMeters(origin, current[idx].position) - (targetAirM / (2.0 * PI))
            )
            distFromIdeal +
                doglegPenaltyDeg(origin, positions, idx) * 2.0 +
                if (airNow > airMaxM) 500.0 else 0.0
        } ?: return null

        val slot = current[worstIdx].position
        val others = current.filterIndexed { i, _ -> i != worstIdx }
        val replacements = unused.filter { poi ->
            others.none { GeoUtils.distanceMeters(it.position, poi.position) < minSpacingM }
        }.sortedBy { GeoUtils.distanceMeters(slot, it.position) }
            .take(8)
        if (replacements.isEmpty()) return null

        val pick = replacements[rng.nextInt(replacements.size.coerceAtMost(3).coerceAtLeast(1))]
        return current.toMutableList().also { it[worstIdx] = pick }
    }

    /** Shrink when over air max or path/air; grow only if far short and path has headroom. */
    fun adjustRadiusM(
        currentR: Double,
        airM: Double,
        pathM: Double,
        targetAirM: Double,
        airMaxM: Double,
        maxPathToAir: Double
    ): Double {
        val pathAir = pathM / airM.coerceAtLeast(1.0)
        var scale = 1.0
        if (airM > airMaxM) {
            scale *= (targetAirM / airM).coerceIn(0.75, 0.98)
        } else if (airM < targetAirM * 0.75 && pathAir < maxPathToAir * 0.9) {
            scale *= (targetAirM / airM.coerceAtLeast(1.0)).coerceIn(1.02, 1.2)
        }
        if (pathAir > maxPathToAir) {
            scale *= (maxPathToAir / pathAir).coerceIn(0.75, 0.95)
        }
        return (currentR * scale).coerceIn(80.0, 8_000.0)
    }

    fun loopAirM(origin: GeoPoint, controls: List<GeoPoint>): Double {
        if (controls.isEmpty()) return 0.0
        var sum = 0.0
        var prev = origin
        for (p in controls) {
            sum += GeoUtils.distanceMeters(prev, p)
            prev = p
        }
        sum += GeoUtils.distanceMeters(prev, origin)
        return sum
    }

    /** Soft error: 0 at target, grows if shorter; large if over [airMaxM]. */
    fun airSoftErrorM(airM: Double, targetAirM: Double, airMaxM: Double): Double =
        if (airM > airMaxM) (airM - airMaxM) + 10_000.0
        else (targetAirM - airM).coerceAtLeast(0.0)

    fun angularDistanceDeg(a: Double, b: Double): Double {
        val d = abs(((a - b + 540.0) % 360.0) - 180.0)
        return d
    }

    /**
     * Higher when inbound and outbound bearings at [idx] nearly reverse (classic dogleg).
     */
    fun doglegPenaltyDeg(origin: GeoPoint, controls: List<GeoPoint>, idx: Int): Double {
        if (controls.isEmpty() || idx !in controls.indices) return 0.0
        val prev = if (idx == 0) origin else controls[idx - 1]
        val cur = controls[idx]
        val next = if (idx == controls.lastIndex) origin else controls[idx + 1]
        val inbound = GeoUtils.bearingDegrees(prev, cur)
        val outbound = GeoUtils.bearingDegrees(cur, next)
        val turn = angularDistanceDeg(inbound, outbound)
        // 180° turn ≈ dogleg; score up to ~180.
        return (180.0 - turn).coerceAtLeast(0.0)
    }

    /** Overpass search radius covering the ring with margin. */
    fun searchRadiusM(targetAirM: Double): Int {
        val r = seedRadiusM(targetAirM, maxPathToAirRatio = 1.8) * 1.45
        return r.roundToIntSafe().coerceIn(400, 4_000)
    }

    private fun Double.roundToIntSafe(): Int =
        if (this.isNaN() || this.isInfinite()) 400 else toInt()
}
