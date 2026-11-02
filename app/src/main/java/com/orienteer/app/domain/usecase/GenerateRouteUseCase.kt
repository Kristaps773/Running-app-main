package com.orienteer.app.domain.usecase

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.OverpassApiService
import com.orienteer.app.data.api.parseJson
import com.orienteer.app.data.local.RouteGenerationPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OsrmNearestResponse
import com.orienteer.app.data.model.OsrmRouteResponse
import com.orienteer.app.data.model.OverpassElement
import com.orienteer.app.data.model.OverpassResponse
import com.orienteer.app.data.model.Route
import com.orienteer.app.domain.CourseEndpoints
import com.orienteer.app.util.CheckpointDescriptionGenerator
import com.orienteer.app.util.GeoUtils
import com.orienteer.app.util.PolylineSimplifier
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import javax.inject.Inject
import kotlin.math.*
import retrofit2.HttpException
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
    private val overpass: OverpassApiService,
    private val gson: Gson
) {

    private sealed class RoutingFailure {
        data class Network(val cause: IOException) : RoutingFailure()
        data class HttpStatus(val code: Int) : RoutingFailure()
        data class OsrmResponse(val code: String) : RoutingFailure()
        data object ResponseParse : RoutingFailure()
        data object RoadSnap : RoutingFailure()
    }

    private var lastRoutingFailure: RoutingFailure? = null
    /** Last OSRM API label that failed (`nearest`, `route`, `route-resnap`). */
    private var lastOsrmCallLabel: String? = null
    /** Per-attempt debug fields attached to [RouteGenerationFailedException] for Sentry. */
    private val attemptDiagnostics = linkedMapOf<String, Any?>()

    companion object {
        private const val TAG = "GenerateRouteUseCase"

        /**
         * When true, one batched Overpass query fills missing landmarkType/description on classic
         * (and any) controls that were not attached during object-ring selection.
         */
        private const val OVERPASS_ENRICH_MISSING_LANDMARKS = true
        private const val ENRICH_AROUND_RADIUS_M = 130
        private const val ENRICH_MAX_ELEMENTS = 48

        private const val SEGMENT_MIN_M = 200.0
        private const val SEGMENT_MAX_M = 2_000.0
        private const val MIN_CHECKPOINTS = 2
        /** Cap for public OSRM — too many waypoints → slow/fail /route. */
        private const val MAX_CHECKPOINTS = 10
        /** Max OSRM snap distance from requested point to road (too tight → all candidates rejected). */
        private const val SNAP_MAX_DISTANCE_M = 1_200.0
        private const val SNAP_RETRIES = 4
        private const val CANDIDATE_MIN_SPACING_M = 120.0
        private const val FAST_ACCEPT_TOLERANCE_M = 500.0
        /** Floor / share of target for fast-accept (fixed 500 m is too loose on 1–3 km loops). */
        private const val FAST_ACCEPT_MIN_M = 150.0
        private const val FAST_ACCEPT_TARGET_FRACTION = 0.12
        /** After this fraction of air budget is spent, stop expanding away from start. */
        private const val BUDGET_CLOSE_LOOP_FRACTION = 0.5
        /** Allow slight outward growth once closing phase started. */
        private const val MAX_RADIUS_GROWTH_AFTER_HALF = 1.05
        private const val MAX_ITERATIONS = 5
        private const val MAX_ITERATIONS_SHORT = 6
        /**
         * Full generation passes with a new random layout when an attempt fails
         * (OSRM unreachable, empty pool, etc.). Inner loops also try many layouts per attempt.
         */
        private const val MAX_GENERATION_ATTEMPTS = 3
        /** Per-attempt wall-clock limit so UI cannot hang forever on public OSRM. */
        private const val ATTEMPT_TIMEOUT_MS = 45_000L
        private const val OSRM_RETRY_BACKOFF_MS = 250L
        /** Cap Overpass so a slow POI query cannot consume the whole attempt. */
        private const val OVERPASS_TIMEOUT_MS = 6_000L
        private const val SHORT_ROUTE_M = 3_000.0
        private const val SNAP_PARALLELISM = 4
        private const val POLYLINE_SIMPLIFY_TOLERANCE_M = 8.0
        private const val RUNNING_SPEED_KMH = 10.0      // fallback est. time when OSRM route unavailable (km/h)
        /** Round lat/lon to ~110 m so nearby regenerations reuse POI fetch. */
        private const val OVERPASS_CACHE_COORD_DECIMALS = 3
        private const val OVERPASS_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val OVERPASS_CACHE_MAX_ENTRIES = 8
        private data class CachedOverpass(
            val candidates: List<Triple<GeoPoint, OverpassElement, Double>>,
            val storedAtMs: Long
        )

        private val overpassCandidateCache =
            ConcurrentHashMap<Long, CachedOverpass>()

        private val TAG_WEIGHTS = mapOf(
            "historic=monument" to 1.6,
            "historic=memorial" to 1.5,
            "historic=statue" to 1.55,
            "historic=ruins" to 1.5,
            "historic=wayside_cross" to 1.45,
            "historic=archaeological_site" to 1.45,
            "historic=castle" to 1.5,
            "tourism=viewpoint" to 1.5,
            "tourism=artwork" to 1.4,
            "tourism=information" to 1.35,
            "tourism=picnic_site" to 1.25,
            "amenity=fountain" to 1.3,
            "amenity=bench" to 1.2,
            "amenity=shelter" to 1.25,
            "amenity=drinking_water" to 1.2,
            "amenity=post_box" to 1.15,
            "natural=peak" to 1.4,
            "natural=rock" to 1.35,
            "natural=spring" to 1.35,
            "natural=tree" to 1.15,
            "man_made=tower" to 1.45,
            "man_made=water_tower" to 1.35,
            "man_made=windmill" to 1.4,
            "man_made=cross" to 1.4,
            "leisure=playground" to 1.2,
            "leisure=picnic_table" to 1.2,
            "leisure=fitness_station" to 1.15,
            "barrier=bollard" to 1.15,
            "highway=bus_stop" to 1.1,
            "highway=street_lamp" to 1.05
        )

        /** POI types unsuitable as checkpoint anchors (large areas, traffic, private). */
        private val EXCLUDED_PRIMARY_TAGS = setOf(
            "amenity=parking",
            "amenity=fuel",
            "amenity=bank",
            "amenity=hospital",
            "amenity=police",
            "amenity=prison",
            "leisure=pitch",
            "leisure=stadium"
        )
    }

    /**
     * @param origin          Runner's starting GPS position.
     * @param targetDistanceM Desired total straight-line route distance in metres.
     * @param destination     When non-null, one-way course ending at this point (not return-to-start).
     * @return                A [Result] wrapping the generated [Route]. All distances are straight-line.
     */
    suspend operator fun invoke(
        origin: GeoPoint,
        targetDistanceM: Double,
        generationPrefs: RouteGenerationPreferences = RouteGenerationPreferences(),
        destination: GeoPoint? = null,
        onRouteUpdate: (suspend (Route) -> Unit)? = null
    ): Result<Route> {
        when (
            val decision = CourseEndpoints.evaluateTargetDistance(targetDistanceM, origin, destination)
        ) {
            is CourseEndpoints.TargetDistanceDecision.BelowMinimum ->
                return Result.failure(
                    IllegalArgumentException(
                        "Course must be at least ${GeoUtils.formatDistance(decision.minM)} " +
                            "(start and finish are that far apart)."
                    )
                )
            is CourseEndpoints.TargetDistanceDecision.AboveMaximum ->
                return Result.failure(
                    IllegalArgumentException(
                        "Target distance must be at most ${GeoUtils.formatDistance(decision.maxM)}."
                    )
                )
            is CourseEndpoints.TargetDistanceDecision.EndpointsExceedMax ->
                return Result.failure(
                    IllegalArgumentException(
                        "Start and finish are ${GeoUtils.formatDistance(decision.airM)} apart — " +
                            "over the ${GeoUtils.formatDistance(decision.maxM)} course limit. Move them closer."
                    )
                )
            is CourseEndpoints.TargetDistanceDecision.Ok -> Unit
        }
        val maxPathToAirRatio = generationPrefs.maxPathToAirRatio.coerceIn(1.1f, 2.5f).toDouble()
        val airDistanceToleranceM = generationPrefs.airDistanceToleranceM
            .coerceIn(
                UserPreferencesStore.MIN_AIR_TOLERANCE_M.toFloat(),
                UserPreferencesStore.MAX_AIR_TOLERANCE_M.toFloat()
            )
            .toDouble()
        var lastFailure: Throwable? = null
        val attemptSummaries = mutableListOf<Map<String, Any?>>()
        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            lastRoutingFailure = null
            lastOsrmCallLabel = null
            attemptDiagnostics.clear()
            val attemptStartedMs = System.currentTimeMillis()
            attemptDiagnostics["attempt_index"] = attempt + 1
            attemptDiagnostics["max_attempts"] = MAX_GENERATION_ATTEMPTS
            attemptDiagnostics["target_distance_m"] = targetDistanceM.toInt()
            attemptDiagnostics["max_path_to_air_ratio"] = maxPathToAirRatio
            attemptDiagnostics["air_tolerance_m"] = airDistanceToleranceM.toInt()
            attemptDiagnostics["attempt_timeout_ms"] = ATTEMPT_TIMEOUT_MS
            attemptDiagnostics["one_way"] = destination != null
            val result = try {
                Result.success(
                    withTimeout(ATTEMPT_TIMEOUT_MS) {
                        generateRouteAttempt(
                            origin = origin,
                            targetDistanceM = targetDistanceM,
                            maxPathToAirRatio = maxPathToAirRatio,
                            airDistanceToleranceM = airDistanceToleranceM,
                            attemptIndex = attempt,
                            destination = destination,
                            onRouteUpdate = onRouteUpdate
                        )
                    }
                )
            } catch (e: TimeoutCancellationException) {
                // Must NOT call generationFail() here — it throws and skips retries / wrong Sentry stage.
                attemptDiagnostics["timed_out"] = true
                attemptDiagnostics["rejection_gate"] = "timeout"
                putRoutingFailureDiagnostics()
                Result.failure(
                    RouteGenerationFailedException(
                        "Route generation timed out. Try again or choose a shorter distance.",
                        e,
                        attemptDiagnostics.toMap()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouteGenerationFailedException) {
                Result.failure(e)
            } catch (e: Throwable) {
                putRoutingFailureDiagnostics()
                Result.failure(
                    RouteGenerationFailedException(
                        e.message ?: "Route generation failed. Try again.",
                        e,
                        attemptDiagnostics.toMap()
                    )
                )
            }
            result.onSuccess { return Result.success(it) }
            result.onFailure { error ->
                attemptDiagnostics.putIfAbsent(
                    "elapsed_ms",
                    (System.currentTimeMillis() - attemptStartedMs).toInt()
                )
                val summary = compactAttemptSummary(
                    (error as? RouteGenerationFailedException)?.diagnostics
                        ?: attemptDiagnostics.toMap()
                )
                attemptSummaries += summary
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
        val finalMessage = lastFailure?.message
            ?: "Route generation failed after $MAX_GENERATION_ATTEMPTS attempts"
        return Result.failure(
            RouteGenerationFailedException(
                message = finalMessage,
                cause = lastFailure?.takeIf { it !is RouteGenerationFailedException },
                diagnostics = buildMap {
                    put("attempts_tried", attemptSummaries.size)
                    put("max_attempts", MAX_GENERATION_ATTEMPTS)
                    put("target_distance_m", targetDistanceM.toInt())
                    put("max_path_to_air_ratio", maxPathToAirRatio)
                    put("air_tolerance_m", airDistanceToleranceM.toInt())
                    put("attempt_summaries", attemptSummaries)
                    (lastFailure as? RouteGenerationFailedException)?.diagnostics?.let { putAll(it) }
                }
            )
        )
    }

    private fun generationFail(message: String, cause: Throwable? = null): Nothing {
        putRoutingFailureDiagnostics()
        throw RouteGenerationFailedException(message, cause, attemptDiagnostics.toMap())
    }

    /** Key fields only — keeps multi-attempt Sentry payloads under size limits. */
    private fun compactAttemptSummary(full: Map<String, Any?>): Map<String, Any?> = buildMap {
        listOf(
            "attempt_index", "generator", "timed_out", "elapsed_ms",
            "routing_failure_kind", "osrm_code", "http_status", "last_osrm_call",
            "osrm_exception", "osrm_exception_message", "osrm_exception_cause",
            "network_exception", "rejection_gate", "rejection_gates",
            "checkpoint_count", "candidate_pool_size",
            "route_fetch_attempts", "route_fetch_successes", "iterations_run",
            "ratio_reject_count", "over_target_air_count", "selection_null_count",
            "fell_back_to_classic", "ring_r_m", "swap_count",
            "had_preferred_route", "had_fallback_route", "had_routed_path",
            "best_air_m", "best_air_error_m", "best_path_m", "best_path_air_ratio",
            "best_checkpoint_count"
        ).forEach { key ->
            if (full.containsKey(key)) put(key, full[key])
        }
    }

    private fun putRoutingFailureDiagnostics() {
        lastOsrmCallLabel?.let { attemptDiagnostics["last_osrm_call"] = it }
        when (val failure = lastRoutingFailure) {
            is RoutingFailure.Network -> {
                attemptDiagnostics["routing_failure_kind"] = "network"
                attemptDiagnostics["rejection_gate"] = "routing_network"
                attemptDiagnostics["network_exception"] = failure.cause.javaClass.simpleName
                attemptDiagnostics["network_message"] = failure.cause.message
            }
            is RoutingFailure.HttpStatus -> {
                attemptDiagnostics["routing_failure_kind"] = "http"
                attemptDiagnostics["rejection_gate"] = "routing_http"
                attemptDiagnostics["http_status"] = failure.code
            }
            is RoutingFailure.OsrmResponse -> {
                attemptDiagnostics["routing_failure_kind"] = "osrm_code"
                attemptDiagnostics["rejection_gate"] = "routing_osrm"
                attemptDiagnostics["osrm_code"] = failure.code
            }
            RoutingFailure.ResponseParse -> {
                // Keep a more specific kind (e.g. gson_type_erasure) if already set.
                attemptDiagnostics.putIfAbsent("routing_failure_kind", "response_parse")
                attemptDiagnostics.putIfAbsent("rejection_gate", "routing_parse")
            }
            RoutingFailure.RoadSnap -> {
                attemptDiagnostics["routing_failure_kind"] = "road_snap"
                attemptDiagnostics["rejection_gate"] = "road_snap"
            }
            null ->
                attemptDiagnostics.putIfAbsent("routing_failure_kind", "none")
        }
    }

    private suspend fun generateRouteAttempt(
        origin: GeoPoint,
        targetDistanceM: Double,
        maxPathToAirRatio: Double,
        airDistanceToleranceM: Double,
        attemptIndex: Int,
        destination: GeoPoint? = null,
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
                (attemptIndex.toLong() shl 48) xor
                (destination?.latitude?.toBits()?.toLong() ?: 0L)
        )
        val n = computeRandomCheckpointCount(targetDistanceM, rng)
        attemptDiagnostics["checkpoint_count"] = n

        // One-way: classic open chain only (keeps return-to-start object-ring path unchanged).
        if (destination != null) {
            attemptDiagnostics["generator"] = "classic_one_way"
            return generateOneWayClassicRoute(
                origin = origin,
                destination = destination,
                targetDistanceM = targetDistanceM,
                n = n,
                rng = rng,
                maxPathToAirRatio = maxPathToAirRatio,
                airDistanceToleranceM = airDistanceToleranceM,
                routeId = routeId,
                emitRouteUpdate = emitRouteUpdate
            )
        }

        val candidates = fetchCandidates(origin, targetDistanceM)
        attemptDiagnostics["candidate_pool_size"] = candidates.size
        if (candidates.size < n) {
            Log.d(TAG, "Object-ring fallback: candidates=${candidates.size}, needed=$n")
            attemptDiagnostics["generator"] = "classic_sparse_pois"
            return generateClassicRoute(
                origin, targetDistanceM, n, rng, maxPathToAirRatio, airDistanceToleranceM, routeId, emitRouteUpdate
            )
        }
        return generateObjectRingRoute(
            origin = origin,
            targetDistanceM = targetDistanceM,
            n = n,
            pool = candidates,
            rng = rng,
            maxPathToAirRatio = maxPathToAirRatio,
            airDistanceToleranceM = airDistanceToleranceM,
            routeId = routeId,
            emitRouteUpdate = emitRouteUpdate
        )
    }

    /**
     * Seed an even ring sized for [targetDistanceM], lock controls onto OSM objects,
     * route on foot, then swap POIs / rescale the ring under air-max + path/air limits.
     */
    private suspend fun generateObjectRingRoute(
        origin: GeoPoint,
        targetDistanceM: Double,
        n: Int,
        pool: List<CandidateObject>,
        rng: Random,
        maxPathToAirRatio: Double,
        airDistanceToleranceM: Double,
        routeId: String,
        emitRouteUpdate: suspend (Route) -> Unit
    ): Route {
        attemptDiagnostics["generator"] = "object_seed_search"
        val airMaxM = targetDistanceM + airDistanceToleranceM
        var ringR = ObjectRingPlanner.seedRadiusM(targetDistanceM, maxPathToAirRatio)
        var bearing0 = rng.nextDouble() * 360.0
        attemptDiagnostics["ring_r_m"] = ringR.roundToInt()

        var bestRoute: Route? = null
        var bestRouteFallback: Route? = null
        var bestError = Double.MAX_VALUE
        var bestErrorFallback = Double.MAX_VALUE
        var routeFetchAttempts = 0
        var routeFetchSuccesses = 0
        var ratioRejectCount = 0
        var overTargetAirCount = 0
        var selectionNullCount = 0
        var swapCount = 0
        val fastAcceptM = fastAcceptToleranceM(targetDistanceM)
        val iterationLimit = if (targetDistanceM < SHORT_ROUTE_M) MAX_ITERATIONS_SHORT else MAX_ITERATIONS
        val maxOsrmCalls = 6
        attemptDiagnostics["iteration_limit"] = iterationLimit
        attemptDiagnostics["fast_accept_tolerance_m"] = fastAcceptM.toInt()

        val rankedPool = pool.map {
            ObjectRingPlanner.RankedPoi(
                position = it.position,
                weight = it.weight,
                key = "${it.position.latitude},${it.position.longitude},${it.element.primaryTag}"
            )
        }
        val candidateByKey = pool.associateBy {
            "${it.position.latitude},${it.position.longitude},${it.element.primaryTag}"
        }

        fun selectedObjects(ranked: List<ObjectRingPlanner.RankedPoi>): List<CandidateObject> =
            ranked.mapNotNull { candidateByKey[it.key] }

        repeat(iterationLimit) { iteration ->
            if (routeFetchAttempts >= maxOsrmCalls) return@repeat
            val slots = ObjectRingPlanner.ringSlots(origin, ringR, n, bearing0)
            var ranked = ObjectRingPlanner.assignObjectsToRing(
                origin = origin,
                slots = slots,
                pool = rankedPool,
                minSpacingM = CANDIDATE_MIN_SPACING_M
            )
            if (ranked == null || ranked.size < n) {
                selectionNullCount++
                bearing0 = (bearing0 + 41.0 + rng.nextDouble() * 20.0) % 360.0
                return@repeat
            }

            var lastAirM = 0.0
            var lastPathM = 0.0
            var swapTries = 0
            while (swapTries < 3 && routeFetchAttempts < maxOsrmCalls) {
                val selected = selectedObjects(ranked!!)
                if (selected.size < n) {
                    selectionNullCount++
                    break
                }
                Log.d(TAG, "Object-ring iter=$iteration swap=$swapTries r=${ringR.toInt()}m n=$n")
                routeFetchAttempts++
                val routed = fetchRoutedLoop(origin, selected.map { it.position }) ?: break
                routeFetchSuccesses++
                val snapped = routed.checkpointPositions
                val osrmResult = routed.osrmResult
                val distancesFromPrev = computeDistancesFromPrev(origin, snapped)
                val totalStraightLineM = distancesFromPrev.sum()
                val softError = ObjectRingPlanner.airSoftErrorM(
                    totalStraightLineM, targetDistanceM, airMaxM
                )
                val underError = (targetDistanceM - totalStraightLineM).coerceAtLeast(0.0)

                val ratioRejection = pathToAirRejection(
                    osrmResult, totalStraightLineM, maxPathToAirRatio
                )
                if (ratioRejection.rejected) {
                    ratioRejectCount++
                    Log.d(TAG, "  ratio reject: ${ratioRejection.reason}")
                }
                val overTargetAir = totalStraightLineM > airMaxM
                if (overTargetAir) {
                    overTargetAirCount++
                    Log.d(
                        TAG,
                        "  air ${totalStraightLineM.toInt()}m exceeds max ${airMaxM.toInt()}m"
                    )
                }

                val pathM = when {
                    osrmResult.code == "Ok" && osrmResult.routeList().isNotEmpty() ->
                        osrmResult.routeList().first().distance
                    else -> 0.0
                }
                lastAirM = totalStraightLineM
                lastPathM = pathM

                if (softError < bestErrorFallback ||
                    (!ratioRejection.rejected && !overTargetAir && softError < bestError)
                ) {
                    val (polyline, estimatedDurationS) = when {
                        osrmResult.code == "Ok" && osrmResult.routeList().isNotEmpty() -> {
                            val r = osrmResult.routeList().first()
                            val geometry = r.geometry
                            if (geometry != null) {
                                simplifyPolyline(geometry.toGeoPoints()) to r.duration
                            } else {
                                fallbackPolylineAndDuration(origin, snapped)
                            }
                        }
                        else -> fallbackPolylineAndDuration(origin, snapped)
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
                        actualPathDistanceM = pathM,
                        estimatedDurationS = estimatedDurationS
                    )
                    if (softError < bestErrorFallback) {
                        bestErrorFallback = softError
                        bestRouteFallback = candidate
                        emitRouteUpdate(candidate)
                    }
                    if (!ratioRejection.rejected && !overTargetAir && softError < bestError) {
                        bestError = softError
                        bestRoute = candidate
                        emitRouteUpdate(candidate)
                    }
                }

                if (!overTargetAir && !ratioRejection.rejected && underError <= fastAcceptM) {
                    Log.d(TAG, "Fast accept under air max, within ${fastAcceptM.toInt()}m of target")
                    return finalizeRoute(
                        resolveGeneratedRoute(
                            bestRoute, bestRouteFallback, targetDistanceM,
                            maxPathToAirRatio, airDistanceToleranceM
                        )
                    )
                }

                if (!overTargetAir && !ratioRejection.rejected) {
                    selectAcceptableRoute(
                        bestRoute, bestRouteFallback, targetDistanceM,
                        maxPathToAirRatio, airDistanceToleranceM
                    )?.let { return finalizeRoute(it) }
                }

                val usedKeys = ranked!!.map { it.key }.toSet()
                val unused = rankedPool.filter { it.key !in usedKeys }
                val swapped = ObjectRingPlanner.swapWorstControl(
                    origin = origin,
                    current = ranked!!,
                    unused = unused,
                    targetAirM = targetDistanceM,
                    airMaxM = airMaxM,
                    minSpacingM = CANDIDATE_MIN_SPACING_M,
                    rng = rng
                )
                if (swapped == null) break
                ranked = swapped
                swapCount++
                swapTries++
            }

            // Between attempts: morph ring radius from last path/air, then new bearing.
            if (lastPathM > 0.0 && lastAirM > 0.0) {
                val previousR = ringR
                ringR = ObjectRingPlanner.adjustRadiusM(
                    currentR = ringR,
                    airM = lastAirM,
                    pathM = lastPathM,
                    targetAirM = targetDistanceM,
                    airMaxM = airMaxM,
                    maxPathToAir = maxPathToAirRatio
                )
                Log.d(
                    TAG,
                    "  path-seed adjust r ${previousR.toInt()}→${ringR.toInt()}m " +
                        "air=${lastAirM.toInt()} path=${lastPathM.toInt()}"
                )
            }
            bearing0 = (bearing0 + 67.0 + rng.nextDouble() * 30.0) % 360.0
        }

        attemptDiagnostics["ring_r_m"] = ringR.roundToInt()
        attemptDiagnostics["swap_count"] = swapCount
        recordBestCandidateDiagnostics(bestRoute, bestRouteFallback, targetDistanceM)
        attemptDiagnostics["route_fetch_attempts"] = routeFetchAttempts
        attemptDiagnostics["route_fetch_successes"] = routeFetchSuccesses
        attemptDiagnostics["iterations_run"] = iterationLimit
        attemptDiagnostics["ratio_reject_count"] = ratioRejectCount
        attemptDiagnostics["over_target_air_count"] = overTargetAirCount
        attemptDiagnostics["selection_null_count"] = selectionNullCount

        selectAcceptableRoute(
            bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
        )?.let { return finalizeRoute(it) }

        if (routeFetchAttempts > 0 && routeFetchSuccesses == 0) {
            generationFail(messageForRoutingFailures())
        }

        pickBestRoutedCandidate(bestRoute, bestRouteFallback, targetDistanceM)?.let { bestEffort ->
            Log.w(
                TAG,
                "Limits unmet (air max ${airMaxM.toInt()}m, path/air $maxPathToAirRatio×); " +
                    "returning best routed candidate"
            )
            return finalizeRoute(bestEffort)
        }

        if (bestRoute == null && bestRouteFallback == null) {
            Log.w(TAG, "Object-ring produced no route; classic fallback")
            attemptDiagnostics["fell_back_to_classic"] = true
            return generateClassicRoute(
                origin, targetDistanceM, n, rng, maxPathToAirRatio, airDistanceToleranceM, routeId, emitRouteUpdate
            )
        }

        putQualityGateDiagnostics(
            targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRoute, bestRouteFallback
        )
        generationFail(
            routeRejectionFailureMessage(
                targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRouteFallback ?: bestRoute
            )
        )
    }

    /**
     * One-way classic generator: place n intermediate checkpoints, route
     * origin → CPs → destination. Closing bias aims at [destination], not origin.
     */
    private suspend fun generateOneWayClassicRoute(
        origin: GeoPoint,
        destination: GeoPoint,
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
        var routeFetchAttempts = 0
        var routeFetchSuccesses = 0
        var ratioRejectCount = 0
        var overTargetAirCount = 0
        var pointGenNullCount = 0
        val fastAcceptM = fastAcceptToleranceM(targetDistanceM)
        val iterationLimit = if (targetDistanceM < SHORT_ROUTE_M) MAX_ITERATIONS_SHORT else MAX_ITERATIONS
        attemptDiagnostics["iteration_limit"] = iterationLimit
        attemptDiagnostics["fast_accept_tolerance_m"] = fastAcceptM.toInt()

        repeat(iterationLimit) {
            val rawPositions = generatePointsSequentially(
                origin = origin,
                segmentLengths = segmentLengths.take(n),
                targetDistanceM = targetDistanceM,
                rng = rng,
                home = destination
            )
            if (rawPositions == null) {
                pointGenNullCount++
                return@repeat
            }

            routeFetchAttempts++
            val routed = fetchRoutedOpen(origin, rawPositions, destination) ?: return@repeat
            routeFetchSuccesses++
            val snapped = routed.checkpointPositions
            val osrmResult = routed.osrmResult

            val distancesFromPrev = computeDistancesFromPrevOpen(origin, snapped, destination)
            val totalStraightLineM = distancesFromPrev.sum()
            val airMaxM = targetDistanceM + airDistanceToleranceM
            val softError = abs(totalStraightLineM - targetDistanceM)
            val underError = (targetDistanceM - totalStraightLineM).coerceAtLeast(0.0)
            val ratioRejection = pathToAirRejection(
                osrmResult, totalStraightLineM, maxPathToAirRatio
            )
            if (ratioRejection.rejected) ratioRejectCount++
            if (softError < bestErrorFallback) {
                bestErrorFallback = softError
                val fallback = buildCandidateRoute(
                    routeId, origin, snapped, distancesFromPrev, targetDistanceM,
                    totalStraightLineM, osrmResult, destination
                )
                bestRouteFallback = fallback
                emitRouteUpdate(fallback)
            }
            val overTargetAir = totalStraightLineM > airMaxM
            if (overTargetAir) overTargetAirCount++
            if (!ratioRejection.rejected && !overTargetAir && softError < bestError) {
                bestError = softError
                val candidate = buildCandidateRoute(
                    routeId, origin, snapped, distancesFromPrev, targetDistanceM,
                    totalStraightLineM, osrmResult, destination
                )
                bestRoute = candidate
                emitRouteUpdate(candidate)
            }
            if (overTargetAir) {
                val shrink = (targetDistanceM / totalStraightLineM).coerceIn(0.75, 0.98)
                segmentLengths = segmentLengths.map { it * shrink }
                return@repeat
            }
            if (underError <= fastAcceptM && !ratioRejection.rejected &&
                (bestRoute != null || bestRouteFallback != null)
            ) {
                return finalizeRoute(
                    resolveGeneratedRoute(
                        bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
                    )
                )
            }
            selectAcceptableRoute(
                bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
            )?.let { return finalizeRoute(it) }
            if (totalStraightLineM < targetDistanceM * 0.75) {
                val grow = (targetDistanceM / totalStraightLineM).coerceIn(1.02, 1.2)
                segmentLengths = segmentLengths.map { it * grow }
            }
        }
        recordBestCandidateDiagnostics(bestRoute, bestRouteFallback, targetDistanceM)
        attemptDiagnostics["route_fetch_attempts"] = routeFetchAttempts
        attemptDiagnostics["route_fetch_successes"] = routeFetchSuccesses
        attemptDiagnostics["iterations_run"] = iterationLimit
        attemptDiagnostics["ratio_reject_count"] = ratioRejectCount
        attemptDiagnostics["over_target_air_count"] = overTargetAirCount
        attemptDiagnostics["selection_null_count"] = pointGenNullCount

        selectAcceptableRoute(
            bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
        )?.let { return finalizeRoute(it) }
        if (routeFetchAttempts > 0 && routeFetchSuccesses == 0) {
            generationFail(messageForRoutingFailures())
        }
        pickBestRoutedCandidate(bestRoute, bestRouteFallback, targetDistanceM)?.let { bestEffort ->
            Log.w(
                TAG,
                "One-way classic: limits unmet; returning best routed candidate"
            )
            return finalizeRoute(bestEffort)
        }
        putQualityGateDiagnostics(
            targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRoute, bestRouteFallback
        )
        attemptDiagnostics.putIfAbsent("rejection_gate", "unreachable_or_no_route")
        generationFail(
            "Could not find a valid one-way route. Try a longer distance or move start/finish."
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
        attemptDiagnostics.putIfAbsent("generator", "classic")
        var segmentLengths = computeRandomSegmentLengths(n + 1, targetDistanceM, rng)
        var bestRoute: Route? = null
        var bestRouteFallback: Route? = null
        var bestError = Double.MAX_VALUE
        var bestErrorFallback = Double.MAX_VALUE
        var routeFetchAttempts = 0
        var routeFetchSuccesses = 0
        var ratioRejectCount = 0
        var overTargetAirCount = 0
        var pointGenNullCount = 0
        val fastAcceptM = fastAcceptToleranceM(targetDistanceM)
        val iterationLimit = if (targetDistanceM < SHORT_ROUTE_M) MAX_ITERATIONS_SHORT else MAX_ITERATIONS
        attemptDiagnostics["iteration_limit"] = iterationLimit
        attemptDiagnostics["fast_accept_tolerance_m"] = fastAcceptM.toInt()

        repeat(iterationLimit) {
            val rawPositions = generatePointsSequentially(origin, segmentLengths.take(n), targetDistanceM, rng)
            if (rawPositions == null) {
                pointGenNullCount++
                return@repeat
            }

            routeFetchAttempts++
            val routed = fetchRoutedLoop(origin, rawPositions) ?: return@repeat
            routeFetchSuccesses++
            val snapped = routed.checkpointPositions
            val osrmResult = routed.osrmResult

            val distancesFromPrev = computeDistancesFromPrev(origin, snapped)
            val totalStraightLineM = distancesFromPrev.sum()
            val airMaxM = targetDistanceM + airDistanceToleranceM
            val softError = ObjectRingPlanner.airSoftErrorM(
                totalStraightLineM, targetDistanceM, airMaxM
            )
            val underError = (targetDistanceM - totalStraightLineM).coerceAtLeast(0.0)
            val ratioRejection = pathToAirRejection(
                osrmResult, totalStraightLineM, maxPathToAirRatio
            )
            if (ratioRejection.rejected) ratioRejectCount++
            if (softError < bestErrorFallback) {
                bestErrorFallback = softError
                val fallback = buildCandidateRoute(
                    routeId, origin, snapped, distancesFromPrev, targetDistanceM,
                    totalStraightLineM, osrmResult
                )
                bestRouteFallback = fallback
                emitRouteUpdate(fallback)
            }
            val overTargetAir = totalStraightLineM > airMaxM
            if (overTargetAir) overTargetAirCount++
            if (!ratioRejection.rejected && !overTargetAir && softError < bestError) {
                bestError = softError
                val candidate = buildCandidateRoute(
                    routeId, origin, snapped, distancesFromPrev, targetDistanceM,
                    totalStraightLineM, osrmResult
                )
                bestRoute = candidate
                emitRouteUpdate(candidate)
            }
            if (overTargetAir) {
                val shrink = (targetDistanceM / totalStraightLineM).coerceIn(0.75, 0.98)
                segmentLengths = segmentLengths.map { it * shrink }
                return@repeat
            }
            if (underError <= fastAcceptM && !ratioRejection.rejected &&
                (bestRoute != null || bestRouteFallback != null)
            ) {
                return finalizeRoute(
                    resolveGeneratedRoute(
                        bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
                    )
                )
            }
            selectAcceptableRoute(
                bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
            )?.let { return finalizeRoute(it) }
            if (totalStraightLineM < targetDistanceM * 0.75) {
                val grow = (targetDistanceM / totalStraightLineM).coerceIn(1.02, 1.2)
                segmentLengths = segmentLengths.map { it * grow }
            }
        }
        recordBestCandidateDiagnostics(bestRoute, bestRouteFallback, targetDistanceM)
        attemptDiagnostics["route_fetch_attempts"] = routeFetchAttempts
        attemptDiagnostics["route_fetch_successes"] = routeFetchSuccesses
        attemptDiagnostics["iterations_run"] = iterationLimit
        attemptDiagnostics["ratio_reject_count"] = ratioRejectCount
        attemptDiagnostics["over_target_air_count"] = overTargetAirCount
        attemptDiagnostics["selection_null_count"] = pointGenNullCount

        selectAcceptableRoute(
            bestRoute, bestRouteFallback, targetDistanceM, maxPathToAirRatio, airDistanceToleranceM
        )?.let { route ->
            return finalizeRoute(route)
        }
        if (routeFetchAttempts > 0 && routeFetchSuccesses == 0) {
            generationFail(messageForRoutingFailures())
        }
        pickBestRoutedCandidate(bestRoute, bestRouteFallback, targetDistanceM)?.let { bestEffort ->
            Log.w(
                TAG,
                "Classic: limits unmet (±${airDistanceToleranceM.toInt()}m air, path/air $maxPathToAirRatio×); " +
                    "returning best routed candidate"
            )
            return finalizeRoute(bestEffort)
        }
        putQualityGateDiagnostics(
            targetDistanceM, maxPathToAirRatio, airDistanceToleranceM, bestRoute, bestRouteFallback
        )
        attemptDiagnostics.putIfAbsent("rejection_gate", "unreachable_or_no_route")
        generationFail(
            "Could not find a valid route. One or more points may be unreachable (e.g. in water or no roads nearby). Try again or move to a different area."
        )
    }

    private fun recordBestCandidateDiagnostics(
        preferred: Route?,
        fallback: Route?,
        targetM: Double
    ) {
        val best = pickBestRoutedCandidate(preferred, fallback, targetM) ?: preferred ?: fallback
        attemptDiagnostics["had_preferred_route"] = preferred != null
        attemptDiagnostics["had_fallback_route"] = fallback != null
        attemptDiagnostics["had_routed_path"] = best?.hasRoutedWalkingPath == true
        if (best != null) {
            val air = loopAirDistanceM(best)
            attemptDiagnostics["best_air_m"] = air.roundToInt()
            attemptDiagnostics["best_air_error_m"] = abs(air - targetM).roundToInt()
            attemptDiagnostics["best_path_m"] = best.actualPathDistanceM.roundToInt()
            attemptDiagnostics["best_path_air_ratio"] =
                ((best.actualPathDistanceM / air.coerceAtLeast(1.0)) * 100).roundToInt() / 100.0
            attemptDiagnostics["best_checkpoint_count"] = best.waypointCount
            attemptDiagnostics["best_has_routed_path"] = best.hasRoutedWalkingPath
        }
    }

    /** Which quality gates the best candidate failed (air / path-air / no path). */
    private fun putQualityGateDiagnostics(
        targetM: Double,
        maxRatio: Double,
        airToleranceM: Double,
        preferred: Route?,
        fallback: Route?
    ) {
        recordBestCandidateDiagnostics(preferred, fallback, targetM)
        val best = pickBestRoutedCandidate(preferred, fallback, targetM) ?: preferred ?: fallback
        val gates = buildList {
            if (best == null) {
                add("no_candidate")
            } else {
                if (!best.hasRoutedWalkingPath) add("no_routed_path")
                if (!withinTargetAirTolerance(best, targetM, airToleranceM)) add("air_tolerance")
                if (exceedsMaxPathToAirRatio(best, maxRatio)) add("path_air_ratio")
            }
        }
        attemptDiagnostics["rejection_gates"] = gates
        attemptDiagnostics["rejection_gate"] = gates.firstOrNull() ?: "unknown"
        attemptDiagnostics["air_tolerance_limit_m"] = airToleranceM.toInt()
        attemptDiagnostics["path_air_limit"] = maxRatio
    }

    private fun buildCandidateRoute(
        routeId: String,
        origin: GeoPoint,
        snapped: List<GeoPoint>,
        distancesFromPrev: List<Double>,
        targetDistanceM: Double,
        totalStraightLineM: Double,
        osrmResult: OsrmRouteResponse?,
        destination: GeoPoint? = null
    ): Route {
        val (polyline, estimatedDurationS) = when {
            osrmResult != null && osrmResult.code == "Ok" && osrmResult.routeList().isNotEmpty() -> {
                val r = osrmResult.routeList().first()
                val geometry = r.geometry
                if (geometry != null) {
                    simplifyPolyline(geometry.toGeoPoints()) to r.duration
                } else {
                    fallbackPolylineAndDuration(origin, snapped, destination)
                }
            }
            else -> fallbackPolylineAndDuration(origin, snapped, destination)
        }
        val actualPathM = when {
            osrmResult != null && osrmResult.code == "Ok" && osrmResult.routeList().isNotEmpty() ->
                osrmResult.routeList().first().distance
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
            estimatedDurationS = estimatedDurationS,
            destination = destination
        )
    }

    private fun loopAirDistanceM(route: Route): Double =
        route.totalDistanceM.coerceAtLeast(0.0)

    private fun exceedsMaxPathToAirRatio(route: Route, maxRatio: Double): Boolean {
        if (!route.hasRoutedWalkingPath) return false
        val airM = loopAirDistanceM(route).coerceAtLeast(1.0)
        return route.actualPathDistanceM > airM * maxRatio
    }

    /** Air is a max (shorter OK): reject only when air exceeds target + tolerance. */
    private fun withinTargetAirTolerance(route: Route, targetM: Double, airToleranceM: Double): Boolean =
        loopAirDistanceM(route) <= targetM + airToleranceM

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

    /**
     * Prefer a route within settings limits; otherwise the best routed candidate
     * (closest air to target, then lowest path/air). Hard-fails only when [pickBestRoutedCandidate]
     * finds nothing routed.
     */
    private fun resolveGeneratedRoute(
        preferred: Route?,
        fallback: Route?,
        targetM: Double,
        maxRatio: Double,
        airToleranceM: Double
    ): Route {
        selectAcceptableRoute(preferred, fallback, targetM, maxRatio, airToleranceM)?.let { return it }
        return pickBestRoutedCandidate(preferred, fallback, targetM)
            ?: run {
                putQualityGateDiagnostics(targetM, maxRatio, airToleranceM, preferred, fallback)
                generationFail(
                    routeRejectionFailureMessage(targetM, maxRatio, airToleranceM, fallback ?: preferred)
                )
            }
    }

    /** Best routed loop by air error, then path/air. Null if neither candidate has an OSRM path. */
    private fun pickBestRoutedCandidate(
        preferred: Route?,
        fallback: Route?,
        targetM: Double
    ): Route? {
        val candidates = listOfNotNull(preferred, fallback).filter { it.hasRoutedWalkingPath }
        if (candidates.isEmpty()) return null
        return candidates.minWith(
            compareBy<Route> {
                ObjectRingPlanner.airSoftErrorM(
                    loopAirDistanceM(it),
                    targetM,
                    targetM + 2_000.0
                )
            }.thenBy {
                val air = loopAirDistanceM(it).coerceAtLeast(1.0)
                it.actualPathDistanceM / air
            }
        )
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
        if (osrmResult == null || osrmResult.code != "Ok" || osrmResult.routeList().isEmpty()) {
            return PathToAirRejection(rejected = false)
        }
        val route = osrmResult.routeList().first()
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
        if (OVERPASS_ENRICH_MISSING_LANDMARKS) enrichMissingLandmarksBatched(route) else route

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

    /** Absolute fast-accept band; scales down for short targets so 1 km loops are not ±500 m. */
    private fun fastAcceptToleranceM(targetM: Double): Double =
        min(FAST_ACCEPT_TOLERANCE_M, max(FAST_ACCEPT_MIN_M, targetM * FAST_ACCEPT_TARGET_FRACTION))

    private data class CandidateObject(
        val position: GeoPoint,
        val element: OverpassElement,
        val weight: Double
    )

    private suspend fun fetchCandidates(origin: GeoPoint, targetDistanceM: Double): List<CandidateObject> {
        val radiusM = ObjectRingPlanner.searchRadiusM(targetDistanceM)
        val cacheKey = overpassCacheKey(origin, radiusM)
        val now = System.currentTimeMillis()
        cachedCandidates(cacheKey, now)?.let { hit ->
            Log.d(TAG, "Overpass cache hit radius=${radiusM}m candidates=${hit.size}")
            return hit
        }

        val query = buildCandidateOverpassQuery(origin.latitude, origin.longitude, radiusM)
        val elements = runCatching {
            withTimeout(OVERPASS_TIMEOUT_MS) {
                overpass.query(query).parseJson(gson, OverpassResponse::class.java).elements.toList()
            }
        }.getOrElse { emptyList() }
        val candidates = elements.mapNotNull { e ->
            val point = e.coordinateOrNull() ?: return@mapNotNull null
            val tag = e.primaryTag ?: return@mapNotNull null
            if (tag in EXCLUDED_PRIMARY_TAGS) return@mapNotNull null
            val weight = TAG_WEIGHTS[tag] ?: 1.0
            CandidateObject(point, e, weight)
        }.distinctBy { "${it.position.latitude},${it.position.longitude},${it.element.primaryTag}" }

        if (candidates.isNotEmpty()) {
            storeCandidates(cacheKey, candidates, now)
        }
        return candidates
    }

    private fun overpassCacheKey(origin: GeoPoint, radiusM: Int): Long {
        val scale = 10.0.pow(OVERPASS_CACHE_COORD_DECIMALS)
        val latKey = (origin.latitude * scale).roundToInt()
        val lonKey = (origin.longitude * scale).roundToInt()
        // Pack into one key; radius is small (≤3500) so fits beside rounded coords.
        return (latKey.toLong() shl 32) xor (lonKey.toLong() shl 8) xor radiusM.toLong()
    }

    private fun cachedCandidates(key: Long, nowMs: Long): List<CandidateObject>? {
        val hit = overpassCandidateCache[key] ?: return null
        if (nowMs - hit.storedAtMs > OVERPASS_CACHE_TTL_MS) {
            overpassCandidateCache.remove(key)
            return null
        }
        return hit.candidates.map { (pos, element, weight) ->
            CandidateObject(pos, element, weight)
        }
    }

    private fun storeCandidates(key: Long, candidates: List<CandidateObject>, nowMs: Long) {
        if (overpassCandidateCache.size >= OVERPASS_CACHE_MAX_ENTRIES) {
            val oldest = overpassCandidateCache.minByOrNull { it.value.storedAtMs }?.key
            if (oldest != null) overpassCandidateCache.remove(oldest)
        }
        overpassCandidateCache[key] = CachedOverpass(
            candidates = candidates.map { Triple(it.position, it.element, it.weight) },
            storedAtMs = nowMs
        )
    }

    /**
     * Build checkpoint positions for the classic generator.
     * Less random than pure jitter: one consistent turn direction, even turn angles,
     * and each candidate is snapped to a road via OSRM before acceptance.
     */
    /**
     * @param home Point to bias toward in the closing phase (start for loops, finish for one-way).
     */
    private suspend fun generatePointsSequentially(
        origin: GeoPoint,
        segmentLengths: List<Double>,
        targetDistanceM: Double,
        rng: Random,
        home: GeoPoint = origin
    ): List<GeoPoint>? {
        val result = mutableListOf<GeoPoint>()
        var prev = origin
        var spentM = 0.0
        var maxRadiusM = 0.0
        val n = segmentLengths.size
        // One loop orientation for the whole course (avoids zigzag unroutable shapes).
        val turnSign = if (rng.nextBoolean()) 1.0 else -1.0
        val turnPerLeg = (360.0 / (n + 1).coerceAtLeast(3)) * turnSign
        var prevBearing = rng.nextDouble(0.0, 360.0)

        for ((index, segmentTargetM) in segmentLengths.withIndex()) {
            var placed: GeoPoint? = null
            repeat(SNAP_RETRIES) { attempt ->
                val bearing = pickSegmentBearing(
                    home = home,
                    prev = prev,
                    spentM = spentM,
                    targetDistanceM = targetDistanceM,
                    prevBearing = prevBearing,
                    turnPerLeg = turnPerLeg,
                    isLastCheckpoint = index == n - 1,
                    attempt = attempt,
                    rng = rng
                )
                val legM = (segmentTargetM * (1.0 + attempt * 0.08))
                    .coerceIn(SEGMENT_MIN_M * 0.75, SEGMENT_MAX_M)
                val raw = GeoUtils.offsetPoint(prev, legM, bearing)
                val distFromHome = GeoUtils.distanceMeters(home, raw)
                if (shouldCloseLoop(spentM, targetDistanceM) &&
                    maxRadiusM > 0 &&
                    distFromHome > maxRadiusM * MAX_RADIUS_GROWTH_AFTER_HALF
                ) {
                    return@repeat
                }
                // Must be on the road network — raw random lat/lon is why generation "never" found a path.
                val snapped = snapOne(raw) ?: return@repeat
                // Keep checkpoints separated so OSRM /route does not collapse legs.
                if (result.any { GeoUtils.distanceMeters(it, snapped) < CANDIDATE_MIN_SPACING_M }) {
                    return@repeat
                }
                if (GeoUtils.distanceMeters(prev, snapped) < CANDIDATE_MIN_SPACING_M * 0.5) {
                    return@repeat
                }
                placed = snapped
                prevBearing = GeoUtils.bearingDegrees(prev, snapped)
                return@repeat
            }
            val point = placed ?: return null
            result.add(point)
            spentM += GeoUtils.distanceMeters(prev, point)
            maxRadiusM = max(maxRadiusM, GeoUtils.distanceMeters(home, point))
            prev = point
        }
        return result
    }

    /**
     * Prefer a steady left/right path; when closing, bias strongly toward [home]
     * (start for return-to-start, finish for one-way).
     */
    private fun pickSegmentBearing(
        home: GeoPoint,
        prev: GeoPoint,
        spentM: Double,
        targetDistanceM: Double,
        prevBearing: Double,
        turnPerLeg: Double,
        isLastCheckpoint: Boolean,
        attempt: Int,
        rng: Random
    ): Double {
        val closing = shouldCloseLoop(spentM, targetDistanceM) || isLastCheckpoint
        val towardHome = GeoUtils.bearingDegrees(prev, home)
        val continueLoop = (prevBearing + turnPerLeg + 360.0) % 360.0
        val jitter = rng.nextDouble(-18.0, 18.0) + attempt * 12.0 * kotlin.math.sign(turnPerLeg)
        return if (closing) {
            val blend = 0.65 + attempt * 0.1
            val mixed = interpolateBearing(continueLoop, towardHome, blend.coerceAtMost(0.95))
            (mixed + jitter * 0.35 + 360.0) % 360.0
        } else {
            (continueLoop + jitter + 360.0) % 360.0
        }
    }

    /** Shortest-arc blend from [fromDeg] toward [toDeg] by [t] in 0..1. */
    private fun interpolateBearing(fromDeg: Double, toDeg: Double, t: Double): Double {
        var delta = (toDeg - fromDeg + 540.0) % 360.0 - 180.0
        return (fromDeg + delta * t.coerceIn(0.0, 1.0) + 360.0) % 360.0
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
        val response = osrmCall("nearest") {
            osrm.nearest(GeoUtils.toOsrmCoordinate(candidate))
                .parseJson(gson, OsrmNearestResponse::class.java)
        } ?: return null
        if (response.code != "Ok" || response.waypointList().isEmpty()) {
            lastOsrmCallLabel = "nearest"
            noteRoutingFailure(RoutingFailure.OsrmResponse(response.code ?: "unknown"))
            return null
        }
        val wp = response.waypointList().first()
        val snapped = GeoPoint(wp.latitude, wp.longitude)
        if (GeoUtils.distanceMeters(candidate, snapped) > SNAP_MAX_DISTANCE_M) {
            lastOsrmCallLabel = "nearest"
            noteRoutingFailure(RoutingFailure.RoadSnap)
            return null
        }
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
     * Fetches a walking loop via OSRM /route (OSRM snaps waypoints).
     * Parallel /nearest fallback is only used when the route response is Ok but snap
     * validation fails — never after network/timeout (that blew the 55s attempt budget).
     */
    private suspend fun fetchRoutedLoop(
        origin: GeoPoint,
        checkpointPositions: List<GeoPoint>
    ): RoutedLoopResult? = fetchRoutedWaypoints(origin, checkpointPositions, end = origin)

    /** Open chain: origin → checkpoints → [destination]. */
    private suspend fun fetchRoutedOpen(
        origin: GeoPoint,
        checkpointPositions: List<GeoPoint>,
        destination: GeoPoint
    ): RoutedLoopResult? = fetchRoutedWaypoints(origin, checkpointPositions, end = destination)

    private suspend fun fetchRoutedWaypoints(
        origin: GeoPoint,
        checkpointPositions: List<GeoPoint>,
        end: GeoPoint
    ): RoutedLoopResult? {
        val inputPoints = listOf(origin) + checkpointPositions + end
        val osrmResult = osrmCall("route") {
            osrm.route(GeoUtils.toOsrmCoordinates(inputPoints))
                .parseJson(gson, OsrmRouteResponse::class.java)
        } ?: return null

        if (osrmResult.code != "Ok" || osrmResult.routeList().isEmpty()) {
            lastOsrmCallLabel = "route"
            noteRoutingFailure(RoutingFailure.OsrmResponse(osrmResult.code ?: "unknown"))
            return null
        }

        val snapped = extractCheckpointSnaps(osrmResult, inputPoints.size)
        if (snapped == null) {
            lastOsrmCallLabel = "route"
            noteRoutingFailure(RoutingFailure.RoadSnap)
            if (checkpointPositions.size <= 5) {
                delay(OSRM_RETRY_BACKOFF_MS)
                return fetchRoutedWithParallelSnap(origin, checkpointPositions, end)
            }
            return null
        }

        return RoutedLoopResult(osrmResult, snapped)
    }

    private suspend fun fetchRoutedWithParallelSnap(
        origin: GeoPoint,
        checkpointPositions: List<GeoPoint>,
        end: GeoPoint
    ): RoutedLoopResult? {
        val snapped = snapAllParallel(checkpointPositions) ?: run {
            lastOsrmCallLabel = lastOsrmCallLabel ?: "nearest"
            noteRoutingFailure(RoutingFailure.RoadSnap)
            return null
        }
        val inputPoints = listOf(origin) + snapped + end
        val osrmResult = osrmCall("route-resnap") {
            osrm.route(GeoUtils.toOsrmCoordinates(inputPoints))
                .parseJson(gson, OsrmRouteResponse::class.java)
        } ?: return null
        if (osrmResult.code != "Ok" || osrmResult.routeList().isEmpty()) {
            lastOsrmCallLabel = "route-resnap"
            noteRoutingFailure(RoutingFailure.OsrmResponse(osrmResult.code ?: "unknown"))
            return null
        }
        val validated = extractCheckpointSnaps(osrmResult, inputPoints.size)
        if (validated == null) {
            lastOsrmCallLabel = "route-resnap"
            noteRoutingFailure(RoutingFailure.RoadSnap)
        }
        return RoutedLoopResult(osrmResult, validated ?: snapped)
    }

    private fun noteRoutingFailure(failure: RoutingFailure) {
        if (lastRoutingFailure == null || failure is RoutingFailure.HttpStatus || failure is RoutingFailure.Network) {
            lastRoutingFailure = failure
        }
    }

    private suspend fun <T> osrmCall(label: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: HttpException) {
            lastOsrmCallLabel = label
            val (osrmCode, bodySnippet) = peekOsrmError(e)
            if (osrmCode != null) {
                noteRoutingFailure(RoutingFailure.OsrmResponse(osrmCode))
            } else {
                noteRoutingFailure(RoutingFailure.HttpStatus(e.code()))
            }
            bodySnippet?.let { attemptDiagnostics["osrm_error_body"] = it }
            Log.w(TAG, "$label HTTP ${e.code()}" + (osrmCode?.let { " ($it)" } ?: ""))
            null
        } catch (e: JsonSyntaxException) {
            lastOsrmCallLabel = label
            noteRoutingFailure(RoutingFailure.ResponseParse)
            recordOsrmException(e)
            Log.w(TAG, "$label JSON parse failed", e)
            null
        } catch (e: IOException) {
            lastOsrmCallLabel = label
            noteRoutingFailure(RoutingFailure.Network(e))
            recordOsrmException(e)
            Log.w(TAG, "$label network failed", e)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClassCastException) {
            // Typical R8/Gson generic erasure: LinkedTreeMap cannot be cast to OsrmWaypoint.
            lastOsrmCallLabel = label
            noteRoutingFailure(RoutingFailure.ResponseParse)
            recordOsrmException(e)
            attemptDiagnostics["routing_failure_kind"] = "gson_type_erasure"
            attemptDiagnostics["rejection_gate"] = "gson_type_erasure"
            Log.e(TAG, "$label Gson/R8 type erasure (ClassCastException)", e)
            null
        } catch (e: Exception) {
            lastOsrmCallLabel = label
            noteRoutingFailure(RoutingFailure.ResponseParse)
            recordOsrmException(e)
            Log.w(TAG, "$label failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun recordOsrmException(e: Throwable) {
        attemptDiagnostics["osrm_exception"] = e.javaClass.name
        attemptDiagnostics["osrm_exception_message"] = e.message?.take(240)
        e.cause?.let { cause ->
            attemptDiagnostics["osrm_exception_cause"] =
                "${cause.javaClass.simpleName}: ${cause.message?.take(160)}"
        }
    }

    /** Best-effort OSRM `code` + short body snippet from an error response (e.g. InvalidQuery). */
    private fun peekOsrmError(e: HttpException): Pair<String?, String?> {
        return try {
            val raw = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                ?: return null to null
            val code = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
            val snippet = raw.replace(Regex("\\s+"), " ").take(240)
            code to snippet
        } catch (_: Exception) {
            null to null
        }
    }

    private fun messageForRoutingFailures(): String {
        return when (val failure = lastRoutingFailure) {
            is RoutingFailure.Network -> {
                val chain = generateSequence(failure.cause as Throwable) { it.cause }
                when {
                    failure.cause is UnknownHostException ||
                        chain.any { it is UnknownHostException } ->
                        "No network connection. Connect to the internet and try again."
                    failure.cause is SocketTimeoutException ||
                        chain.any { it is SocketTimeoutException } ->
                        "Routing timed out. Try again or choose a shorter distance."
                    else ->
                        "Can't reach the routing service. Check your connection and try again."
                }
            }
            is RoutingFailure.HttpStatus -> when (failure.code) {
                429 ->
                    "Routing service is busy (too many requests). Wait a minute and try again."
                in 500..599 ->
                    "Routing service temporarily unavailable. Try again in a few minutes."
                404 ->
                    "Routing service endpoint not found. Please update the app."
                else ->
                    "Routing service returned an error (HTTP ${failure.code}). Try again."
            }
            is RoutingFailure.OsrmResponse -> when (failure.code) {
                "NoRoute", "NoSegment" ->
                    "Could not route on nearby roads. Try again or move closer to paths and streets."
                "InvalidOptions", "InvalidQuery", "InvalidValue" ->
                    "Routing rejected the request (bad coordinates). Check GPS and try again."
                else ->
                    "Routing engine error (${failure.code}). Try again."
            }
            RoutingFailure.ResponseParse ->
                "Received invalid data from the routing service. Try again or update the app."
            RoutingFailure.RoadSnap ->
                "Could not snap route points to nearby roads. Try again or choose a different area."
            null ->
                "Could not compute a walking route. Check your connection and try again."
        }
    }

    private fun extractCheckpointSnaps(
        osrmResult: OsrmRouteResponse,
        expectedWaypointCount: Int
    ): List<GeoPoint>? {
        if (osrmResult.waypointList().size != expectedWaypointCount) return null
        if (osrmResult.waypointList().any { it.distance > SNAP_MAX_DISTANCE_M }) return null
        if (expectedWaypointCount <= 2) return emptyList()
        return osrmResult.waypointList()
            .drop(1)
            .dropLast(1)
            .map { GeoPoint(it.latitude, it.longitude) }
    }

    private fun computeDistancesFromPrev(origin: GeoPoint, checkpoints: List<GeoPoint>): List<Double> =
        computeDistancesFromPrevOpen(origin, checkpoints, end = origin)

    private fun computeDistancesFromPrevOpen(
        origin: GeoPoint,
        checkpoints: List<GeoPoint>,
        end: GeoPoint
    ): List<Double> =
        buildList {
            var prev = origin
            for (cp in checkpoints) {
                add(GeoUtils.distanceMeters(prev, cp))
                prev = cp
            }
            add(GeoUtils.distanceMeters(prev, end))
        }

    private fun simplifyPolyline(points: List<GeoPoint>): List<GeoPoint> =
        PolylineSimplifier.simplify(points, POLYLINE_SIMPLIFY_TOLERANCE_M)

    private fun fallbackPolylineAndDuration(
        origin: GeoPoint,
        snapped: List<GeoPoint>,
        destination: GeoPoint? = null
    ): Pair<List<GeoPoint>, Double> {
        val end = destination ?: origin
        val polyline = buildList {
            add(origin)
            addAll(snapped)
            add(end)
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
        estimatedDurationS: Double,
        destination: GeoPoint? = null
    ): Route {
        val oneWay = destination != null
        val checkpoints = buildList {
            add(
                Checkpoint(
                    id = 0,
                    position = origin,
                    description = if (oneWay) {
                        "Start — begin your run here."
                    } else {
                        "Start / Finish — return here to complete your run."
                    },
                    isStart = true,
                    distanceFromPrev = 0.0
                )
            )
            snapped.forEachIndexed { idx, point ->
                val (desc, name, tag) = if (selected.isNotEmpty() && idx < selected.size) {
                    CheckpointDescriptionGenerator.generate(idx, listOf(selected[idx].element))
                } else {
                    // Generic text; batched Overpass may replace when landmarkType is still null.
                    CheckpointDescriptionGenerator.generate(idx, emptyList())
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
        ).withFinishCheckpoint(finishPosition = destination)
    }

    /**
     * Single Overpass round-trip for all controls missing [Checkpoint.landmarkType].
     * Uses a padded bbox covering those points (not one query per control).
     */
    private suspend fun enrichMissingLandmarksBatched(route: Route): Route {
        val needs = route.checkpoints.filter { !it.isStart && it.landmarkType.isNullOrBlank() }
        if (needs.isEmpty()) return route

        val lats = needs.map { it.position.latitude }
        val lons = needs.map { it.position.longitude }
        val padDeg = ENRICH_AROUND_RADIUS_M / 111_320.0
        val south = (lats.minOrNull() ?: return route) - padDeg
        val north = (lats.maxOrNull() ?: return route) + padDeg
        val west = (lons.minOrNull() ?: return route) - padDeg
        val east = (lons.maxOrNull() ?: return route) + padDeg

        val elements = runCatching {
            withTimeout(OVERPASS_TIMEOUT_MS) {
                val query = buildBboxOverpassQuery(
                    south = south,
                    west = west,
                    north = north,
                    east = east,
                    timeoutSec = 8,
                    maxElements = ENRICH_MAX_ELEMENTS
                )
                overpass.query(query).parseJson(gson, OverpassResponse::class.java).elements.toList()
            }
        }.getOrElse {
            Log.w(TAG, "Batched landmark enrich failed: ${it.message}")
            emptyList()
        }
        if (elements.isEmpty()) return route

        val enriched = route.checkpoints.map { cp ->
            if (cp.isStart || !cp.landmarkType.isNullOrBlank()) return@map cp
            val ranked = rankElementsNear(cp.position, elements)
                .filter { e ->
                    val coord = e.coordinateOrNull() ?: return@filter false
                    GeoUtils.distanceMeters(cp.position, coord) <= ENRICH_AROUND_RADIUS_M
                }
            if (ranked.isEmpty()) return@map cp
            // generate() uses 0-based index for "Checkpoint N" display.
            val (desc, name, tag) = CheckpointDescriptionGenerator.generate(cp.id - 1, ranked)
            cp.copy(description = desc, landmarkName = name, landmarkType = tag)
        }
        return route.copy(checkpoints = enriched)
    }

    private fun buildCandidateOverpassQuery(lat: Double, lon: Double, radiusM: Int): String =
        buildAreaOverpassQuery(lat, lon, radiusM = radiusM, timeoutSec = 7, maxElements = 64)

    private fun buildBboxOverpassQuery(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        timeoutSec: Int,
        maxElements: Int
    ): String {
        val bbox = "$south,$west,$north,$east"
        val poiLines = listOf(
            "nwr[\"historic\"~\"monument|memorial|statue|ruins|wayside_cross|archaeological_site|castle|fort|boundary_stone\"]($bbox);",
            "nwr[\"tourism\"~\"viewpoint|artwork|information|picnic_site|clock\"]($bbox);",
            "nwr[\"amenity\"~\"bench|fountain|shelter|drinking_water|post_box|waste_basket|telephone\"]($bbox);",
            "nwr[\"natural\"~\"peak|rock|spring|tree\"]($bbox);",
            "nwr[\"man_made\"~\"tower|water_tower|windmill|cross|survey_point|obelisk\"]($bbox);",
            "nwr[\"leisure\"~\"playground|picnic_table|fitness_station|garden\"]($bbox);",
            "nwr[\"barrier\"=\"bollard\"]($bbox);",
            "node[\"highway\"~\"bus_stop|street_lamp|traffic_signals|crossing|mini_roundabout\"]($bbox);"
        ).joinToString("\n") { "  $it" }
        return """
            [out:json][timeout:$timeoutSec];
            (
            $poiLines
            );
            out center tags $maxElements;
        """.trimIndent()
    }

    /** Targeted OSM features common on Street-O / urban orienteering maps (not broad amenity=*). */
    private fun buildAreaOverpassQuery(
        lat: Double,
        lon: Double,
        radiusM: Int,
        timeoutSec: Int,
        maxElements: Int
    ): String {
        val around = "around:$radiusM,$lat,$lon"
        val poiLines = listOf(
            "nwr[\"historic\"~\"monument|memorial|statue|ruins|wayside_cross|archaeological_site|castle|fort|boundary_stone\"]($around);",
            "nwr[\"tourism\"~\"viewpoint|artwork|information|picnic_site|clock\"]($around);",
            "nwr[\"amenity\"~\"bench|fountain|shelter|drinking_water|post_box|waste_basket|telephone\"]($around);",
            "nwr[\"natural\"~\"peak|rock|spring|tree\"]($around);",
            "nwr[\"man_made\"~\"tower|water_tower|windmill|cross|survey_point|obelisk\"]($around);",
            "nwr[\"leisure\"~\"playground|picnic_table|fitness_station|garden\"]($around);",
            "nwr[\"barrier\"=\"bollard\"]($around);",
            "node[\"highway\"~\"bus_stop|street_lamp|traffic_signals|crossing|mini_roundabout\"]($around);"
        ).joinToString("\n") { "  $it" }
        return """
            [out:json][timeout:$timeoutSec];
            (
            $poiLines
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
