package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils

/**
 * Soft-focus classic road pins onto nearby OSM objects when spacing allows;
 * otherwise keep the road pin and still attach near-object metadata.
 * Path/spacing fit wins over landmark pickiness (nearest unused POI).
 */
object LandmarkAttachPlanner {

    const val DEFAULT_FOCUS_RADIUS_M = 15.0
    const val DEFAULT_NEAR_RADIUS_M = 220.0

    enum class Mode {
        /** Control relocated onto the POI. */
        FOCUS,
        /** Road pin kept; POI used only for description/metadata. */
        NEAR,
        /** No usable POI. */
        NONE
    }

    data class Candidate(
        val position: GeoPoint,
        val key: String,
        val weight: Double = 1.0
    )

    data class Result(
        val position: GeoPoint,
        val mode: Mode,
        /** Index into the [pool] passed to [attach], or null when [Mode.NONE]. */
        val poolIndex: Int?
    )

    /**
     * @param roadPositions classic road-snapped intermediate controls (not start/finish).
     * @param pool candidate OSM objects (any Street-O tag is fine).
     * @param minSpacingM minimum distance between focused (moved) positions.
     * @param lockedFlags when true at index i, never move that control (near-only).
     */
    fun attach(
        roadPositions: List<GeoPoint>,
        pool: List<Candidate>,
        minSpacingM: Double,
        focusRadiusM: Double = DEFAULT_FOCUS_RADIUS_M,
        nearRadiusM: Double = DEFAULT_NEAR_RADIUS_M,
        lockedFlags: List<Boolean> = emptyList()
    ): List<Result> {
        if (roadPositions.isEmpty()) return emptyList()
        val used = BooleanArray(pool.size)
        val focusedPlaced = mutableListOf<GeoPoint>()
        val results = ArrayList<Result>(roadPositions.size)

        roadPositions.forEachIndexed { index, road ->
            val locked = lockedFlags.getOrElse(index) { false }
            val ranked = pool.indices
                .filter { !used[it] }
                .map { i -> i to GeoUtils.distanceMeters(road, pool[i].position) }
                .sortedWith(
                    compareBy<Pair<Int, Double>> { it.second }
                        .thenByDescending { pool[it.first].weight }
                )

            if (ranked.isEmpty()) {
                results.add(Result(road, Mode.NONE, null))
                return@forEachIndexed
            }

            val (bestIdx, bestDist) = ranked.first()
            val bestPos = pool[bestIdx].position

            val canFocus = !locked &&
                bestDist <= focusRadiusM &&
                !violatesSpacing(bestPos, focusedPlaced, minSpacingM)

            if (canFocus) {
                used[bestIdx] = true
                focusedPlaced.add(bestPos)
                results.add(Result(bestPos, Mode.FOCUS, bestIdx))
                return@forEachIndexed
            }

            // Near: keep road pin; still claim nearest POI within near radius if any.
            val near = ranked.firstOrNull { it.second <= nearRadiusM }
            if (near != null) {
                used[near.first] = true
                results.add(Result(road, Mode.NEAR, near.first))
            } else {
                results.add(Result(road, Mode.NONE, null))
            }
        }
        return results
    }

    /** True when moving [candidate] would violate spacing vs already placed focused points. */
    internal fun violatesSpacing(
        candidate: GeoPoint,
        placedFocused: List<GeoPoint>,
        minSpacingM: Double
    ): Boolean =
        placedFocused.any { GeoUtils.distanceMeters(it, candidate) < minSpacingM }
}
