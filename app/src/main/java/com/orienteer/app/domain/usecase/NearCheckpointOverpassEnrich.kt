package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import com.orienteer.app.data.model.OverpassLandmarkTags
import com.orienteer.app.data.model.Route
import com.orienteer.app.util.CheckpointDescriptionGenerator
import com.orienteer.app.util.GeoUtils
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.roundToInt

/**
 * Near-control Overpass enrich: shard queries, retries, whitelist, and dense-tag fallback (testable without Hilt).
 */
object NearCheckpointOverpassEnrich {

    const val ENRICH_AROUND_RADIUS_M = 220.0
    const val SOFT_ATTACH_NUDGE_M = 15.0
    const val SHARD_SIZE = 2
    const val MAX_ELEMENTS_PER_SHARD = 60
    private const val MAX_ATTEMPTS = 2
    private val BACKOFF_MS = longArrayOf(400, 800)
    private const val QUERY_TIMEOUT_SEC = 8

    fun shard(points: List<GeoPoint>): List<List<GeoPoint>> = points.chunked(SHARD_SIZE)

    fun isRetryableFailure(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            when (cur) {
                is TimeoutCancellationException,
                is SocketTimeoutException -> return true
                is HttpException -> if (cur.code() == 502 || cur.code() == 504) return true
                is IOException -> {
                    val msg = cur.message.orEmpty()
                    if (msg.contains("502") || msg.contains("504") || msg.contains("timeout", ignoreCase = true)) {
                        return true
                    }
                }
            }
            cur = cur.cause
        }
        return false
    }

    suspend fun fetchShardWithRetry(
        delay: suspend (Long) -> Unit,
        execute: suspend () -> List<OverpassElement>
    ): List<OverpassElement> {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return execute()
            } catch (t: Throwable) {
                if (!isRetryableFailure(t) || attempt == MAX_ATTEMPTS - 1) {
                    return emptyList()
                }
                delay(BACKOFF_MS.getOrElse(attempt) { BACKOFF_MS.last() })
            }
        }
        return emptyList()
    }

    fun buildNearCheckpointsOverpassQuery(
        points: List<GeoPoint>,
        radiusM: Int = ENRICH_AROUND_RADIUS_M.roundToInt(),
        timeoutSec: Int = QUERY_TIMEOUT_SEC,
        maxElements: Int = MAX_ELEMENTS_PER_SHARD
    ): String {
        val keyRegex =
            "amenity|historic|tourism|natural|man_made|leisure|barrier|building|highway|waterway|power|landuse"
        val poiLines = points.joinToString("\n") { p ->
            """  nwr(around:$radiusM,${p.latitude},${p.longitude})[~"^($keyRegex)$"~"."];"""
        }
        return """
            [out:json][timeout:$timeoutSec];
            (
            $poiLines
            );
            out center tags $maxElements;
        """.trimIndent()
    }

    fun rankElementsNear(anchor: GeoPoint, elements: List<OverpassElement>): List<OverpassElement> =
        elements
            .mapNotNull { e -> e.coordinateOrNull()?.let { coord -> e to GeoUtils.distanceMeters(anchor, coord) } }
            .filter { (e, _) -> e.primaryTag != null }
            .sortedWith(
                compareBy<Pair<OverpassElement, Double>> { it.second }
                    .thenByDescending { (e, _) -> OverpassLandmarkTags.tagWeight(e.primaryTag!!) }
            )
            .map { it.first }

    fun rankedWhitelistedNear(
        anchor: GeoPoint,
        elements: List<OverpassElement>,
        excludeDense: Boolean,
        radiusM: Double = ENRICH_AROUND_RADIUS_M
    ): List<OverpassElement> =
        rankElementsNear(anchor, elements).filter { e ->
            val tag = e.primaryTag ?: return@filter false
            if (tag in OverpassLandmarkTags.EXCLUDED_PRIMARY_TAGS) return@filter false
            if (excludeDense && tag in OverpassLandmarkTags.DENSE_LANDMARK_TAGS) return@filter false
            if (!OverpassLandmarkTags.isWhitelistedPrimaryTag(tag)) return@filter false
            val coord = e.coordinateOrNull() ?: return@filter false
            GeoUtils.distanceMeters(anchor, coord) <= radiusM
        }

    suspend fun enrichRoute(
        route: Route,
        enrichTimeoutMs: Long = 8_000L,
        delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        queryExecutor: suspend (String) -> List<OverpassElement>
    ): Route = runCatching {
        withTimeout(enrichTimeoutMs) {
            val needs = route.checkpoints.filter { !it.isStart && it.landmarkType.isNullOrBlank() }
            if (needs.isEmpty()) return@withTimeout route

            val merged = mutableListOf<OverpassElement>()
            for (group in shard(needs.map { it.position })) {
                val query = buildNearCheckpointsOverpassQuery(group)
                val elements = fetchShardWithRetry(delay) {
                    queryExecutor(query)
                }
                merged.addAll(elements)
            }
            if (merged.isEmpty()) return@withTimeout route

            val enriched = route.checkpoints.map { cp ->
                if (cp.isStart || !cp.landmarkType.isNullOrBlank()) return@map cp
                var ranked = rankedWhitelistedNear(cp.position, merged, excludeDense = true)
                if (ranked.isEmpty()) {
                    ranked = rankedWhitelistedNear(cp.position, merged, excludeDense = false)
                }
                if (ranked.isEmpty()) return@map cp
                val best = ranked.first()
                val poiCoord = best.coordinateOrNull() ?: return@map cp
                val distToPoiM = GeoUtils.distanceMeters(cp.position, poiCoord)
                val onObject = distToPoiM <= SOFT_ATTACH_NUDGE_M
                val position = if (onObject) poiCoord else cp.position
                val (desc, name, tag) = CheckpointDescriptionGenerator.generate(
                    cp.id - 1,
                    ranked,
                    onObject = onObject
                )
                cp.copy(position = position, description = desc, landmarkName = name, landmarkType = tag)
            }
            route.copy(checkpoints = enriched)
        }
    }.getOrElse { route }
}
