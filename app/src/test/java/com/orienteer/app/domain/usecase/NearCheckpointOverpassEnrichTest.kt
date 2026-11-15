package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import com.orienteer.app.data.model.Route
import com.orienteer.app.util.GeoUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID
import kotlin.system.measureTimeMillis

class NearCheckpointOverpassEnrichTest {

    private fun routeWithBlankLandmarks(vararg positions: GeoPoint): Route {
        val cps = buildList {
            add(
                Checkpoint(
                    id = 0,
                    position = positions.first(),
                    description = "Start",
                    isStart = true
                )
            )
            positions.drop(1).forEachIndexed { i, p ->
                add(
                    Checkpoint(
                        id = i + 2,
                        position = p,
                        description = "Control ${i + 1}",
                        landmarkType = null
                    )
                )
            }
        }
        return Route(
            id = UUID.randomUUID().toString(),
            checkpoints = cps,
            polylinePoints = positions.toList(),
            targetDistanceM = 5_000.0,
            totalDistanceM = 5_000.0,
            estimatedDurationS = 3_000.0
        )
    }

    private fun elementAt(lat: Double, lon: Double, vararg tags: Pair<String, String>): OverpassElement {
        return OverpassElement(
            type = "node",
            id = tags.hashCode().toLong(),
            lat = lat,
            lon = lon,
            tags = tags.toMap(),
            center = null
        )
    }

    @Test
    fun fetchShardWithRetry_succeedsAfterSimulated502() = runBlocking {
        var calls = 0
        val bench = elementAt(56.951, 24.10, "amenity" to "bench")
        val result = NearCheckpointOverpassEnrich.fetchShardWithRetry(
            delay = { /* no-op in unit tests */ }
        ) {
            calls++
            if (calls < 2) throw IOException("HTTP 502 Bad Gateway")
            listOf(bench)
        }
        assertEquals(2, calls)
        assertEquals(1, result.size)
        assertEquals("amenity=bench", result.first().primaryTag)
    }

    @Test
    fun fetchShardWithRetry_stopsAfterOneRetryOn502() = runBlocking {
        var calls = 0
        val result = NearCheckpointOverpassEnrich.fetchShardWithRetry(
            delay = { /* no-op in unit tests */ }
        ) {
            calls++
            throw IOException("HTTP 502 Bad Gateway")
        }
        assertTrue(result.isEmpty())
        assertEquals(2, calls)
    }

    @Test
    fun enrichRoute_batchesTwoCheckpointsPerOverpassCall() = runBlocking {
        val p1 = GeoPoint(56.950, 24.10)
        val p2 = GeoPoint(56.951, 24.10)
        val p3 = GeoPoint(56.952, 24.10)
        val p4 = GeoPoint(56.953, 24.10)
        val route = routeWithBlankLandmarks(p1, p2, p3, p4)
        var queryCount = 0
        NearCheckpointOverpassEnrich.enrichRoute(route) { query ->
            queryCount++
            assertTrue(query.contains("around:220"))
            emptyList()
        }
        assertEquals(2, queryCount)
    }

    @Test
    fun enrichRoute_honorsOverallBudgetAcrossShards() = runBlocking {
        val start = GeoPoint(56.95, 24.10)
        val cps = listOf(
            GeoPoint(56.9502, 24.1002),
            GeoPoint(56.9504, 24.1004),
            GeoPoint(56.9506, 24.1006),
            GeoPoint(56.9508, 24.1008),
            GeoPoint(56.9510, 24.1010)
        )
        val route = routeWithBlankLandmarks(start, *cps.toTypedArray())
        val elapsedMs = measureTimeMillis {
            NearCheckpointOverpassEnrich.enrichRoute(
                route = route,
                enrichTimeoutMs = 1_000L
            ) {
                kotlinx.coroutines.delay(700L)
                emptyList()
            }
        }
        assertTrue("overall enrich budget should stop sequential shard work", elapsedMs < 1_800L)
    }

    @Test
    fun attach_denseOnlyNeighborhood_attachesAfterFallback() {
        val anchor = GeoPoint(56.95, 24.10)
        val lamp = elementAt(56.95005, 24.10005, "highway" to "street_lamp")
        val withoutDense = NearCheckpointOverpassEnrich.rankedWhitelistedNear(
            anchor,
            listOf(lamp),
            excludeDense = true
        )
        assertTrue(withoutDense.isEmpty())
        val withDense = NearCheckpointOverpassEnrich.rankedWhitelistedNear(
            anchor,
            listOf(lamp),
            excludeDense = false
        )
        assertEquals(1, withDense.size)
        assertEquals("highway=street_lamp", withDense.first().primaryTag)
    }

    @Test
    fun enrichRoute_softAttachWithin15m_nudgesPositionAndUsesAtStyle() = runBlocking {
        val start = GeoPoint(56.95, 24.10)
        val cp = GeoPoint(56.9502, 24.1002)
        val route = routeWithBlankLandmarks(start, cp)
        val poiAt10m = GeoUtils.offsetPoint(cp, 10.0, 45.0)
        val bench = elementAt(poiAt10m.latitude, poiAt10m.longitude, "amenity" to "bench")
        val enriched = NearCheckpointOverpassEnrich.enrichRoute(route) { listOf(bench) }
        val control = enriched.checkpoints.last()
        assertEquals(poiAt10m.latitude, control.position.latitude, 1e-6)
        assertEquals(poiAt10m.longitude, control.position.longitude, 1e-6)
        assertTrue(control.description.contains("At "))
        assertEquals("amenity=bench", control.landmarkType)
    }

    @Test
    fun enrichRoute_softAttachBeyond15m_keepsPositionAndUsesNearStyle() = runBlocking {
        val start = GeoPoint(56.95, 24.10)
        val cp = GeoPoint(56.9502, 24.1002)
        val route = routeWithBlankLandmarks(start, cp)
        val poiAt40m = GeoUtils.offsetPoint(cp, 40.0, 45.0)
        val bench = elementAt(poiAt40m.latitude, poiAt40m.longitude, "amenity" to "bench")
        val enriched = NearCheckpointOverpassEnrich.enrichRoute(route) { listOf(bench) }
        val control = enriched.checkpoints.last()
        assertEquals(cp.latitude, control.position.latitude, 1e-6)
        assertEquals(cp.longitude, control.position.longitude, 1e-6)
        assertTrue(control.description.contains("Near "))
        assertEquals("amenity=bench", control.landmarkType)
    }

    @Test
    fun enrichRoute_softFailOnTimeout_returnsOriginalRoute() = runBlocking {
        val start = GeoPoint(56.95, 24.10)
        val cp = GeoPoint(56.9502, 24.1002)
        val route = routeWithBlankLandmarks(start, cp)
        val result = NearCheckpointOverpassEnrich.enrichRoute(
            route = route,
            enrichTimeoutMs = 50L
        ) {
            kotlinx.coroutines.delay(500L)
            listOf(elementAt(cp.latitude, cp.longitude, "amenity" to "bench"))
        }
        assertEquals(route.id, result.id)
        assertEquals(route.checkpoints.size, result.checkpoints.size)
        result.checkpoints.zip(route.checkpoints).forEach { (got, orig) ->
            assertEquals(orig.position.latitude, got.position.latitude, 1e-9)
            assertEquals(orig.position.longitude, got.position.longitude, 1e-9)
            assertEquals(orig.landmarkType, got.landmarkType)
            assertEquals(orig.description, got.description)
        }
    }

    @Test
    fun speedTuningConstants_exposedForRegression() {
        assertEquals(15.0, NearCheckpointOverpassEnrich.SOFT_ATTACH_NUDGE_M, 0.0)
        assertEquals(2, NearCheckpointOverpassEnrich.SHARD_SIZE)
        assertEquals(220.0, NearCheckpointOverpassEnrich.ENRICH_AROUND_RADIUS_M, 0.0)
    }

    @Test
    fun enrichRoute_attachesDenseLandmarkWhenOnlyDensePoiNearby() = runBlocking {
        val start = GeoPoint(56.95, 24.10)
        val cp = GeoPoint(56.9502, 24.1002)
        val route = routeWithBlankLandmarks(start, cp)
        val lamp = elementAt(cp.latitude + 0.00003, cp.longitude + 0.00003, "highway" to "street_lamp")
        val enriched = NearCheckpointOverpassEnrich.enrichRoute(route) { listOf(lamp) }
        val control = enriched.checkpoints.last()
        assertEquals("highway=street_lamp", control.landmarkType)
        assertFalse(control.description.isBlank())
    }
}
