package com.orienteer.app.domain.usecase

import android.util.Log
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.OverpassApiService
import com.orienteer.app.data.local.RouteGenerationPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import com.orienteer.app.data.model.OsrmRouteResponse
import com.orienteer.app.data.model.Route
import com.orienteer.app.util.CheckpointDescriptionGenerator
import com.orienteer.app.util.GeoUtils
import com.orienteer.app.util.PolylineSimplifier
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Generates a randomised loop running route using straight-line (Haversine) distances only.
 *
 * Algorithm:
 * 1. Random checkpoint count n (e.g. 3–8). Compute n+1 random segment lengths
 *    (start→1, 1→2, …, n→start) that sum to targetDistanceM.
 * 2. From the previous point, pick the next segment's target straight-line distance,
 *    place a point on that circle (random bearing), snap to nearest road via OSRM /nearest.
 * 3. Repeat for all n segments. Last leg is back to start; use Haversine(prev, origin).
 * 4. All distances (route total, segment labels, UI, PDF) are straight-line only.
 *    Actual path may be longer.
 * 5. Call OSRM /route for polyline; estimated time uses OSRM duration (actual path) when available, else fallback.
 */
class GenerateRouteUseCase @Inject constructor(
    private val osrm: OsrmApiService,
    private val overpass: OverpassApiService
) {

    companion object {
        private const val TAG = "GenerateRouteUseCase"

        /**
         * When false, skips one Overpass query per checkpoint after the route is built (major speed win).
         * Classic routes keep generic checkpoint text; object-based routes still use OSM tags from the candidate pool.
         */
        private const val OVERPASS_ENRICH_CHECKPOINTS = false

        private const val SEGMENT_MIN_M = 200.0
        private const val SEGMENT_MAX_M = 2_000.0
        private const val MIN_CHECKPOINTS = 2
        /** Cap for public OSRM — too many waypoints → slow/fail /route. */
        private const val MAX_CHECKPOINTS = 10
        private const val SNAP_MAX_DISTANCE_M = 600.0
        private const val SNAP_RETRIES = 4
        private const val CANDIDATE_MIN_SPACING_M = 120.0
        private const val FAST_ACCEPT_TOLERANCE_M = 500.0
        /** After this fraction of air budget is spent, stop expanding away from start. */
        private const val BUDGET_CLOSE_LOOP_FRACTION = 0.5
        /** Allow slight outward growth once closing phase started. */
        private const val MAX_RADIUS_GROWTH_AFTER_HALF = 1.05
        private const val MAX_ITERATIONS = 6
        /** Full generation passes with a new random layout when an attempt fails (ratio, unreachable, etc.). */
        private const val MAX_GENERATION_ATTEMPTS = 3
        /** Per-attempt wall-clock limit so UI cannot hang forever on public OSRM. */
        private const val ATTEMPT_TIMEOUT_MS = 55_000L
        private const val OSRM_RETRY_BACKOFF_MS = 400L
        private const val SHORT_ROUTE_M = 3_000.0
        private const val SNAP_PARALLELISM = 4
        private const val POLYLINE_SIMPLIFY_TOLERANCE_M = 8.0
        private const val RUNNING_SPEED_KMH = 10.0      // fallback est. time when OSRM route unavailable (km/h)
        private val TAG_WEIGHTS = mapOf(
            "historic=monument" to 1.6,
            "historic=memorial" to 1.5,
            "historic=statue" to 1.55,
            "historic=wayside_cross" to 1.45,
            "tourism=viewpoint" to 1.5,
            "tourism=artwork" to 1.4,
            "tourism=information" to 1.35,
            "amenity=fountain" to 1.3,
            "natural=peak" to 1.4,
            "natural=rock" to 1.35,
            "man_made=tower" to 1.45,
            "leisure=playground" to 1.2,
            "barrier=bollard" to 1.15,
            "highway=bus_stop" to 1.1
        )

        private val OVERPASS_POI_KEYS = listOf(
            "amenity", "historic", "tourism", "natural", "leisure",
            "man_made", "barrier", "shop", "craft", "sport"
        )
    }

    /**
     * @param origin          Runner's starting GPS position.
     * @param targetDistanceM Desired total straight-line route distance in metres.
     * @return                A [Result] wrapping the generated [Route]. All distances are straight-line.
     */
    suspend operator fun invoke(
        origin: GeoPoint,
        targetDistanceM: Double,
        generationPrefs: RouteGenerationPreferences = RouteGenerationPreferences(),
        onRouteUpdate: (suspend (Route) -> Unit)? = null
    ): Result<Route> {
        val maxPathToAirRatio = generationPrefs.maxPathToAirRatio.coerceIn(1.1f, 2.5f).toDouble()
        val airDistanceToleranceM = generationPrefs.airDistanceToleranceM
            .coerceIn(
                UserPreferencesStore.MIN_AIR_TOLERANCE_M.toFloat(),
                UserPreferencesStore.MAX_AIR_TOLERANCE_M.toFloat()
            )
            .toDouble()
        var lastFailure: Throwable? = null
        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            val result = try {
                Result.success(
                    withTimeout(ATTEMPT_TIMEOUT_MS) {
                        generateRouteAttempt(
                            origin = origin,
                            targetDistanceM = targetDistanceM,
                            maxPathToAirRatio = maxPathToAirRatio,
                            airDistanceToleranceM = airDistanceToleranceM,
                            attemptIndex = attempt,
                            onRouteUpdate = onRouteUpdate
                        )
                    }
                )
            } catch (e: TimeoutCancellationException) {
                Result.failure(
                    IllegalStateException(
                        "Route generation timed out. Try again or choose a shorter distance."
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            result.onSuccess { return Result.success(it) }
            result.onFailure { error ->
                lastFailure = error
                if (attempt < MAX_GENERATION_ATTEMPTS - 1) {
                    Log.w(
                        TAG,
                        "Generation attempt ${attempt + 1}/$MAX_GENERATION_ATTEMPTS failed: ${error.message}. Retrying…"
                    )
                    delay(OSRM_RETRY_BACKOFF_MS)
                }
            }
        }
        return Result.failure(
            lastFailure ?: IllegalStateException("Route generation failed after $MAX_GENERATION_ATTEMPTS attempts")
        )
    }

    private suspend fun generateRouteAttempt(
        origin: GeoPoint,
        targetDistanceM: Double,
        maxPathToAirRatio: Double,
        airDistanceToleranceM: Double,
        attemptIndex: Int,
        onRouteUpdate: (suspend (Route) -> Unit)? = null
    ): Route {
        require(targetDistanceM in 500.0..50_000.0) {
            "Target distance must be between 500 m and 50 km"
        }

        val routeId = UUID.randomUUID().toString()
        val emitRouteUpdate: suspend (Route) -> Unit = { route ->
            // Only push routed candidates to UI — avoids Map stuck on "Routing…" with path=0.
            if (route.hasRoutedWalkingPath) {
                onRouteUpdate?.invoke(route)
            }
        }

        val rng = Random(
            seed = System.nanoTime() xor
                origin.latitude.toBits().toLong() xor
                (origin.longitude.toBits().toLong() shl 32) xor
                (attemptIndex.toLong() shl 48)
        )
        val n = computeRandomCheckpointCount(targetDistanceM, rng)
        // Short targets: classic placement avoids POI greedy filter emptying the pool.
        if (targetDistanceM < SHORT_ROUTE_M) {
            Log.d(TAG, "Short route ${targetDistanceM.toInt()}m — classic generator")
            return generateClassicRoute(
                origin, targetDistanceM, n, rng, maxPathToAirRatio, airDistanceToleranceM, routeId, emitRouteUpdate
            )
        }
        val candidates = fetchCandidates(origin, targetDistanceM)
        if (candidates.size < n) {
            Log.d(TAG, "Object-first fallback: candidates=${candidates.size}, needed=$n")
            return generateClassicRoute(
                origin, targetDistanceM, n, rng, maxPathToAirRatio, airDistanceToleranceM, routeId, emitRouteUpdate
            )
        }
        var bestRoute: Route? = null
        var bestRouteFallback: Route? = null
        var bestError = Double.MAX_VALUE
        var bestErrorFallback = Double.MAX_VALUE

        repeat(MAX_ITERATIONS) { iteration ->
            val selected = chooseCandidateSequence(
                origin = origin,
                targetDistanceM = targetDistanceM,
                desiredCount = n,
                pool = candidates,
                rng = rng,
                airDistanceToleranceM = airDistanceToleranceM
            )
            if (selected == null) return@repeat
            Log.d(TAG, "Iteration $iteration — selected=${selected.size} objects")
            val rawPositions = selected.map { it.position }

            val routed = fetchRoutedLoop(origin, rawPositions) ?: return@repeat
            val snapped = routed.checkpointPositions
            val osrmResult = routed.osrmResult

            val distancesFromPrev = computeDistancesFromPrev(origin, snapped)

            val totalStraightLineM = distancesFromPrev.sum()
            val error = abs(totalStraightLineM - targetDistanceM)

            Log.d(TAG, "  actual=${totalStraightLineM.toInt()}m error=${error.toInt()}m")

            // Path-to-air ratio: reject candidates whose OSRM walking path is too long vs air legs.
            val ratioRejection = pathToAirRejection(
                osrmResult, totalStraightLineM, maxPathToAirRatio
            )
            if (ratioRejection.rejected) {
                Log.d(TAG, "  ratio reject: ${ratioRejection.reason}")
            }

            val overTargetAir = totalStraightLineM > targetDistanceM + airDistanceToleranceM
            if (overTargetAir) {
                Log.d(
                    TAG,
                    "  air ${totalStraightLineM.toInt()}m exceeds target ${targetDistanceM.toInt()}m " +
                        "+ ${airDistanceToleranceM.toInt()}m tolerance"
                )
            }

            if (error < bestErrorFallback || (!ratioRejection.rejected && !overTargetAir && error < bestError)) {
                val (polyline, estimatedDurationS) = when {
                    osrmResult != null && osrmResult.code == "Ok" && osrmResult.routes.isNotEmpty() -> {
                        val r = osrmResult.routes.first()
                        simplifyPolyline(r.geometry.toGeoPoints()) to r.duration
                    }
                    else -> fallbackPolylineAndDuration(origin, snapped)
                }
                val actualPathM = when {
                    osrmResult != null && osrmResult.code == "Ok" && osrmResult.routes.isNotEmpty() ->
                        osrmResult.routes.first().distance
                    else -> 0.0
                }
                val candidate = buildRoute(
                    id = routeId,
                    origin = origin,
                    snapped = snapped,
                    selected = selected,
                    distancesFromPrev = distancesFromPrev,
                    polyline = polyline,
                    targetDistanceM = targetDistanceM,
                    totalDistanceM = totalStraightLineM,
                    actualPathDistanceM = actualPathM,
                    estimatedDurationS = estimatedDurationS
                )
                if (error < bestErrorFallback) {
                    bestErrorFallback = error
                    bestRouteFallback = candidate
                    emitRouteUpdate(candidate)
                }
                if (!ratioRejection.rejected &&
                    !overTargetAir &&
                    error < bestError
                ) {
                    bestError = error
                    bestRoute = candidate
                    emitRouteUpdate(candidate)
                }
            }

            if (overTargetAir) return@repeat

            if (error <= FAST_ACCEPT_TOLERANCE_M && !ratioRejection.rejected && (bestRoute != null || bestRouteFallback != null)) {
                Log.d(TAG, "Fast accept within ±${FAST_ACCEPT_TOLERANCE_M.toInt()}m")
                val done = selectAcceptableRoute(
                    bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
                ) ?: error(
                    routeRejectionFailureMessage(
                        targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRouteFallback
                    )
                )
                return finalizeRoute(done)
            }

            if (error <= airDistanceToleranceM) {
                Log.d(TAG, "Within ±${airDistanceToleranceM.toInt()}m, done.")
                val done = selectAcceptableRoute(
                    bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
                )
                if (done != null) return finalizeRoute(done)
            }
        }

        selectAcceptableRoute(
            bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
        )?.let { route ->
            return finalizeRoute(route)
        }

        bestRouteFallback?.let { fallback ->
            Log.w(
                TAG,
                "No route met target air (±${airDistanceToleranceM.toInt()}m) and path/air ratio $maxPathToAirRatio"
            )
            error(
                routeRejectionFailureMessage(
                    targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, fallback
                )
            )
        }

        if (bestRoute == null) {
            Log.w(TAG, "Object-based iterations produced no valid route; classic generator fallback")
            return generateClassicRoute(
                origin, targetDistanceM, n, rng, maxPathToAirRatio, airDistanceToleranceM, routeId, emitRouteUpdate
            )
        }

        error(
            routeRejectionFailureMessage(
                targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRoute
            )
        )
    }

    private suspend fun generateClassicRoute(
        origin: GeoPoint,
        targetDistanceM: Double,
        n: Int,
        rng: Random,
        maxPathToAirRatio: Double,
        airDistanceToleranceM: Double,
        routeId: String,
        emitRouteUpdate: suspend (Route) -> Unit
    ): Route {
        var segmentLengths = computeRandomSegmentLengths(n + 1, targetDistanceM, rng)
        var bestRoute: Route? = null
        var bestRouteFallback: Route? = null
        var bestError = Double.MAX_VALUE
        var bestErrorFallback = Double.MAX_VALUE

        repeat(MAX_ITERATIONS) {
            val rawPositions = generatePointsSequentially(origin, segmentLengths.take(n), targetDistanceM, rng)
                ?: return@repeat

            val routed = fetchRoutedLoop(origin, rawPositions) ?: return@repeat
            val snapped = routed.checkpointPositions
            val osrmResult = routed.osrmResult

            val distancesFromPrev = computeDistancesFromPrev(origin, snapped)
            val totalStraightLineM = distancesFromPrev.sum()
            val error = abs(totalStraightLineM - targetDistanceM)
            val ratioRejection = pathToAirRejection(
                osrmResult, totalStraightLineM, maxPathToAirRatio
            )
            if (error < bestErrorFallback) {
                bestErrorFallback = error
                val fallback = buildCandidateRoute(
                    routeId, origin, snapped, distancesFromPrev, targetDistanceM,
                    totalStraightLineM, osrmResult
                )
                bestRouteFallback = fallback
                emitRouteUpdate(fallback)
            }
            val overTargetAir = totalStraightLineM > targetDistanceM + airDistanceToleranceM
            if (!ratioRejection.rejected && !overTargetAir && error < bestError) {
                bestError = error
                val candidate = buildCandidateRoute(
                    routeId, origin, snapped, distancesFromPrev, targetDistanceM,
                    totalStraightLineM, osrmResult
                )
                bestRoute = candidate
                emitRouteUpdate(candidate)
            }
            if (overTargetAir) return@repeat
            if (error <= FAST_ACCEPT_TOLERANCE_M && !ratioRejection.rejected && (bestRoute != null || bestRouteFallback != null)) {
                val done = selectAcceptableRoute(
                    bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
                ) ?: error(
                    routeRejectionFailureMessage(
                        targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRouteFallback
                    )
                )
                return finalizeRoute(done)
            }
            if (error <= airDistanceToleranceM && (bestRoute != null || bestRouteFallback != null)) {
                val done = selectAcceptableRoute(
                    bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
                )
                if (done != null) return finalizeRoute(done)
            }
            val scale = targetDistanceM / totalStraightLineM
            segmentLengths = segmentLengths.map { it * scale }
        }
        selectAcceptableRoute(
            bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
        )?.let { route ->
            return finalizeRoute(route)
        }
        bestRouteFallback?.let { fallback ->
            error(
                routeRejectionFailureMessage(
                    targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, fallback
                )
            )
        }
        error(
            "Could not find a valid route. One or more points may be unreachable (e.g. in water or no roads nearby). Try again or move to a different area."
        )
    }

    private fun buildCandidateRoute(
        routeId: String,
        origin: GeoPoint,
        snapped: List<GeoPoint>,
        distancesFromPrev: List<Double>,
        targetDistanceM: Double,
        totalStraightLineM: Double,
        osrmResult: OsrmRouteResponse?
    ): Route {
        val (polyline, estimatedDurationS) = when {
            osrmResult != null && osrmResult.code == "Ok" && osrmResult.routes.isNotEmpty() -> {
                val r = osrmResult.routes.first()
                simplifyPolyline(r.geometry.toGeoPoints()) to r.duration
            }
            else -> fallbackPolylineAndDuration(origin, snapped)
        }
        val actualPathM = when {
            osrmResult != null && osrmResult.code == "Ok" && osrmResult.routes.isNotEmpty() ->
                osrmResult.routes.first().distance
            else -> 0.0
        }
        return buildRoute(
            id = routeId,
            origin = origin,
            snapped = snapped,
            selected = emptyList(),
            distancesFromPrev = distancesFromPrev,
            polyline = polyline,
            targetDistanceM = targetDistanceM,
            totalDistanceM = totalStraightLineM,
            actualPathDistanceM = actualPathM,
            estimatedDurationS = estimatedDurationS
        )
    }

    private fun loopAirDistanceM(route: Route): Double =
        route.totalDistanceM.coerceAtLeast(0.0)

    private fun exceedsMaxPathToAirRatio(route: Route, maxRatio: Double): Boolean {
        if (!route.hasRoutedWalkingPath) return false
        val airM = loopAirDistanceM(route).coerceAtLeast(1.0)
        return route.actualPathDistanceM > airM * maxRatio
    }

    private fun withinTargetAirTolerance(route: Route, targetM: Double, airToleranceM: Double): Boolean =
        abs(loopAirDistanceM(route) - targetM) <= airToleranceM

    private fun selectAcceptableRoute(
        preferred: Route?,
        fallback: Route?,
        targetM: Double,
        maxRatio: Double,
        airToleranceM: Double
    ): Route? {
        fun acceptable(route: Route): Boolean {
            val hasPath = route.hasRoutedWalkingPath
            val airOk = withinTargetAirTolerance(route, targetM, airToleranceM)
            val ratioOk = !exceedsMaxPathToAirRatio(route, maxRatio)
            return hasPath && airOk && ratioOk
        }

        if (preferred != null && acceptable(preferred)) return preferred
        if (fallback != null && acceptable(fallback)) return fallback
        return null
    }

    private fun routeRejectionFailureMessage(
        targetM: Double,
        maxRatio: Double,
        airToleranceM: Double,
        fallback: Route?
    ): String {
        if (fallback == null) {
            return "Could not find a route near ${GeoUtils.formatDistance(targetM)} " +
                "(±${airToleranceM.toInt()} m air) within path/air limit ${"%.1f".format(maxRatio)}×. " +
                "Try again or adjust Settings."
        }
        val targetKm = targetM / 1000.0
        val airKm = loopAirDistanceM(fallback) / 1000.0
        val pathKm = fallback.pathDistanceM?.div(1000.0)
        val pathAirRatio = pathKm?.let { it / airKm.coerceAtLeast(0.001) }
        val airOffTargetM = abs(loopAirDistanceM(fallback) - targetM)
        val issues = buildList {
            if (!fallback.hasRoutedWalkingPath) {
                add("walking path not available (roads could not be routed)")
            }
            if (airOffTargetM > airToleranceM) {
                add("air ${"%.1f".format(airKm)} km vs target ${"%.1f".format(targetKm)} km")
            }
            if (exceedsMaxPathToAirRatio(fallback, maxRatio)) {
                add("path/air ${"%.2f".format(pathAirRatio)}× > limit ${"%.1f".format(maxRatio)}×")
            }
        }
        val pathLabel = pathKm?.let { "${"%.1f".format(it)} km path" } ?: "no routed path"
        return "No acceptable route after ${MAX_GENERATION_ATTEMPTS} attempts. " +
            "Best try: $pathLabel, ${"%.1f".format(airKm)} km air " +
            "(${issues.joinToString("; ")}). Try adjusting Settings or generate again."
    }

    private data class PathToAirRejection(
        val rejected: Boolean,
        val reason: String? = null,
        val totalPathM: Double = 0.0,
        val totalAirM: Double = 0.0
    )

    private fun pathToAirRejection(
        osrmResult: OsrmRouteResponse?,
        totalStraightLineM: Double,
        maxPathToAirRatio: Double
    ): PathToAirRejection {
        if (osrmResult == null || osrmResult.code != "Ok" || osrmResult.routes.isEmpty()) {
            return PathToAirRejection(rejected = false)
        }
        val route = osrmResult.routes.first()
        val totalPath = route.distance
        if (totalPath > totalStraightLineM * maxPathToAirRatio) {
            return PathToAirRejection(
                rejected = true,
                reason = "walking path exceeds air distance limit",
                totalPathM = totalPath,
                totalAirM = totalStraightLineM
            )
        }
        return PathToAirRejection(rejected = false, totalPathM = totalPath, totalAirM = totalStraightLineM)
    }

    private suspend fun finalizeRoute(route: Route): Route =
        if (OVERPASS_ENRICH_CHECKPOINTS) enrichWithDescriptions(route) else route

    private fun computeCheckpointCount(distanceM: Double): Int {
        // ~1.5 km air per checkpoint; capped for public OSRM.
        val approx = (distanceM / 1_500.0).roundToInt().coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
        return approx
    }

    private fun computeRandomCheckpointCount(distanceM: Double, rng: Random): Int {
        val base = computeCheckpointCount(distanceM)
        val lo = maxOf(MIN_CHECKPOINTS, base - 1)
        val hi = minOf(MAX_CHECKPOINTS, base + 1)
        return rng.nextInt(lo, hi + 1)
    }

    private fun computeRandomSegmentLengths(n: Int, targetM: Double, rng: Random): List<Double> {
        val raw = List(n) { SEGMENT_MIN_M + rng.nextDouble() * (SEGMENT_MAX_M - SEGMENT_MIN_M) }
        val sum = raw.sum()
        return raw.map { it * (targetM / sum) }
    }

    private data class CandidateObject(
        val position: GeoPoint,
        val element: OverpassElement,
        val weight: Double
    )

    private suspend fun fetchCandidates(origin: GeoPoint, targetDistanceM: Double): List<CandidateObject> {
        val radiusM = (targetDistanceM / 2.0).roundToInt().coerceIn(400, 3500)
        val query = buildCandidateOverpassQuery(origin.latitude, origin.longitude, radiusM)
        val elements = runCatching { overpass.query(query).elements }.getOrElse { emptyList() }
        return elements.mapNotNull { e ->
            val point = e.coordinateOrNull() ?: return@mapNotNull null
            val tag = e.primaryTag ?: return@mapNotNull null
            val weight = TAG_WEIGHTS[tag] ?: 1.0
            CandidateObject(point, e, weight)
        }.distinctBy { "${it.position.latitude},${it.position.longitude},${it.element.primaryTag}" }
    }

    private suspend fun generatePointsSequentially(
        origin: GeoPoint,
        segmentLengths: List<Double>,
        targetDistanceM: Double,
        rng: Random
    ): List<GeoPoint>? {
        val result = mutableListOf<GeoPoint>()
        var prev = origin
        var spentM = 0.0
        var maxRadiusM = 0.0

        for (segmentTargetM in segmentLengths) {
            var point: GeoPoint? = null
            repeat(SNAP_RETRIES) { attempt ->
                val bearing = pickSegmentBearing(
                    origin, prev, spentM, targetDistanceM, rng, attempt
                )
                val offsetM = if (attempt == 0) 0.0 else 40.0 * attempt
                val candidate = if (offsetM == 0.0) {
                    GeoUtils.offsetPoint(prev, segmentTargetM, bearing)
                } else {
                    val base = GeoUtils.offsetPoint(prev, segmentTargetM, bearing)
                    GeoUtils.offsetPoint(base, offsetM, rng.nextDouble(0.0, 360.0))
                }
                val distFromOrigin = GeoUtils.distanceMeters(origin, candidate)
                if (shouldCloseLoop(spentM, targetDistanceM) &&
                    maxRadiusM > 0 &&
                    distFromOrigin > maxRadiusM * MAX_RADIUS_GROWTH_AFTER_HALF
                ) {
                    return@repeat
                }
                point = candidate
                return@repeat
            }
            val placed = point ?: return null
            result.add(placed)
            spentM += GeoUtils.distanceMeters(prev, placed)
            maxRadiusM = max(maxRadiusM, GeoUtils.distanceMeters(origin, placed))
            prev = placed
        }
        return result
    }

    private fun pickSegmentBearing(
        origin: GeoPoint,
        prev: GeoPoint,
        spentM: Double,
        targetDistanceM: Double,
        rng: Random,
        attempt: Int
    ): Double {
        val closing = shouldCloseLoop(spentM, targetDistanceM)
        val towardStart = GeoUtils.bearingDegrees(prev, origin)
        val awayFromStart = GeoUtils.bearingDegrees(origin, prev)
        val jitter = when {
            closing -> rng.nextDouble(-50.0, 50.0) + attempt * 15.0
            else -> rng.nextDouble(-70.0, 70.0)
        }
        return if (closing) {
            (towardStart + jitter + 360.0) % 360.0
        } else {
            (awayFromStart + jitter + 360.0) % 360.0
        }
    }

    private fun shouldCloseLoop(spentM: Double, targetDistanceM: Double): Boolean =
        spentM >= targetDistanceM * BUDGET_CLOSE_LOOP_FRACTION

    private fun partialLoopDistanceM(
        origin: GeoPoint,
        chosen: List<CandidateObject>,
        current: GeoPoint
    ): Double {
        var prev = origin
        var sum = 0.0
        for (c in chosen) {
            sum += GeoUtils.distanceMeters(prev, c.position)
            prev = c.position
        }
        sum += GeoUtils.distanceMeters(prev, current)
        return sum
    }

    private fun maxRadiusM(origin: GeoPoint, chosen: List<CandidateObject>): Double =
        chosen.maxOfOrNull { GeoUtils.distanceMeters(origin, it.position) } ?: 0.0

    private fun chooseCandidateSequence(
        origin: GeoPoint,
        targetDistanceM: Double,
        desiredCount: Int,
        pool: List<CandidateObject>,
        rng: Random,
        airDistanceToleranceM: Double
    ): List<CandidateObject>? {
        val remaining = pool.shuffled(rng).toMutableList()
        val chosen = mutableListOf<CandidateObject>()
        var current = origin
        val remainingAfterSteps = { stepsLeft: Int ->
            // Leave budget for remaining legs + return home.
            (targetDistanceM - partialLoopDistanceM(origin, chosen, current))
                .coerceAtLeast(SEGMENT_MIN_M) / max(1, stepsLeft)
        }

        repeat(desiredCount) { step ->
            val stepsLeftIncludingThis = desiredCount - step
            val spentPartial = partialLoopDistanceM(origin, chosen, current)
            val closingPhase = shouldCloseLoop(spentPartial, targetDistanceM)
            val prevMaxRadius = maxRadiusM(origin, chosen)
            val targetLegM = remainingAfterSteps(stepsLeftIncludingThis + 1) // +1 return approx

            var viable = remaining.filter { c ->
                chosen.none { already ->
                    GeoUtils.distanceMeters(already.position, c.position) < CANDIDATE_MIN_SPACING_M
                }
            }
            if (closingPhase && prevMaxRadius > 0) {
                val inwardOnly = viable.filter { c ->
                    GeoUtils.distanceMeters(origin, c.position) <= prevMaxRadius * MAX_RADIUS_GROWTH_AFTER_HALF
                }
                if (inwardOnly.isNotEmpty()) viable = inwardOnly
            }
            if (viable.isEmpty()) return null

            val isLast = step == desiredCount - 1
            if (isLast) {
                // Final pick: full loop must land near target air.
                val buildTolerance = airDistanceToleranceM * 1.25
                viable = viable.filter { c ->
                    abs(loopDistance(origin, chosen + c) - targetDistanceM) <= buildTolerance
                }
                if (viable.isEmpty()) return null
            } else {
                // Intermediate: keep partial spent + return estimate under soft headroom.
                val softCap = targetDistanceM + airDistanceToleranceM * 1.5
                viable = viable.filter { c ->
                    val leg = GeoUtils.distanceMeters(current, c.position)
                    val returnHome = GeoUtils.distanceMeters(c.position, origin)
                    val projected = spentPartial + leg + returnHome
                    projected <= softCap && leg >= SEGMENT_MIN_M * 0.5
                }
                if (viable.isEmpty()) return null
            }

            val scored = viable.map { c ->
                val distFromOrigin = GeoUtils.distanceMeters(origin, c.position)
                val leg = GeoUtils.distanceMeters(current, c.position)
                val currentLoop = loopDistance(origin, chosen + c)
                val error = abs(currentLoop - targetDistanceM)
                val legError = abs(leg - targetLegM)
                var quality = (1.0 / (1.0 + error * 0.35 + legError * 0.65)) * c.weight
                if (closingPhase) {
                    if (prevMaxRadius > 0 && distFromOrigin > prevMaxRadius) {
                        quality *= 0.2
                    }
                    val lastDist = GeoUtils.distanceMeters(origin, current)
                    if (distFromOrigin < lastDist) quality *= 1.25
                } else if (step == 0) {
                    quality *= 1.0 + (distFromOrigin / targetDistanceM).coerceIn(0.0, 0.35)
                }
                quality += rng.nextDouble(0.0, 0.02)
                c to quality
            }
            val best = scored.maxByOrNull { it.second }?.first ?: return null
            chosen += best
            remaining.remove(best)
            current = best.position
        }
        // Final full-loop gate (in case last filter was soft).
        val finalAir = loopDistance(origin, chosen)
        if (abs(finalAir - targetDistanceM) > airDistanceToleranceM * 1.25) return null
        return chosen
    }

    private fun loopDistance(origin: GeoPoint, objects: List<CandidateObject>): Double {
        if (objects.isEmpty()) return 0.0
        var prev = origin
        var sum = 0.0
        for (c in objects) {
            sum += GeoUtils.distanceMeters(prev, c.position)
            prev = c.position
        }
        sum += GeoUtils.distanceMeters(prev, origin)
        return sum
    }

    private suspend fun snapOne(candidate: GeoPoint): GeoPoint? {
        val response = runCatching {
            osrm.nearest(GeoUtils.toOsrmCoordinate(candidate))
        }.getOrNull() ?: return null
        if (response.code != "Ok" || response.waypoints.isEmpty()) return null
        val wp = response.waypoints.first()
        val snapped = GeoPoint(wp.latitude, wp.longitude)
        if (GeoUtils.distanceMeters(candidate, snapped) > SNAP_MAX_DISTANCE_M) return null
        return snapped
    }

    private suspend fun snapAllParallel(candidates: List<GeoPoint>): List<GeoPoint>? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(SNAP_PARALLELISM)
        val results = candidates.map { point ->
            async {
                semaphore.withPermit { snapOne(point) }
            }
        }.awaitAll()
        if (results.any { it == null }) return@coroutineScope null
        results.filterNotNull()
    }

    private data class RoutedLoopResult(
        val osrmResult: OsrmRouteResponse,
        val checkpointPositions: List<GeoPoint>
    )

    /**
     * Fetches a walking loop via OSRM /route, letting OSRM snap waypoints to the road network.
     * Falls back to parallel /nearest snapping when route snapping fails validation.
     */
    private suspend fun fetchRoutedLoop(
        origin: GeoPoint,
        checkpointPositions: List<GeoPoint>
    ): RoutedLoopResult? {
        val inputPoints = listOf(origin) + checkpointPositions + origin
        val osrmResult = runCatching {
            osrm.route(GeoUtils.toOsrmCoordinates(inputPoints))
        }.getOrNull()

        if (osrmResult == null) {
            delay(OSRM_RETRY_BACKOFF_MS)
            return fetchRoutedLoopWithParallelSnap(origin, checkpointPositions)
        }

        if (osrmResult.code != "Ok" || osrmResult.routes.isEmpty()) {
            delay(OSRM_RETRY_BACKOFF_MS)
            return fetchRoutedLoopWithParallelSnap(origin, checkpointPositions)
        }

        val snapped = extractCheckpointSnaps(osrmResult, inputPoints.size)
        if (snapped == null) {
            delay(OSRM_RETRY_BACKOFF_MS)
            return fetchRoutedLoopWithParallelSnap(origin, checkpointPositions)
        }

        return RoutedLoopResult(osrmResult, snapped)
    }

    private suspend fun fetchRoutedLoopWithParallelSnap(
        origin: GeoPoint,
        checkpointPositions: List<GeoPoint>
    ): RoutedLoopResult? {
        val snapped = snapAllParallel(checkpointPositions) ?: return null
        val inputPoints = listOf(origin) + snapped + origin
        val osrmResult = runCatching {
            osrm.route(GeoUtils.toOsrmCoordinates(inputPoints))
        }.getOrNull() ?: return null
        if (osrmResult.code != "Ok" || osrmResult.routes.isEmpty()) return null
        val validated = extractCheckpointSnaps(osrmResult, inputPoints.size) ?: snapped
        return RoutedLoopResult(osrmResult, validated)
    }

    private fun extractCheckpointSnaps(
        osrmResult: OsrmRouteResponse,
        expectedWaypointCount: Int
    ): List<GeoPoint>? {
        if (osrmResult.waypoints.size != expectedWaypointCount) return null
        if (osrmResult.waypoints.any { it.distance > SNAP_MAX_DISTANCE_M }) return null
        if (expectedWaypointCount <= 2) return emptyList()
        return osrmResult.waypoints
            .drop(1)
            .dropLast(1)
            .map { GeoPoint(it.latitude, it.longitude) }
    }

    private fun computeDistancesFromPrev(origin: GeoPoint, checkpoints: List<GeoPoint>): List<Double> =
        buildList {
            var prev = origin
            for (cp in checkpoints) {
                add(GeoUtils.distanceMeters(prev, cp))
                prev = cp
            }
            add(GeoUtils.distanceMeters(prev, origin))
        }

    private fun simplifyPolyline(points: List<GeoPoint>): List<GeoPoint> =
        PolylineSimplifier.simplify(points, POLYLINE_SIMPLIFY_TOLERANCE_M)

    private fun fallbackPolylineAndDuration(origin: GeoPoint, snapped: List<GeoPoint>): Pair<List<GeoPoint>, Double> {
        val polyline = buildList {
            add(origin)
            addAll(snapped)
            add(origin)
        }
        val totalM = polyline.zipWithNext { a, b -> GeoUtils.distanceMeters(a, b) }.sum()
        val durationSec = totalM / 1000.0 * (3600.0 / RUNNING_SPEED_KMH) // 10 km/h running
        return polyline to durationSec
    }

    private fun buildRoute(
        id: String,
        origin: GeoPoint,
        snapped: List<GeoPoint>,
        selected: List<CandidateObject>,
        distancesFromPrev: List<Double>,
        polyline: List<GeoPoint>,
        targetDistanceM: Double,
        totalDistanceM: Double,
        actualPathDistanceM: Double,
        estimatedDurationS: Double
    ): Route {
        val checkpoints = buildList {
            add(
                Checkpoint(
                    id = 0,
                    position = origin,
                    description = "Start / Finish — return here to complete your run.",
                    isStart = true,
                    distanceFromPrev = 0.0
                )
            )
            snapped.forEachIndexed { idx, point ->
                val defaultDesc = Triple("Checkpoint ${idx + 1}", null, null)
                val (desc, name, tag) = if (selected.isNotEmpty() && idx < selected.size) {
                    CheckpointDescriptionGenerator.generate(idx, listOf(selected[idx].element))
                } else {
                    defaultDesc
                }
                add(
                    Checkpoint(
                        id = idx + 1,
                        position = point,
                        description = desc,
                        landmarkName = name,
                        landmarkType = tag,
                        distanceFromPrev = distancesFromPrev[idx]
                    )
                )
            }
        }

        return Route(
            id = id,
            checkpoints = checkpoints,
            polylinePoints = polyline,
            targetDistanceM = targetDistanceM,
            totalDistanceM = totalDistanceM,
            actualPathDistanceM = actualPathDistanceM,
            estimatedDurationS = estimatedDurationS
        )
    }

    private suspend fun enrichWithDescriptions(route: Route): Route {
        val enriched = route.checkpoints.map { cp ->
            if (cp.isStart) return@map cp
            if (!cp.description.startsWith("Checkpoint ${cp.id}: The ")) return@map cp
            val elements = runCatching {
                val query = buildOverpassQuery(cp.position.latitude, cp.position.longitude)
                overpass.query(query).elements
            }.getOrElse { emptyList() }
            val ranked = rankElementsNear(cp.position, elements)
            val (desc, name, tag) = CheckpointDescriptionGenerator.generate(cp.id, ranked)
            cp.copy(description = desc, landmarkName = name, landmarkType = tag)
        }
        return route.copy(checkpoints = enriched)
    }

    private fun buildOverpassQuery(lat: Double, lon: Double): String =
        buildAreaOverpassQuery(lat, lon, radiusM = 130, timeoutSec = 8, maxElements = 16)

    private fun buildCandidateOverpassQuery(lat: Double, lon: Double, radiusM: Int): String =
        buildAreaOverpassQuery(lat, lon, radiusM = radiusM, timeoutSec = 6, maxElements = 24)

    /** Single union query (nodes + ways + relations) — fewer round-trips than separate node/way blocks. */
    private fun buildAreaOverpassQuery(
        lat: Double,
        lon: Double,
        radiusM: Int,
        timeoutSec: Int,
        maxElements: Int
    ): String {
        val poiLines = OVERPASS_POI_KEYS.joinToString("\n") { key ->
            "  nwr[\"$key\"](around:$radiusM,$lat,$lon);"
        }
        return """
            [out:json][timeout:$timeoutSec];
            (
            $poiLines
              node["highway"~"bus_stop|traffic_signals|crossing"](around:$radiusM,$lat,$lon);
            );
            out center tags $maxElements;
        """.trimIndent()
    }

    private fun rankElementsNear(anchor: GeoPoint, elements: List<OverpassElement>): List<OverpassElement> =
        elements
            .mapNotNull { e -> e.coordinateOrNull()?.let { coord -> e to GeoUtils.distanceMeters(anchor, coord) } }
            .filter { (e, _) -> e.primaryTag != null }
            .sortedWith(
                compareBy<Pair<OverpassElement, Double>> { it.second }
                    .thenByDescending { (e, _) -> TAG_WEIGHTS[e.primaryTag] ?: 1.0 }
            )
            .map { it.first }
}
